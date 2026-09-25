#!/usr/bin/env python3
"""Restore rev189-194 source into a new directory; never run Actions or modify this checkout."""
import argparse, base64, hashlib, json, lzma, subprocess, sys, tempfile
from pathlib import Path
REVISION = "2026-09-25.194-furniture-world-presentation-and-remote-menus"
PAYLOAD_SHA256 = "e3dbf587ae262c817bcd708fef23641fc3d69888b446774772eb64f0ef43e3db"
PATCH_SHA256 = "a5aaf12636babfba0fa295071fad06b771015ee7d9fbed46fee9ea110fc116b9"

def check(data, expected):
    if hashlib.sha256(data).hexdigest() != expected:
        raise RuntimeError("Source checkpoint checksum mismatch; nothing will be applied")
    return data

def safe_path(root, name):
    rel = Path(name)
    if rel.is_absolute() or ".." in rel.parts:
        raise RuntimeError("Unsafe checkpoint path: " + name)
    return root / rel

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    here = Path(__file__).resolve().parent
    repo = here.parents[1]
    out = args.output.resolve()
    if out.exists() or out == repo or repo in out.parents:
        parser.error("--output must be a new directory outside the checkout")
    archive = b"".join((here / f"source-delta.json.xz.part{i:02d}").read_bytes() for i in range(5))
    payload = json.loads(lzma.decompress(check(archive, PAYLOAD_SHA256)))
    if payload["revision"] != REVISION or payload["base_revision"] != "2026-09-25.193-native-vanilla-display-stacks":
        raise RuntimeError("Unexpected source revision")
    patch = check(payload["patch"].encode("utf-8"), PATCH_SHA256)
    subprocess.run([sys.executable, str(repo / "checkpoints/rev193/restore.py"), "--output", str(out)], check=True)
    with tempfile.TemporaryDirectory(prefix="lfb-rev194-") as temp:
        file = Path(temp) / "source.patch"
        file.write_bytes(patch)
        subprocess.run(["git", "-C", str(out), "apply", "--check", str(file)], check=True)
        subprocess.run(["git", "-C", str(out), "apply", str(file)], check=True)
    for name, encoded in payload["resources"].items():
        target = safe_path(out, name)
        if target.exists():
            raise RuntimeError("Refusing to overwrite an existing resource: " + name)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(base64.b64decode(encoded, validate=True))
    for name, content in payload["validation_sources"].items():
        target = safe_path(out, name)
        if target.exists():
            raise RuntimeError("Refusing to overwrite validation source: " + name)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8")
    info = out / "src/main/java/dev/yinghuang/legacyforgebridge/BuildInfo.java"
    if REVISION not in info.read_text(encoding="utf-8"):
        raise RuntimeError("Restored BuildInfo does not match rev194")
    print("Restored rev194 source into", out)
    print("This is source restoration, not a Gradle/Minecraft/Actions verification run.")

if __name__ == "__main__":
    main()
