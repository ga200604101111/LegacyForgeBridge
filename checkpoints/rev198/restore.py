#!/usr/bin/env python3
"""Restore cumulative rev198 experimental source; never builds or starts Actions."""
from pathlib import Path, PurePosixPath
import argparse
import hashlib
import json
import lzma
import subprocess
import sys
import tempfile

PATCH_PIN = '8560db39b4a306804798bdce0b5c1022d865833d2692fc0840d8a44140649254'
PATCH_XZ_PIN = 'b324ebd577b568351be00b4e2338428702211cc00f2459970876c2ffb8d6b446'
VALIDATION_PIN = 'dcd9657fa8d3ead42169a711adc3ebf8bc93bca07996fff56f4cb5f962095197'


def digest(data):
    return hashlib.sha256(data).hexdigest()


def safe(root, name):
    path = PurePosixPath(name)
    if path.is_absolute() or '..' in path.parts or '\\' in name:
        raise ValueError('Unsafe payload path: ' + name)
    result = (root / name).resolve()
    if root.resolve() not in result.parents:
        raise ValueError('Escaped destination: ' + name)
    return result


def unpack(path, pin, limit):
    encoded = path.read_bytes()
    if digest(encoded) != pin:
        raise ValueError('Wrong compressed checksum: ' + path.name)
    decoder = lzma.LZMADecompressor(memlimit=64 * 1024 * 1024)
    decoded = decoder.decompress(encoded, max_length=limit + 1)
    if len(decoded) > limit or not decoder.eof or decoder.unused_data:
        raise ValueError('Invalid or oversized payload: ' + path.name)
    return decoded


def load_payload(here):
    manifest = json.loads((here / 'payload-manifest.json').read_text(encoding='utf-8'))
    if (manifest['source_patch_compressed_sha256'] != PATCH_XZ_PIN
            or manifest['source_patch_decompressed_sha256'] != PATCH_PIN
            or manifest['validation_compressed_sha256'] != VALIDATION_PIN):
        raise ValueError('Wrong payload manifest')
    patch = unpack(here / 'source.patch.xz', PATCH_XZ_PIN, 1_000_000)
    if digest(patch) != PATCH_PIN:
        raise ValueError('Wrong source patch')
    raw = unpack(here / 'validation-sources.json.xz', VALIDATION_PIN, 1_000_000)
    if len(raw) != manifest['validation_decompressed_bytes']:
        raise ValueError('Wrong validation payload size')
    files = json.loads(raw)
    if not isinstance(files, dict) or len(files) != manifest['validation_files']:
        raise ValueError('Wrong validation file collection')
    for name, text in files.items():
        safe(here, name)
        if not isinstance(text, str):
            raise ValueError('Validation payload must contain UTF-8 text')
    spec = json.loads((here / 'source-manifest.json').read_text(encoding='utf-8'))
    if spec['patch_sha256'] != PATCH_PIN:
        raise ValueError('Wrong source manifest')
    return patch, spec, files


def apply_payload(out, patch, spec, files):
    for row in spec['files']:
        target = safe(out, row['path'])
        if not target.is_file() or digest(target.read_bytes()) != row['before_sha256']:
            raise ValueError('Unexpected rev197 source: ' + row['path'])
    with tempfile.TemporaryDirectory(prefix='lfb-rev198-') as directory:
        patch_file = Path(directory) / 'source.patch'
        patch_file.write_bytes(patch)
        subprocess.run(['git', '-C', str(out), 'apply', '--check', str(patch_file)], check=True)
        subprocess.run(['git', '-C', str(out), 'apply', str(patch_file)], check=True)
    for row in spec['files']:
        if digest(safe(out, row['path']).read_bytes()) != row['after_sha256']:
            raise ValueError('Unexpected patched source: ' + row['path'])
    validation = out / 'tools/local-validation/rev198'
    validation.mkdir(parents=True, exist_ok=False)
    for name, text in files.items():
        dest = safe(validation, name)
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_text(text, encoding='utf-8')
    (validation / 'source.patch').write_bytes(patch)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    here = Path(__file__).resolve().parent
    repo = here.parents[1]
    out = args.output.resolve()
    if out.exists() or out == repo or repo in out.parents:
        parser.error('--output must be a new directory outside the checkout')
    patch, spec, files = load_payload(here)
    subprocess.run([sys.executable, str(repo / 'checkpoints/rev197/restore.py'),
                    '--output', str(out)], check=True)
    apply_payload(out, patch, spec, files)
    print('Restored experimental source:', out)
    print('iYAMATO remains PARTIAL; this is not a build or game/server validation.')


if __name__ == '__main__':
    main()
