#!/usr/bin/env python3
"""Verify and unpack rev208 source/validation files. Does not build or execute code."""
import argparse
import base64
import hashlib
import json
import lzma
from pathlib import Path, PurePosixPath

PIN = '6c0fe3065cfaf50f6bf1ffd65e4b011c23d950d86d42501b40eefa3a947f3a4d'
PARTS = [f'source-payload.json.xz.b64.part{i:02d}' for i in range(7)]

def unpack(output):
    here = Path(__file__).resolve().parent
    out = Path(output).absolute()
    if out.exists() or out.is_symlink():
        raise ValueError('Output must be a new directory')
    for parent in out.parents:
        if parent.is_symlink():
            raise ValueError('Output ancestors must not be symlinks')
    manifest = json.loads((here / 'payload-manifest.json').read_text(encoding='utf-8'))
    if manifest['schema'] != 1 or manifest['parts'] != PARTS:
        raise ValueError('Unexpected manifest schema or payload parts')
    if manifest['sha256'] != PIN or manifest['files'] != 272:
        raise ValueError('Unexpected manifest checksum or file count')
    encoded = ''.join((here / name).read_text(encoding='ascii').strip() for name in PARTS)
    raw = base64.b64decode(encoded, validate=True)
    if len(raw) != 62988 or hashlib.sha256(raw).hexdigest() != PIN:
        raise ValueError('Payload checksum mismatch')
    decoder = lzma.LZMADecompressor(memlimit=128 * 1024 * 1024)
    decoded = decoder.decompress(raw, max_length=444311)
    if len(decoded) != 444310 or not decoder.eof or decoder.unused_data:
        raise ValueError('Unexpected decompressed payload')
    payload = json.loads(decoded)
    if payload['schema'] != 1 or len(payload['files']) != 272:
        raise ValueError('Unexpected payload schema or file count')
    checked = {}
    for entry in payload['files']:
        name = entry['path']
        path = PurePosixPath(name)
        if not name or '\\' in name or ':' in name or '\0' in name:
            raise ValueError('Unsafe path: ' + repr(name))
        if path.is_absolute() or '..' in path.parts or str(path) != name or name == '.':
            raise ValueError('Unsafe path: ' + name)
        if name in checked:
            raise ValueError('Duplicate path: ' + name)
        content = entry['content'].encode('utf-8')
        if hashlib.sha256(content).hexdigest() != entry['sha256']:
            raise ValueError('File checksum mismatch: ' + name)
        checked[name] = content
    for name in checked:
        if any(str(parent) in checked for parent in PurePosixPath(name).parents):
            raise ValueError('File/directory conflict: ' + name)
    out.mkdir(parents=True, exist_ok=False)
    for name, content in checked.items():
        target = out / name
        target.parent.mkdir(parents=True, exist_ok=True)
        with target.open('xb') as stream:
            stream.write(content)
    print('Verified and unpacked', len(checked), 'source/validation files to', out)
    print('Read README.zh-TW.md for build instructions and validation limitations.')
    return out

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, type=Path)
    try:
        unpack(parser.parse_args().output)
    except (OSError, ValueError, KeyError, lzma.LZMAError) as error:
        parser.exit(1, str(error) + '\n')
