#!/usr/bin/env python3
"""Recover the staged Corpus #2 P0 source batch.

This first recovery revision is deliberately non-destructive: it validates and
identifies the interrupted checkpoint payload so the next revision can apply it
with an explicit format/sha contract instead of executing opaque data.
"""

from __future__ import annotations

import base64
import hashlib
import json
from pathlib import Path
import tarfile
import io
import zipfile
import zlib

ROOT = Path(__file__).resolve().parents[1]
PARTS = [ROOT / "tools" / f"corpus2-bc.part{i:02d}" for i in range(3)]


def main() -> None:
    missing = [str(path.relative_to(ROOT)) for path in PARTS if not path.is_file()]
    if missing:
        raise SystemExit(f"missing checkpoint part(s): {', '.join(missing)}")

    encoded = "".join(path.read_text(encoding="ascii").strip() for path in PARTS)
    compressed = base64.b64decode(encoded, validate=True)
    payload = zlib.decompress(compressed)

    print(f"checkpoint parts={len(PARTS)} encoded={len(encoded)} compressed={len(compressed)} payload={len(payload)}")
    print(f"compressed_sha256={hashlib.sha256(compressed).hexdigest()}")
    print(f"payload_sha256={hashlib.sha256(payload).hexdigest()}")
    print(f"payload_prefix_hex={payload[:64].hex()}")
    print("payload_prefix_text=" + repr(payload[:2048].decode("utf-8", errors="backslashreplace")))

    kind = "unknown"
    if payload.startswith((b"diff --git ", b"--- a/", b"*** Begin Patch")):
        kind = "patch"
    elif payload.startswith((b"{", b"[")):
        kind = "json"
        try:
            parsed = json.loads(payload)
            if isinstance(parsed, dict):
                print("json_keys=" + ",".join(sorted(map(str, parsed.keys()))))
            else:
                print(f"json_type={type(parsed).__name__} json_length={len(parsed)}")
        except Exception as exc:  # diagnostic only
            print(f"json_parse_error={exc!r}")
    elif payload.startswith(b"PK\x03\x04"):
        kind = "zip"
        with zipfile.ZipFile(io.BytesIO(payload)) as archive:
            print("zip_entries=" + ",".join(archive.namelist()[:40]))
    else:
        try:
            with tarfile.open(fileobj=io.BytesIO(payload), mode="r:*") as archive:
                kind = "tar"
                print("tar_entries=" + ",".join(member.name for member in archive.getmembers()[:40]))
        except tarfile.TarError:
            pass

    print(f"payload_kind={kind}")
    print("recovery_mode=inspect-only; no source files changed")


if __name__ == "__main__":
    main()
