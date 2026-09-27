#!/usr/bin/env python3
"""Restore rev209 source kit from its verified delta and unchanged rev208 sources. No build/network/Actions."""
import argparse,base64,hashlib,json,lzma,subprocess,sys,tempfile
from pathlib import Path,PurePosixPath
PIN='4ce478ee4fc86a74fa94ce9d3a2c35a2bed16694fc456b0cc63f268b667c6aed'
PARTS=[f'source-payload.json.xz.b64.part{i:02d}' for i in range(6)]
def safe(name):
    p=PurePosixPath(name)
    if not name or name=='.' or p.is_absolute() or '..' in p.parts or str(p)!=name or any(x in name for x in ('\\',':','\0')):raise ValueError('Unsafe path: '+repr(name))
    return p
def digest(b):return hashlib.sha256(b).hexdigest()
def restore(output,base_kit=None):
    here=Path(__file__).resolve().parent;out=Path(output).absolute()
    if out.exists() or out.is_symlink() or any(p.is_symlink() for p in out.parents):raise ValueError('Output must be a new non-symlink directory')
    manifest=json.loads((here/'payload-manifest.json').read_text())
    if manifest['parts']!=PARTS or manifest['files']!=70 or manifest['sha256']!=PIN:raise ValueError('Manifest mismatch')
    raw=base64.b64decode(''.join((here/p).read_text().strip() for p in PARTS),validate=True)
    if len(raw)!=51432 or digest(raw)!=PIN:raise ValueError('Payload checksum mismatch')
    decoder=lzma.LZMADecompressor(memlimit=128*1024*1024);decoded=decoder.decompress(raw,max_length=253844)
    if len(decoded)!=253843 or not decoder.eof or decoder.unused_data:raise ValueError('Decompression boundary')
    payload=json.loads(decoded);checked={}
    if payload['schema']!=1 or len(payload['files'])!=70:raise ValueError('Payload schema/count')
    for e in payload['files']:
        name=e['path'];safe(name);b=e['content'].encode('utf-8')
        if name in checked or digest(b)!=e['sha256']:raise ValueError('Duplicate path or content checksum mismatch')
        checked[name]=b
    with tempfile.TemporaryDirectory(prefix='lfb-rev209-') as temp:
        base=Path(base_kit).resolve() if base_kit else Path(temp)/'rev208'
        if not base_kit:
            script=here.parent/'rev208/unpack.py'
            if not script.is_file():raise ValueError('Full checkout required, or pass --base-kit pointing at an unpacked rev208 source kit')
            subprocess.run([sys.executable,str(script),'--output',str(base)],check=True)
        reuse=json.loads(checked['reuse-rev208.json'])
        if len(reuse)!=199:raise ValueError('Reuse manifest count')
        for name,e in reuse.items():
            safe(name);safe(e['path']);p=base/e['path']
            if name in checked or p.is_symlink() or base not in p.resolve().parents:raise ValueError('Invalid inherited path')
            b=p.read_bytes()
            if digest(b)!=e['sha256']:raise ValueError('Inherited source checksum mismatch: '+name)
            checked[name]=b
        source_hashes=json.loads(checked['source-sha256.json'])
        for name,sha in source_hashes.items():
            if name not in checked or digest(checked[name])!=sha:raise ValueError('Build source hash mismatch: '+name)
        if len(checked)!=269:raise ValueError('Restored file count')
        for name in checked:
            if any(str(p) in checked for p in PurePosixPath(name).parents):raise ValueError('File/directory conflict')
        out.mkdir(parents=True,exist_ok=False)
        for name,b in checked.items():
            p=out/name;p.parent.mkdir(parents=True,exist_ok=True)
            with p.open('xb') as f:f.write(b)
    print('Restored 269 verified source/compact-validation files; read README.zh-TW.md. No main build or game verification is implied.')
    return out
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',required=True,type=Path);p.add_argument('--base-kit',type=Path)
    a=p.parse_args()
    try:restore(a.output,a.base_kit)
    except (OSError,ValueError,KeyError,lzma.LZMAError,subprocess.CalledProcessError) as e:p.exit(1,str(e)+'\n')
