#!/usr/bin/env python3
"""Restore rev198, then add the source-only rev199 evidence utility. Does not build."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--output', type=Path, required=True)
    args = p.parse_args()
    root = Path(__file__).resolve().parent
    repo = root.parent.parent
    output = args.output.resolve()
    if output.exists() or output == repo or repo in output.parents:
        p.error('Output must be new and outside this checkout')
    manifest = json.loads((root / 'source-manifest.json').read_text())
    for rel, expected in manifest.items():
        if hashlib.sha256((root / rel).read_bytes()).hexdigest() != expected:
            raise ValueError('Checksum mismatch: ' + rel)
    previous = root.parent / 'rev198' / 'restore.py'
    if not previous.is_file():
        p.error('Use the complete repository: checkpoints/rev198/restore.py is required')
    subprocess.run([sys.executable, str(previous), '--output', str(output)], check=True)
    for rel in manifest:
        if not rel.startswith('src/'):
            continue
        target = output / rel
        if target.exists():
            raise FileExistsError('Refusing to overwrite restored baseline: ' + str(target))
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(root / rel, target)
    # Retain the same layout so the standalone runner remains self-contained.
    validation = output / 'tools' / 'local-validation' / 'rev199'
    shutil.copytree(root, validation, ignore=shutil.ignore_patterns('__pycache__'))
    print('Restored source evidence utility only. Conversion/runtime admission is unchanged.')

if __name__ == '__main__':
    main()
