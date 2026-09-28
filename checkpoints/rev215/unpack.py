#!/usr/bin/env python3
"""Restore pinned rev215 source/build/test inputs. No network, build, game launch or Actions."""
import argparse,base64,hashlib,json,lzma
from pathlib import Path,PurePosixPath
PIN='c3aee0117c66c35a7a3053ff8624e94f76261b5022205e9d61d42985ab04c2a2'
PARTS=[f'source-payload.json.xz.b64.part{i:02d}' for i in range(7)]
COUNT=45;COMPRESSED=57756;DECODED=284849
CORRECTION='abf715145abc35f15230d440c9ce2e2e6094550f7e20a934959bdb61173163f6'

def sha(b):return hashlib.sha256(b).hexdigest()
def safe(name):
    if not isinstance(name,str):raise ValueError('Path must be text')
    p=PurePosixPath(name)
    if not name or name=='.' or p.is_absolute() or '..' in p.parts or str(p)!=name or any(c in name for c in ('\\',':','\0')) or any(ord(c)<32 for c in name):raise ValueError('Unsafe path')
    return p

def restore(output):
    here=Path(__file__).resolve().parent;out=Path(output).absolute()
    if out.exists() or out.is_symlink() or any(p.is_symlink() for p in out.parents):raise ValueError('Output must be new and non-symlink')
    m=json.loads((here/'payload-manifest.json').read_text(encoding='utf-8'))
    if m['sha256']!=PIN or m['parts']!=PARTS or m['files']!=COUNT or m['compressed_bytes']!=COMPRESSED or m['decoded_bytes']!=DECODED or m.get('metadata_correction_sha256')!=CORRECTION or m['main_in_checkpoint'] is not False or m['source_payload_installable'] is not False:raise ValueError('Manifest mismatch')
    raw=base64.b64decode(''.join((here/n).read_text(encoding='ascii').strip() for n in PARTS),validate=True)
    if len(raw)!=COMPRESSED or sha(raw)!=PIN:raise ValueError('Payload checksum mismatch')
    decoder=lzma.LZMADecompressor(memlimit=128*1024*1024);decoded=decoder.decompress(raw,max_length=DECODED+1)
    if len(decoded)!=DECODED or not decoder.eof or decoder.unused_data:raise ValueError('Decompression boundary')
    payload=json.loads(decoded);checked={};folded=set()
    if payload['schema']!=1 or len(payload['files'])!=COUNT:raise ValueError('Schema or count mismatch')
    for e in payload['files']:
        name=e['path'];safe(name);b=e['content'].encode('utf-8')
        if name.casefold() in folded or sha(b)!=e['sha256']:raise ValueError('Duplicate or corrupt entry')
        checked[name]=b;folded.add(name.casefold())
    for name in checked:
        if any(str(p).casefold() in folded for p in PurePosixPath(name).parents):raise ValueError('File/directory conflict')
    for name,expected in json.loads(checked['source-sha256.json']).items():
        if name not in checked or sha(checked[name])!=expected:raise ValueError('Build input mismatch')
    # The capsule is preserved; apply the pinned documentation-only provenance erratum.
    correction_bytes=(here/'metadata-correction.json').read_bytes()
    if sha(correction_bytes)!=CORRECTION:raise ValueError('Metadata correction checksum mismatch')
    correction=json.loads(correction_bytes)
    if correction['schema']!=1 or len(correction['files'])!=2:raise ValueError('Metadata correction schema')
    seen=set()
    for entry in correction['files']:
        name=entry['path']
        if name not in {'README.zh-TW.md','validation/summary.json'} or name in seen:raise ValueError('Metadata correction scope')
        seen.add(name);before=checked[name]
        if sha(before)!=entry['before_sha256']:raise ValueError('Metadata correction preimage')
        after=before.decode('utf-8')
        for replacement in entry['replacements']:
            if after.count(replacement['old'])!=1:raise ValueError('Metadata correction replacement')
            after=after.replace(replacement['old'],replacement['new'])
        checked[name]=after.encode('utf-8')
        if sha(checked[name])!=entry['after_sha256']:raise ValueError('Metadata correction postimage')
    out.mkdir(parents=True,exist_ok=False)
    for name,b in checked.items():
        dest=out/name;dest.parent.mkdir(parents=True,exist_ok=True)
        with dest.open('xb') as f:f.write(b)
    print('Restored 45 verified source/build/test/doc/summary files. Read README.zh-TW.md. Main JAR, dependency JARs, corpus and full raw verification logs are not in this capsule.')
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',required=True,type=Path);a=p.parse_args()
    try:restore(a.output)
    except (OSError,ValueError,KeyError,TypeError,lzma.LZMAError) as e:p.exit(1,str(e)+'\n')
