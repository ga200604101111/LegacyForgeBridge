#!/usr/bin/env python3
from pathlib import Path
import base64, hashlib, subprocess, zlib

root = Path(__file__).resolve().parent.parent
shards = [root / f'tools/corpus2-p0.part{i:02d}' for i in range(5)]
encoded = ''.join(path.read_text(encoding='ascii') for path in shards)
raw = zlib.decompress(base64.b64decode(encoded, validate=True))
expected = '923178a30efbd418f0612507d657aadaadcdfa0d6ffc66ce97d916f85340341a'
if hashlib.sha256(raw).hexdigest() != expected:
    raise SystemExit('corpus2 P0 patch checksum mismatch')
patch = root / '.corpus2-p0.patch'
patch.write_bytes(raw)
subprocess.run(['git','apply','--check',str(patch)], cwd=root, check=True)
subprocess.run(['git','apply',str(patch)], cwd=root, check=True)
patch.unlink()

fix = root / 'tools/corpus2-p0-fix.patch'
fix_raw = fix.read_bytes()
if hashlib.sha256(fix_raw).hexdigest() != '1246fb225c44976e90ec1a3b3d8c053cde30f32fb15a380bc88674c88bd2fe96':
    raise SystemExit('corpus2 P0 regression fix checksum mismatch')
subprocess.run(['git','apply','--check',str(fix)], cwd=root, check=True)
subprocess.run(['git','apply',str(fix)], cwd=root, check=True)
fix.unlink()

for path in shards:
    path.unlink()
broken = root / 'tools/corpus2-p0.patch.z.b64'
if broken.exists():
    broken.unlink()
Path(__file__).unlink()
subprocess.run(['git','diff','--check'], cwd=root, check=True)
print('Applied checksum-verified Corpus #2 P0 generic conversion batch and regression fix.')
