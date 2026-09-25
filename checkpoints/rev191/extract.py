"""Verify and extract the complete ordered rev189/rev190/rev191 source patches.
This does not modify the working tree, invoke Gradle, or trigger GitHub Actions.
"""
from pathlib import Path
import argparse
import hashlib
import json
import lzma

BASE = "471db9187a8b17c4cd02a7749c8a09395104e03a"
SHA256 = "e11c923af2812601508d656596662e711176b0bca71d276676e6379bb7180552"
HERE = Path(__file__).resolve().parent

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True, help="New output directory; must not exist")
    args = parser.parse_args()
    packed = b"".join((HERE / f"source-delta.json.xz.{index:02d}").read_bytes() for index in range(4))
    if hashlib.sha256(packed).hexdigest() != SHA256:
        raise SystemExit("Checkpoint integrity verification failed; nothing was extracted")
    payload = json.loads(lzma.decompress(packed).decode("utf-8"))
    if payload.get("base") != BASE or len(payload.get("patches", [])) != 3:
        raise SystemExit("Unexpected checkpoint schema or base commit")
    args.output.mkdir(parents=True, exist_ok=False)
    for revision, patch in zip((189, 190, 191), payload["patches"]):
        if not isinstance(patch, str) or not patch.startswith("diff --git "):
            raise SystemExit(f"Invalid patch payload for revision {revision}")
        (args.output / f"rev{revision}.patch").write_bytes(patch.encode("utf-8"))
    (args.output / "BASE_COMMIT.txt").write_text(BASE + "\n", encoding="utf-8")
    print(f"Extracted three ordered patches based on {BASE} to {args.output}")

if __name__ == "__main__":
    main()
