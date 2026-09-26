#!/usr/bin/env python3
"""Expand pinned rev202-205 source kits into a NEW directory; no build or Actions dispatch."""
from pathlib import Path, PurePosixPath
import argparse, base64, hashlib, json, lzma, shutil
HERE = Path(__file__).resolve().parent
PIN = 'f2dde0cc331085d14319c6c4c0e45d710512a2f9d9092b7e0f976e89a3b24e15'

def safe(root, name):
    p = PurePosixPath(name)
    if p.is_absolute() or '..' in p.parts or '\\' in name or ':' in name:
        raise ValueError('Unsafe payload path: ' + name)
    target = (root / name).resolve()
    if root.resolve() not in target.parents:
        raise ValueError('Escaping payload path: ' + name)
    return target

def payload():
    meta = json.loads((HERE / 'payload-manifest.json').read_text())
    if meta.get('schema') != 1 or len(meta['parts']) > 32:
        raise ValueError('Invalid payload manifest')
    text = ''.join(safe(HERE, n).read_text(encoding='ascii') for n in meta['parts'])
    if len(text) > 2_000_000: raise ValueError('Encoded payload budget exceeded')
    data = base64.b64decode(''.join(text.split()), validate=True)
    if len(data) != meta['compressed_bytes'] or hashlib.sha256(data).hexdigest() != PIN or meta['sha256'] != PIN:
        raise ValueError('Pinned source checksum mismatch')
    decoder = lzma.LZMADecompressor()
    raw = decoder.decompress(data, max_length=8_000_001)
    if not decoder.eof or decoder.unused_data or len(raw) > 8_000_000 or len(raw) != meta['decoded_bytes']:
        raise ValueError('Invalid decoded payload size')
    obj = json.loads(raw)
    if obj.get('schema') != 1 or len(obj['files']) != meta['file_count'] or len(obj['files']) > 2000:
        raise ValueError('Invalid source payload schema/count')
    files = {name: text.encode('utf-8') for name, text in obj['files'].items()}
    for name in files: safe(HERE, name)
    # Retain and validate the original per-revision manifests, not just the transport hash.
    for rev in (202, 203, 204, 205):
        name = 'source-index.json' if rev == 202 else 'source-manifest.json'
        manifest = json.loads(files[f'rev{rev}/{name}'])
        for rel, value in manifest.items():
            expected = value['sha256'] if rev == 202 else value
            if hashlib.sha256(files[f'rev{rev}/{rel}']).hexdigest() != expected:
                raise ValueError(f'Original source checksum mismatch: rev{rev}/{rel}')
    return files

def unpack(output):
    output = output.resolve()
    if output.exists(): raise FileExistsError('Output must not already exist')
    files = payload()
    output.mkdir(parents=True)
    try:
        for name, data in files.items():
            target = safe(output, name)
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
    except BaseException:
        shutil.rmtree(output)
        raise
    return len(files)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    print('Verified and expanded', unpack(args.output), 'source files to', args.output)
    print('Root src is unchanged; use the extracted revision rebuild.py with its pinned base main.')
if __name__ == '__main__': main()
