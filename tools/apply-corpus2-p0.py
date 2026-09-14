#!/usr/bin/env python3
from pathlib import Path
import base64, hashlib, subprocess, zlib

root = Path(__file__).resolve().parent.parent
payload = root / 'tools/corpus2-p0.patch.z.b64'
raw = zlib.decompress(base64.b64decode(payload.read_text(encoding='ascii'), validate=True))
expected = '923178a30efbd418f0612507d657aadaadcdfa0d6ffc66ce97d916f85340341a'
if hashlib.sha256(raw).hexdigest() != expected:
    raise SystemExit('corpus2 P0 patch checksum mismatch')
patch = root / '.corpus2-p0.patch'
patch.write_bytes(raw)
subprocess.run(['git','apply','--check',str(patch)], cwd=root, check=True)
subprocess.run(['git','apply',str(patch)], cwd=root, check=True)
patch.unlink()
payload.unlink()
Path(__file__).unlink()
subprocess.run(['git','diff','--check'], cwd=root, check=True)
print('Applied checksum-verified Corpus #2 P0 generic conversion batch.')
