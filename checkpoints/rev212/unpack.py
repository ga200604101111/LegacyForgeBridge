#!/usr/bin/env python3
"""Restore the verified rev212 source/build/test kit. No build, network, game launch or Actions."""
import argparse,base64,hashlib,json,lzma,sys
from pathlib import Path,PurePosixPath
PIN='a0e930e441921a9aab7608df7ebc35a1a04a93bd1cc05221dc5e5cbabdb0595f'
PARTS=[f'source-payload.json.xz.b64.part{i:02d}' for i in range(6)]
COUNT=59;COMPRESSED=48324;DECODED=256465

def sha(b):return hashlib.sha256(b).hexdigest()
def safe(name):
    if not isinstance(name,str):raise ValueError('Path is not text')
    p=PurePosixPath(name)
    if not name or name=='.' or p.is_absolute() or '..' in p.parts or str(p)!=name or any(c in name for c in ('\\',':','\0')) or any(ord(c)<32 for c in name):raise ValueError('Unsafe path')
    return p

def restore(output):
    here=Path(__file__).resolve().parent;out=Path(output).absolute()
    if out.exists() or out.is_symlink() or any(p.is_symlink() for p in out.parents):raise ValueError('Output must be a new non-symlink directory')
    m=json.loads((here/'payload-manifest.json').read_text(encoding='utf-8'))
    if m['sha256']!=PIN or m['parts']!=PARTS or m['files']!=COUNT or m['compressed_bytes']!=COMPRESSED or m['decoded_bytes']!=DECODED or m['main_in_checkpoint'] is not False or m['source_payload_installable'] is not False:raise ValueError('Manifest mismatch')
    raw=base64.b64decode(''.join((here/p).read_text(encoding='ascii').strip() for p in PARTS),validate=True)
    if len(raw)!=COMPRESSED or sha(raw)!=PIN:raise ValueError('Payload checksum mismatch')
    d=lzma.LZMADecompressor(memlimit=128*1024*1024);decoded=d.decompress(raw,max_length=DECODED+1)
    if len(decoded)!=DECODED or not d.eof or d.unused_data:raise ValueError('Decompression boundary')
    payload=json.loads(decoded);checked={};folded=set()
    if payload['schema']!=1 or len(payload['files'])!=COUNT:raise ValueError('Payload schema/count')
    for entry in payload['files']:
        name=entry['path'];safe(name);b=entry['content'].encode('utf-8')
        if name.casefold() in folded or sha(b)!=entry['sha256']:raise ValueError('Duplicate path or checksum mismatch')
        folded.add(name.casefold());checked[name]=b
    for name in checked:
        if any(str(p).casefold() in folded for p in PurePosixPath(name).parents):raise ValueError('File/directory conflict')
    for name,h in json.loads(checked['source-sha256.json']).items():
        if name not in checked or sha(checked[name])!=h:raise ValueError('Build source checksum mismatch')
    out.mkdir(parents=True,exist_ok=False)
    for name,b in checked.items():
        p=out/name;p.parent.mkdir(parents=True,exist_ok=True)
        with p.open('xb') as f:f.write(b)
    print('Restored 59 verified source/build/test/report files. Read README.zh-TW.md; main JAR and dependencies are not included.')
    return out
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--output',required=True,type=Path);args=parser.parse_args()
    try:restore(args.output)
    except (OSError,ValueError,KeyError,TypeError,lzma.LZMAError) as error:parser.exit(1,str(error)+'\n')
