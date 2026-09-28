#!/usr/bin/env python3
"""Restore a self-contained rev210 SOURCE-ONLY test kit. Not the full cumulative main source.
No compilation, dependencies, network access, workflows, or game launch.
"""
import argparse
import base64
import hashlib
import json
import lzma
from pathlib import Path, PurePosixPath

PIN = "adcaecd1b2f2368bbb66faa5a717cf723a52b7581aebed89a25993d3d4b56828"
PARTS = [f"source-payload.json.xz.b64.part{i:02d}" for i in range(3)]
EXPECTED = {
    "schema": 1, "checkpoint": "rev210-source-only",
    "base_commit": "76939f9f2ac51b972a487e611eccfb43718d4b18",
    "files": 35, "parts": PARTS, "compressed_bytes": 21208,
    "decoded_bytes": 112167, "sha256": PIN, "installable": False,
}


def digest(data):
    return hashlib.sha256(data).hexdigest()


def safe(name):
    p = PurePosixPath(name)
    if (not name or name == "." or p.is_absolute() or ".." in p.parts
            or str(p) != name or any(c in name for c in ("\\", ":", "\0"))):
        raise ValueError("Unsafe path: " + repr(name))
    return p


def restore(output):
    here = Path(__file__).resolve().parent
    out = Path(output).absolute()
    if out.exists() or out.is_symlink() or any(p.is_symlink() for p in out.parents):
        raise ValueError("Output must be a new non-symlink directory")
    manifest = json.loads((here / "payload-manifest.json").read_text(encoding="utf-8"))
    if manifest != EXPECTED:
        raise ValueError("Manifest mismatch")
    raw = base64.b64decode("".join((here / name).read_text(encoding="ascii").strip() for name in PARTS), validate=True)
    if len(raw) != 21208 or digest(raw) != PIN:
        raise ValueError("Payload checksum mismatch")
    decoder = lzma.LZMADecompressor(memlimit=128 * 1024 * 1024)
    decoded = decoder.decompress(raw, max_length=112168)
    if len(decoded) != 112167 or not decoder.eof or decoder.unused_data:
        raise ValueError("Decompression boundary")
    payload = json.loads(decoded)
    if payload["schema"] != 1 or len(payload["files"]) != 35:
        raise ValueError("Payload schema/count")
    checked = {}
    for entry in payload["files"]:
        name = entry["path"]
        safe(name)
        data = entry["content"].encode("utf-8")
        if name in checked or digest(data) != entry["sha256"]:
            raise ValueError("Duplicate path or content checksum mismatch")
        checked[name] = data
    for name in checked:
        if any(str(p) in checked for p in PurePosixPath(name).parents):
            raise ValueError("File/directory conflict")
    for name, sha in json.loads(checked["source-sha256.json"]).items():
        safe(name)
        if name not in checked or digest(checked[name]) != sha:
            raise ValueError("Build/test source checksum mismatch: " + name)
    out.mkdir(parents=True, exist_ok=False)
    for name, data in checked.items():
        dest = out / name
        dest.parent.mkdir(parents=True, exist_ok=True)
        with dest.open("xb") as stream:
            stream.write(data)
    print("Restored 35 verified source/test/validation files. SOURCE ONLY: no main JAR, no complete rev209 kit, no game validation.")
    return out


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    try:
        restore(args.output)
    except (OSError, ValueError, KeyError, lzma.LZMAError) as error:
        parser.exit(1, str(error) + "\n")
