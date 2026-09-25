#!/usr/bin/env python3
"""Restore rev189-193 in a NEW directory. The checkout and GitHub Actions are not modified."""
import argparse
import hashlib
import json
import lzma
from pathlib import Path
import re
import subprocess
import tarfile
import tempfile

REVISION = "2026-09-25.193-native-vanilla-display-stacks"
PATCH_SHA256 = "73fd58696acf24f85ad0f48c1ad5b0348282b9add5e57170785905ebf9108476"

def checked(data, expected):
    actual = hashlib.sha256(data).hexdigest()
    if actual != expected:
        raise RuntimeError(f"Checksum mismatch: expected {expected}, got {actual}")
    return data

def normalize_patch(text):
    # Old checkpoints omit the new-file mode on some /dev/null diffs. Change
    # only this Git metadata; leave every source line and hunk unchanged.
    sections = re.split(r"(?=^diff --git )", text, flags=re.MULTILINE)
    result = []
    for part in sections:
        if part.startswith("diff --git ") and "\n--- /dev/null\n" in part and "\nnew file mode " not in part:
            first, rest = part.split("\n", 1)
            part = first + "\nnew file mode 100644\n" + rest
        result.append(part)
    return "".join(result)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, type=Path, help="New, nonexistent directory outside this checkout")
    args = parser.parse_args()
    here = Path(__file__).resolve().parent
    repo = here.parents[1]
    out = args.output.resolve()
    if out.exists() or repo == out or repo in out.parents:
        parser.error("--output must be a nonexistent directory outside the source checkout")
    patches191 = b"".join((repo / "checkpoints/rev191" / f"source-delta.json.xz.{i:02d}").read_bytes() for i in range(4))
    checked(patches191, "e11c923af2812601508d656596662e711176b0bca71d276676e6379bb7180552")
    prior = json.loads(lzma.decompress(patches191))
    if prior["base"] != "471db9187a8b17c4cd02a7749c8a09395104e03a":
        raise RuntimeError("Unexpected checkpoint base")
    patches192 = b"".join((repo / "checkpoints/rev192" / f"source-delta.json.xz.part{i}").read_bytes() for i in range(2))
    checked(patches192, "feda0413cf2b51e8ae270c3e49baea690b1f2e9d6e892b789e0181fa199aa845")
    latest = json.loads(lzma.decompress(patches192))
    patch193 = checked((here / "source.patch").read_bytes(), PATCH_SHA256).decode("utf-8")
    patches = [*prior["patches"], latest["patch"], patch193]
    with tempfile.TemporaryDirectory(prefix="lfb-rev193-") as temporary:
        temp = Path(temporary)
        archive = temp / "source.tar"
        with archive.open("wb") as output:
            subprocess.run(["git", "-C", str(repo), "archive", "HEAD"], stdout=output, check=True)
        out.mkdir(parents=True)
        with tarfile.open(archive) as source:
            for entry in source.getmembers():
                path = Path(entry.name)
                if path.is_absolute() or ".." in path.parts or entry.issym() or entry.islnk():
                    raise RuntimeError(f"Unsafe archive entry: {entry.name}")
            source.extractall(out)
        subprocess.run(["git", "init", "--quiet", str(out)], check=True)
        build_info = out / "src/main/java/dev/yinghuang/legacyforgebridge/BuildInfo.java"
        if "2026-09-24.188-resource-case-fingerprint" not in build_info.read_text(encoding="utf-8"):
            raise RuntimeError("Root source is not rev188; stop rather than overwriting newer work")
        for revision, text in enumerate(patches, start=189):
            patch = temp / f"rev{revision}.patch"
            patch.write_text(normalize_patch(text), encoding="utf-8", newline="\n")
            command = ["git", "-C", str(out), "apply", "--unidiff-zero"]
            subprocess.run([*command, "--check", str(patch)], check=True)
            subprocess.run([*command, str(patch)], check=True)
            print(f"Applied rev{revision}")
        if REVISION not in build_info.read_text(encoding="utf-8"):
            raise RuntimeError("Final revision mismatch")
    print(f"Restored native rev193 source: {out}")
    print("This restores source only; it does not run Gradle, Minecraft, or GitHub Actions.")

if __name__ == "__main__":
    main()
