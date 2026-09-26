#!/usr/bin/env python3
"""Restore complete rev200 source, then apply the verified rev201 delta. Does not build or run Actions."""
import argparse,shutil,subprocess,sys,tempfile
from pathlib import Path
import unpack

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    here=Path(__file__).resolve().parent;repo=here.parent.parent;out=a.output.resolve()
    if out.exists() or out==repo or repo in out.parents:p.error('--output must be new and outside this checkout')
    previous=here.parent/'rev200/restore.py'
    if not previous.is_file():p.error('The full repository and previous checkpoints are required')
    with tempfile.TemporaryDirectory(prefix='lfb-rev201-') as temp:
        delta=unpack.unpack(Path(temp)/'delta')
        subprocess.run([sys.executable,str(previous),'--output',str(out)],check=True)
        subprocess.run([sys.executable,str(delta/'apply.py'),'--source-root',str(out)],check=True)
        shutil.copytree(delta,out/'tools/local-validation/rev201',ignore=shutil.ignore_patterns('__pycache__'))
    print('Restored source at',out,'; no production build or game verification is implied.')
if __name__=='__main__':main()
