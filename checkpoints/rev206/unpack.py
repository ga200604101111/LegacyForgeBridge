#!/usr/bin/env python3
"""Expand the pinned projectile audit source kit. No game build, runtime admission or Actions."""
from pathlib import Path, PurePosixPath
import argparse, base64, hashlib, json, lzma, shutil
HERE=Path(__file__).resolve().parent
PIN='275f4215e937bb2e87515874a207532a22c82d5582afd8855e91e6af1b61293c'

def safe(root,name):
    p=PurePosixPath(name)
    if not name or p.is_absolute() or '..' in p.parts or '\\' in name or ':' in name:
        raise ValueError('Unsafe payload path')
    out=(root/name).resolve()
    if root.resolve() not in out.parents:raise ValueError('Escaping payload path')
    return out

def unpack(output):
    meta=json.loads((HERE/'payload-manifest.json').read_text())
    if meta.get('schema')!=1 or not 1<=len(meta['parts'])<=8:raise ValueError('Invalid manifest')
    text=''.join(safe(HERE,n).read_text(encoding='ascii') for n in meta['parts'])
    if len(text)>500_000:raise ValueError('Encoded budget exceeded')
    data=base64.b64decode(''.join(text.split()),validate=True)
    if len(data)!=meta['compressed_bytes'] or hashlib.sha256(data).hexdigest()!=PIN or meta['sha256']!=PIN:
        raise ValueError('Pinned source checksum mismatch')
    decoder=lzma.LZMADecompressor();raw=decoder.decompress(data,max_length=1_000_001)
    if not decoder.eof or decoder.unused_data or len(raw)>1_000_000 or len(raw)!=meta['decoded_bytes']:
        raise ValueError('Invalid decoded size')
    obj=json.loads(raw)
    if obj.get('schema')!=1 or len(obj['files'])!=meta['file_count'] or len(obj['files'])>128:
        raise ValueError('Invalid source kit')
    files={n:t.encode('utf-8') for n,t in obj['files'].items()}
    for n in files:safe(HERE,n)
    manifest=json.loads(files['source-manifest.json'])
    if set(manifest)!=(set(files)-{'source-manifest.json'}):raise ValueError('Manifest coverage mismatch')
    for n,expected in manifest.items():
        if hashlib.sha256(files[n]).hexdigest()!=expected:raise ValueError('File checksum mismatch: '+n)
    output=output.resolve()
    if output.exists():raise FileExistsError('Output must be new')
    output.mkdir(parents=True)
    try:
        for n,data in files.items():
            target=safe(output,n);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
    except BaseException:
        shutil.rmtree(output);raise
    return len(files)

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',type=Path,required=True);args=p.parse_args()
    print('Verified source audit files:',unpack(args.output),'; new runtime rules=0; no installable JAR.')
