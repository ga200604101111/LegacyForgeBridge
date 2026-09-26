#!/usr/bin/env python3
"""Restore the complete previous checkpoint chain, then apply the client-only rev200 source delta."""
import argparse
from pathlib import Path
import subprocess
import sys
import apply as delta

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    here=Path(__file__).resolve().parent;repo=here.parent.parent;output=a.output.resolve()
    if output.exists() or output == repo or repo in output.parents:
        p.error('--output must be NEW and outside the checkout')
    previous=here.parent/'rev199/restore.py'
    if not previous.is_file(): p.error('Use the complete repository including checkpoints/rev199')
    delta.payload()  # Validate all new payload files before invoking the old restore chain.
    subprocess.run([sys.executable,str(previous),'--output',str(output)],check=True)
    changed=delta.apply(output)
    print('Client delta applied to restored source: '+str(len(changed))+' files')
    print('No server/original legacy mod modification, binary build or game validation was performed.')
if __name__=='__main__': main()
