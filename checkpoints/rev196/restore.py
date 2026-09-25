#!/usr/bin/env python3
"""Restore cumulative rev196 source into a new directory; does not build or run Actions."""
from pathlib import Path, PurePosixPath
import argparse, hashlib, json, lzma, subprocess, sys
PIN='5799132c1919c67dd863837d739a944874ccffe62ca4c08a4870f90527802ffa'
def safe(root,name):
    p=PurePosixPath(name)
    if p.is_absolute() or '..' in p.parts or '\\' in name:raise ValueError('Unsafe source path')
    result=(root/name).resolve()
    if root.resolve() not in result.parents:raise ValueError('Escaped destination')
    return result

def load_payload(here):
    manifest=json.loads((here/'payload-manifest.json').read_text())
    data=b''.join(safe(here,name).read_bytes() for name in manifest['parts'])
    if hashlib.sha256(data).hexdigest()!=PIN or manifest['sha256']!=PIN:raise ValueError('Wrong rev196 payload')
    decoded=lzma.decompress(data)
    if len(decoded)!=manifest['decompressed_bytes'] or len(decoded)>10_000_000:raise ValueError('Unexpected payload size')
    value=json.loads(decoded)
    if value['schema']!=1:raise ValueError('Unexpected payload schema')
    return value

def apply_payload(value,output):
    # Verify every old file before changing anything in the newly restored directory.
    for row in value['source_files']:
        dest=safe(output,row['path']);before=row['before_sha256']
        if before is None:
            if dest.exists():raise ValueError('Unexpected new-file conflict: '+row['path'])
        elif not dest.is_file() or hashlib.sha256(dest.read_bytes()).hexdigest()!=before:raise ValueError('Wrong source base: '+row['path'])
        data=value['source_delta'][row['path']].encode('utf-8')
        if hashlib.sha256(data).hexdigest()!=row['after_sha256']:raise ValueError('Wrong source delta')
    for row in value['source_files']:
        dest=safe(output,row['path']);dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(value['source_delta'][row['path']].encode('utf-8'))
    validation=output/'tools/local-validation/rev196';validation.mkdir(parents=True,exist_ok=False)
    for name,text in value['local_build'].items():
        dest=safe(validation,name);dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(text.encode('utf-8'))
    (validation/'source.patch').write_text(value['source_patch'],encoding='utf-8')

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    here=Path(__file__).resolve().parent;repo=here.parents[1];output=a.output.resolve()
    if output.exists() or output==repo or repo in output.parents:p.error('--output must be a new directory outside the checkout')
    value=load_payload(here)
    subprocess.run([sys.executable,str(repo/'checkpoints/rev195/restore.py'),'--output',str(output)],check=True)
    apply_payload(value,output)
    if '2026-09-25.196-source-scale-steam-head-and-books' not in (output/'src/main/java/dev/yinghuang/legacyforgebridge/BuildInfo.java').read_text():raise ValueError('Revision mismatch')
    print('Restored rev196 source:',output)
    print('This does not certify a full Gradle build, Minecraft launch or server gameplay.')
if __name__=='__main__':main()
