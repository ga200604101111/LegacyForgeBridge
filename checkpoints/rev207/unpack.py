#!/usr/bin/env python3
"""Verify and unpack the rev207 source checkpoint. Does not build or execute code."""
import argparse, base64, hashlib, json, lzma
from pathlib import Path, PurePosixPath

PIN = '3a6fca2f1854775b3379f6ad50f84ac4164525184077311d9c3bab30503d1caf'

def unpack(output):
    here = Path(__file__).resolve().parent
    out = Path(output).absolute()
    if out.exists() or out.is_symlink():
        raise ValueError('Output must be a new directory')
    manifest = json.loads((here / 'payload-manifest.json').read_text())
    expected = [f'source-payload.json.xz.b64.part{i:02d}' for i in range(4)]
    if manifest['parts'] != expected:
        raise ValueError('Unexpected payload parts')
    encoded = ''.join((here / name).read_text().strip() for name in expected)
    raw = base64.b64decode(encoded, validate=True)
    if len(raw) != 30604 or hashlib.sha256(raw).hexdigest() != PIN:
        raise ValueError('Payload checksum mismatch')
    decoder = lzma.LZMADecompressor(memlimit=128 * 1024 * 1024)
    decoded = decoder.decompress(raw, max_length=141841)
    if len(decoded) != 141840 or not decoder.eof or decoder.unused_data:
        raise ValueError('Unexpected decompressed payload')
    payload = json.loads(decoded)
    if payload['schema'] != 1 or len(payload['files']) != 50:
        raise ValueError('Unexpected payload schema or file count')
    checked = {}
    for entry in payload['files']:
        name = entry['path']; path = PurePosixPath(name)
        if path.is_absolute() or '\\' in name or ':' in name or '..' in path.parts or str(path) != name:
            raise ValueError('Unsafe path: ' + name)
        if not name or name in checked:
            raise ValueError('Empty or duplicate path')
        content = entry['content'].encode('utf-8')
        if hashlib.sha256(content).hexdigest() != entry['sha256']:
            raise ValueError('File checksum mismatch: ' + name)
        checked[name] = content
    out.mkdir(parents=True, exist_ok=False)
    for name, content in checked.items():
        target = out / name; target.parent.mkdir(parents=True, exist_ok=True)
        with target.open('xb') as stream: stream.write(content)
    print('Verified and unpacked', len(checked), 'source/validation files to', out)
    print('No Minecraft validation or full Gradle build is implied; read README.zh-TW.md.')
    return out

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, type=Path)
    unpack(parser.parse_args().output)
