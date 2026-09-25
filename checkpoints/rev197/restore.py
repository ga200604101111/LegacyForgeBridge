#!/usr/bin/env python3
"""Restore cumulative rev197 SOURCE into a new directory; no Actions or build is started."""
import argparse,hashlib,json,lzma,subprocess,sys
from pathlib import Path,PurePosixPath
PIN='5546477781d2127b5d881902ed4cb19a46e896650dd55bf8b967f7139eb2680f'
def safe(root,name):
 p=PurePosixPath(name)
 if p.is_absolute() or '..' in p.parts or '\\' in name:raise ValueError('Unsafe source path')
 dest=(root/name).resolve()
 if root.resolve() not in dest.parents:raise ValueError('Path escapes destination')
 return dest
def load_payload(here):
 manifest=json.loads((here/'payload-manifest.json').read_text(encoding='utf-8'))
 data=b''.join(safe(here,name).read_bytes() for name in manifest['parts'])
 if hashlib.sha256(data).hexdigest()!=PIN or manifest['sha256']!=PIN:raise ValueError('Wrong source payload')
 raw=lzma.decompress(data)
 if len(raw)!=manifest['decompressed_bytes'] or len(raw)>10_000_000:raise ValueError('Unexpected decoded size')
 value=json.loads(raw)
 if value['schema']!=1:raise ValueError('Unknown source schema')
 return value
def apply_payload(value,output):
 for row in value['source_files']:
  dest=safe(output,row['path']);before=row['before_sha256']
  actual=hashlib.sha256(dest.read_bytes()).hexdigest() if dest.is_file() else None
  if actual!=before or (before is None and dest.exists()):raise ValueError('Source base mismatch: '+row['path'])
  data=value['source_delta'][row['path']].encode('utf-8')
  if hashlib.sha256(data).hexdigest()!=row['after_sha256']:raise ValueError('Source delta mismatch')
 for row in value['source_files']:
  dest=safe(output,row['path']);dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(value['source_delta'][row['path']].encode('utf-8'))
 validation=output/'tools/local-validation/rev197';validation.mkdir(parents=True,exist_ok=False)
 for name,text in value['tests'].items():safe(validation,name).write_text(text,encoding='utf-8')
 (validation/'source-files.json').write_text(json.dumps(value['source_files'],indent=2)+'\n',encoding='utf-8')
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--output',type=Path,required=True);a=parser.parse_args()
 here=Path(__file__).resolve().parent;repo=here.parents[1];output=a.output.resolve()
 if output.exists() or output==repo or repo in output.parents:parser.error('--output must be NEW and outside the checkout')
 value=load_payload(here)
 subprocess.run([sys.executable,str(repo/'checkpoints/rev196/restore.py'),'--output',str(output)],check=True)
 apply_payload(value,output)
 if '2026-09-26.197-repeat-books-and-exact-fml-identities' not in (output/'src/main/java/dev/yinghuang/legacyforgebridge/BuildInfo.java').read_text():raise ValueError('Revision mismatch')
 print('Restored rev197 source:',output)
 print('No full build, Minecraft launch or live server certification is implied.')
if __name__=='__main__':main()
