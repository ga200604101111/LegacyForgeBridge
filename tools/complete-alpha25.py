#!/usr/bin/env python3
"""Apply the exact locally tested source batch, then remove this one-shot transport.

Each input and output file is verified against its Git blob hash before any write.
The compressed payload contains source edits only, not the external RPGTool binary.
"""
from pathlib import Path
import base64
import hashlib
import json
import subprocess
import zlib

root = Path(__file__).resolve().parent.parent
parts = [root / f'tools/alpha25-edits-{i}.b64' for i in range(3)]
expected = ['6a047db69acfe651269cd3079947a5fcbf606de8',
            '82e9d644a24cdaecf56307660d565e99af9273ff',
            '0157e1cc707db33e43182c4f9e2aefb5ba32bc0d']
def blob(data):
    return hashlib.sha1(b'blob ' + str(len(data)).encode('ascii') + b'\0' + data).hexdigest()

texts = [path.read_text(encoding='utf-8') for path in parts]
# Correct four known transcription errors in the transfer, not in source code.
texts[0] = texts[0].replace('qIiMh4' + 'eHh4' * 3 + 'f', 'qIiMh4' + 'eHh4' * 2 + 'f').replace('wyC2qZ27', 'wyCqZ27')
texts[1] = texts[1].replace('HP3lu//', 'HP3l0//').replace('kHLlcV5HO5', 'kHLlcVHO5')
for text, expected_hash in zip(texts, expected):
    if blob(text.encode('ascii')) != expected_hash:
        raise ValueError('Source transfer part checksum mismatch')
raw = zlib.decompress(base64.b64decode(''.join(texts), validate=True))
if hashlib.sha256(raw).hexdigest() != '167290ffbbff5c8c07ec90420056644ff7cdcbc6f89e1c271adf8157345c2137':
    raise ValueError('Source batch checksum mismatch')
changes = json.loads(raw)
if len(changes) != 17:
    raise ValueError('Unexpected source file count')
outputs = {}
for change in changes:
    relative = Path(change['path'])
    if relative.is_absolute() or '..' in relative.parts or relative.parts[0] not in ('src', 'docs'):
        raise ValueError('Source path outside permitted directories')
    path = root / relative
    if path.is_symlink() or not path.resolve().is_relative_to(root):
        raise ValueError('Unsafe source path')
    if path in outputs:
        raise ValueError('Duplicate source path')
    before = path.read_bytes() if path.is_file() else None
    if (blob(before) if before is not None else None) != change['before']:
        raise ValueError('Source base changed: ' + str(relative))
    source = before.decode('utf-8') if before is not None else ''
    last = 0
    for start, end, replacement in change['edits']:
        if not (last <= start <= end <= len(source)) or not isinstance(replacement, str):
            raise ValueError('Invalid source edit interval')
        last = end
    result = source
    for start, end, replacement in reversed(change['edits']):
        result = result[:start] + replacement + result[end:]
    data = result.encode('utf-8')
    if blob(data) != change['after']:
        raise ValueError('Source result checksum mismatch: ' + str(relative))
    outputs[path] = data
for path, data in outputs.items():
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
for path in parts:
    path.unlink()
Path(__file__).unlink()
subprocess.run(['git', 'diff', '--check'], cwd=root, check=True)
print('Applied and verified all 17 source/doc/test files; removed one-shot transfer files.')
