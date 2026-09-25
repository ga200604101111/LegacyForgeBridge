"""Extract the verified rev192 source delta; never apply or overwrite a working tree."""
from pathlib import Path
import argparse, hashlib, json, lzma
HOME = Path(__file__).resolve().parent
EXPECTED = "feda0413cf2b51e8ae270c3e49baea690b1f2e9d6e892b789e0181fa199aa845"
p = argparse.ArgumentParser()
p.add_argument("--output", type=Path, required=True)
a = p.parse_args()
data = b"".join((HOME / ("source-delta.json.xz.part" + str(i))).read_bytes() for i in range(2))
if hashlib.sha256(data).hexdigest() != EXPECTED:
    raise SystemExit("Source checkpoint hash mismatch")
value = json.loads(lzma.decompress(data))
a.output.mkdir(parents=True, exist_ok=True)
path = a.output / "rev192-from-rev191.patch"
with path.open("x", encoding="utf-8", newline="\n") as out:
    out.write(value["patch"])
print("Extracted:", path)
print("Restore checkpoints/rev191 first, then review and git apply --check this delta.")
