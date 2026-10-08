#!/usr/bin/env python3
"""Reject unsupported Gson method descriptors in a compiled LegacyForgeBridge main JAR.

Gson JsonObject.addProperty accepts String, Number, Boolean, Character, not primitives.
A former compile-only Gson test double incorrectly declared int/float/boolean overloads,
causing all source conversions to fail with NoSuchMethodError at runtime.

This is a structural ABI check, not a substitute for a real Fabric runtime test.
"""
from __future__ import annotations
import argparse
import json
import struct
import sys
from collections import defaultdict
from pathlib import Path
from zipfile import ZipFile

API = {
    ('com/google/gson/JsonObject', 'addProperty'):
        {'(Ljava/lang/String;Ljava/lang/String;)V',
         '(Ljava/lang/String;Ljava/lang/Number;)V',
         '(Ljava/lang/String;Ljava/lang/Boolean;)V',
         '(Ljava/lang/String;Ljava/lang/Character;)V'},
    ('com/google/gson/JsonArray', 'add'):
        {'(Lcom/google/gson/JsonElement;)V',
         '(Ljava/lang/String;)V',
         '(Ljava/lang/Number;)V',
         '(Ljava/lang/Boolean;)V',
         '(Ljava/lang/Character;)V'},
}

def refs(blob: bytes) -> list[tuple[str,str,str]]:
    if len(blob)<10 or blob[:4] != b'\xca\xfe\xba\xbe':
        raise ValueError('Not a Java .class file')
    count=struct.unpack_from('>H',blob,8)[0]
    cp=[None]*count; i=1; pos=10
    while i<count:
        tag=blob[pos];pos+=1
        if tag==1:
            n=struct.unpack_from('>H',blob,pos)[0];pos+=2
            cp[i]=(tag,blob[pos:pos+n].decode('utf-8', errors='replace'));pos+=n
        elif tag in (3,4):pos+=4
        elif tag in (5,6):pos+=8;i+=1
        elif tag in (7,8,16,19,20):cp[i]=(tag,struct.unpack_from('>H',blob,pos)[0]);pos+=2
        elif tag in (9,10,11,12,17,18):cp[i]=(tag,*struct.unpack_from('>HH',blob,pos));pos+=4
        elif tag==15:pos+=3
        else:raise ValueError(f'Invalid constant pool tag {tag} at entry {i}')
        i+=1
    out=[]
    for entry in cp[1:]:
        if entry is None or entry[0] not in (10,11):continue
        owner=cp[cp[entry[1]][1]][1]
        nat=cp[entry[2]]
        method=cp[nat[1]][1]
        desc=cp[nat[2]][1]
        out.append((owner,method,desc))
    return out

def audit(jar:Path)->dict:
    bad=defaultdict(list);examined=0;linked=0
    with ZipFile(jar) as z:
        if z.testzip() is not None:raise ValueError('ZIP CRC mismatch')
        for entry in z.namelist():
            if not entry.endswith('.class'):continue
            examined+=1
            for owner,method,desc in refs(z.read(entry)):
                allowed=API.get((owner,method))
                if allowed is None:continue
                linked+=1
                if desc not in allowed:bad[f'{owner}.{method}{desc}'].append(entry)
    return {'verdict':'PASS_STATIC_ABI' if not bad else 'FAIL_UNSUPPORTED_GSON_METHOD',
            'jar':jar.name,'classFilesInspected':examined,'gsonTargetMethodRefs':linked,
            'badMethodRefs':{k:sorted(set(v)) for k,v in bad.items()},
            'limitations':'Static Gson ABI reference check only; does not execute Fabric/Forge.'}

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('jar',type=Path)
    p.add_argument('--json',type=Path)
    args=p.parse_args()
    try:r=audit(args.jar)
    except (OSError,ValueError) as e: print('ERROR:',e,file=sys.stderr);return 2
    if args.json:args.json.write_text(json.dumps(r,ensure_ascii=False,indent=2)+'\n')
    print(f"{r['verdict']}: {r['classFilesInspected']} classes; "
          f"{r['gsonTargetMethodRefs']} Gson target method refs; {len(r['badMethodRefs'])} bad signatures")
    for method,items in r['badMethodRefs'].items():print('  ',method,'in',', '.join(items))
    return 0 if not r['badMethodRefs'] else 1
if __name__=='__main__':raise SystemExit(main())
