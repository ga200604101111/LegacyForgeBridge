#!/usr/bin/env python3
"""Restore rev189-195 source into a new directory. Does not run Actions or build Minecraft."""
import argparse, hashlib, subprocess, sys
from pathlib import Path
PATCH_SHA256 = '993993f0642ddd11a981983718705af91e29b62102fa6461c2b744265aea885a'
REVISION = '2026-09-25.195-gridpot-property-abi'

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    here = Path(__file__).resolve().parent
    repo = here.parents[1]
    output = args.output.resolve()
    if output.exists() or output == repo or repo in output.parents:
        parser.error('--output must be a new directory outside the checkout')
    patch = here/'source.patch'
    if hashlib.sha256(patch.read_bytes()).hexdigest() != PATCH_SHA256:
        raise RuntimeError('Unexpected rev195 source patch hash')
    subprocess.run([sys.executable, str(repo/'checkpoints/rev194/restore.py'), '--output', str(output)], check=True)
    subprocess.run(['git', '-C', str(output), 'apply', '--check', str(patch)], check=True)
    subprocess.run(['git', '-C', str(output), 'apply', str(patch)], check=True)
    validation = output/'tools/local-validation/rev195'
    validation.mkdir(parents=True, exist_ok=False)
    for name in ['check_property_abi.py', 'PropertyAbiRegression195.java', 'ClockAbiRegression195.java', 'PatchRuntime195.java', 'property-api-fixtures.json']:
        (validation/name).write_bytes((here/name).read_bytes())
    if REVISION not in (output/'src/main/java/dev/yinghuang/legacyforgebridge/BuildInfo.java').read_text():
        raise RuntimeError('Restored converter revision mismatch')
    print('Restored rev195 source into', output)
    print('This is source restoration, not a full build or Minecraft integration test.')

if __name__ == '__main__': main()
