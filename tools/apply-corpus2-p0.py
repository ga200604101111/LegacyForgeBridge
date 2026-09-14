#!/usr/bin/env python3
"""Recover the interrupted Corpus #2 P0 source checkpoint safely.

The three corpus2-bc parts are a base64-wrapped zlib stream containing one
unified Git patch.  The payload is pinned by two SHA-256 checksums before it is
allowed anywhere near the working tree.  We also reject path traversal and any
patch target outside the deliberately narrow source/document/build allowlist.

The workflow runs this script before the full Gradle build.  ``git apply
--index`` stages the recovered files as well as applying them to the working
tree, so the workflow can commit exactly the source snapshot that passed CI.
"""

from __future__ import annotations

import base64
import hashlib
from pathlib import Path, PurePosixPath
import re
import subprocess
import zlib

ROOT = Path(__file__).resolve().parents[1]
PARTS = [ROOT / "tools" / f"corpus2-bc.part{i:02d}" for i in range(3)]
EXPECTED_COMPRESSED_SHA256 = "77bda83a88303ef12c8d63245daac53446a3b9b572097fa0914de985f260cf11"
EXPECTED_PAYLOAD_SHA256 = "abf1ebe1a9c60105b039129bec48bb6d74a314b974d7ac7e6fa5fb6fd5ba3d15"
DIFF_HEADER = re.compile(rb"^diff --git a/(.+) b/(.+)$", re.MULTILINE)
ALLOWED_EXACT = {"build.gradle", "gradle.properties"}
ALLOWED_PREFIXES = ("src/", "docs/")


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def safe_path(raw: bytes) -> str:
    try:
        value = raw.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise SystemExit(f"checkpoint contains a non-UTF-8 path: {exc}") from exc

    if not value or value.startswith("/") or "\\" in value or "\x00" in value:
        raise SystemExit(f"unsafe checkpoint path: {value!r}")

    pure = PurePosixPath(value)
    if pure.is_absolute() or any(part in {"", ".", ".."} for part in pure.parts):
        raise SystemExit(f"unsafe checkpoint path: {value!r}")

    if value not in ALLOWED_EXACT and not value.startswith(ALLOWED_PREFIXES):
        raise SystemExit(f"checkpoint target is outside the recovery allowlist: {value}")
    return value


def run_git_apply(*args: str, payload: bytes) -> None:
    completed = subprocess.run(
        ["git", "apply", *args, "-"],
        cwd=ROOT,
        input=payload,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    output = completed.stdout.decode("utf-8", errors="replace")
    if output:
        print(output, end="" if output.endswith("\n") else "\n")
    if completed.returncode != 0:
        raise SystemExit(f"git apply {' '.join(args)} failed with exit code {completed.returncode}")


def main() -> None:
    missing = [str(path.relative_to(ROOT)) for path in PARTS if not path.is_file()]
    if missing:
        raise SystemExit(f"missing checkpoint part(s): {', '.join(missing)}")

    encoded = "".join(path.read_text(encoding="ascii").strip() for path in PARTS)
    try:
        compressed = base64.b64decode(encoded, validate=True)
    except Exception as exc:
        raise SystemExit(f"checkpoint base64 validation failed: {exc}") from exc

    compressed_sha = sha256(compressed)
    if compressed_sha != EXPECTED_COMPRESSED_SHA256:
        raise SystemExit(
            "compressed checkpoint SHA-256 mismatch: "
            f"expected {EXPECTED_COMPRESSED_SHA256}, got {compressed_sha}"
        )

    try:
        payload = zlib.decompress(compressed)
    except zlib.error as exc:
        raise SystemExit(f"checkpoint zlib validation failed: {exc}") from exc

    payload_sha = sha256(payload)
    if payload_sha != EXPECTED_PAYLOAD_SHA256:
        raise SystemExit(
            "patch payload SHA-256 mismatch: "
            f"expected {EXPECTED_PAYLOAD_SHA256}, got {payload_sha}"
        )
    if not payload.startswith(b"diff --git a/"):
        raise SystemExit("checkpoint payload is not the expected unified Git diff")

    headers = DIFF_HEADER.findall(payload)
    if not headers:
        raise SystemExit("checkpoint patch contains no diff headers")

    paths: list[str] = []
    for left_raw, right_raw in headers:
        left = safe_path(left_raw)
        right = safe_path(right_raw)
        if left != right:
            raise SystemExit(f"checkpoint rename/copy is not permitted: {left} -> {right}")
        if left not in paths:
            paths.append(left)

    print(
        f"checkpoint verified: parts={len(PARTS)} compressed={len(compressed)} "
        f"payload={len(payload)} files={len(paths)}"
    )
    print(f"compressed_sha256={compressed_sha}")
    print(f"payload_sha256={payload_sha}")
    for path in paths:
        print(f"patch_target={path}")

    # First prove the complete patch applies atomically.  Only then mutate/stage.
    run_git_apply("--check", "--whitespace=error-all", payload=payload)
    run_git_apply("--index", "--whitespace=error-all", payload=payload)
    print("checkpoint recovery applied and staged successfully")


if __name__ == "__main__":
    main()
