#!/usr/bin/env python3
"""Verify and expand the text-only rev201 source/test checkpoint; never overwrite a directory."""
import argparse,base64,hashlib,json,lzma,shutil
from pathlib import Path,PurePosixPath
PIN='b52e34eefe31fdb41c1091b20d504b7f2802e2ccc7c0afcf7ed5f66dc9df5d10'
HERE=Path(__file__).resolve().parent

def safe(root,name):
    p=PurePosixPath(name)
    if p.is_absolute() or '..' in p.parts or '\\' in name:raise ValueError('Unsafe source path')
    path=(root/name).resolve()
    if root.resolve() not in path.parents:raise ValueError('Escaping source path')
    return path

def payload():
    manifest=json.loads((HERE/'payload-manifest.json').read_text(encoding='utf-8'))
    text=''.join(safe(HERE,name).read_text(encoding='ascii') for name in manifest['parts'])
    data=base64.b64decode(''.join(text.split()),validate=True)
    if len(data)!=manifest['compressed_bytes'] or hashlib.sha256(data).hexdigest()!=PIN or manifest['sha256']!=PIN:
        raise ValueError('rev201 source payload checksum mismatch')
    decoder=lzma.LZMADecompressor();raw=decoder.decompress(data,max_length=3_000_001)
    if not decoder.eof or decoder.unused_data or len(raw)>3_000_000 or len(raw)!=manifest['decoded_bytes']:
        raise ValueError('Invalid decoded payload size')
    value=json.loads(raw)
    if value.get('schema')!=1 or len(value['files'])!=manifest['file_count'] or len(value['files'])>128:
        raise ValueError('Unsupported source payload schema')
    files={}
    for name,row in value['files'].items():
        safe(HERE,name);content=row['content'].encode('utf-8')
        if hashlib.sha256(content).hexdigest()!=row['sha256']:raise ValueError('Source file checksum mismatch: '+name)
        files[name]=content
    return files

def unpack(output):
    output=output.resolve();files=payload()
    if output.exists():raise FileExistsError('Output must not already exist')
    output.mkdir(parents=True)
    try:
        for name,data in files.items():
            target=safe(output,name);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
    except BaseException:
        shutil.rmtree(output);raise
    return output

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    print('Expanded verified source/test delta at',unpack(a.output))
if __name__=='__main__':main()
