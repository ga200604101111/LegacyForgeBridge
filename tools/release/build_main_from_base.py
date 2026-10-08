#!/usr/bin/env python3
"""Produce a complete LFB JAR from an exact old-main baseline and reviewed compiled overlays.

This is a deterministic ZIP-level packager, NOT a Java compiler or compatibility test.
Never use a decompiled guess/old renamed binary as a proven new feature.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import tempfile
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo

from verify_main_jar_overlay import sha256, validate


def gather(root: Path, reviewed: set[str]) -> dict[str, bytes]:
    if not root.is_dir():
        raise ValueError("Overlay root directory is missing")
    overlay: dict[str, bytes] = {}
    for file in sorted(root.rglob("*")):
        if file.is_symlink():
            raise ValueError(f"Overlay symlink is not allowed: {file}")
        if not file.is_file():
            continue
        key = file.relative_to(root).as_posix()
        if key not in reviewed:
            raise ValueError(f"Overlay file not explicitly approved: {key}")
        if key.startswith("/") or ".." in key.split("/") or "\\" in key:
            raise ValueError(f"Unsafe overlay archive path: {key}")
        overlay[key] = file.read_bytes()
    if not overlay:
        raise ValueError("Empty overlay cannot produce a new main JAR")
    unused = sorted(reviewed - set(overlay))
    if unused:
        raise ValueError(f"Expected compiled overlay files are missing: {unused}")
    if "fabric.mod.json" not in overlay:
        raise ValueError("A new main requires a reviewed, version-bumped fabric.mod.json")
    if not any(p.endswith(".class") for p in overlay):
        raise ValueError("No reviewed executable Java class in staged overlay")
    return overlay


def build(base: Path, output: Path, overlay_root: Path, reviewed: set[str],
          expected_base_sha256: str, report_path: Path | None = None) -> dict:
    if not expected_base_sha256:
        raise ValueError("A pinned baseline SHA-256 is required")
    if output.resolve() == base.resolve():
        raise ValueError("Refusing to overwrite original complete main JAR")
    if report_path and report_path.resolve() in {base.resolve(), output.resolve()}:
        raise ValueError("Report path must not overwrite the baseline or candidate JAR")
    if output.exists():
        raise ValueError("Output already exists; choose an unused output name")
    if sha256(base.read_bytes()) != expected_base_sha256.lower():
        raise ValueError("Refusing to build from a different old-main baseline SHA-256")
    overlay = gather(overlay_root, reviewed)
    output.parent.mkdir(parents=True, exist_ok=True)
    fd, name = tempfile.mkstemp(prefix=".lfb-unverified-", suffix=".jar", dir=output.parent)
    os.close(fd)
    stage = Path(name)
    staged_report: Path | None = None
    candidate_linked = False
    try:
        with ZipFile(base, "r") as original, ZipFile(stage, "w") as dest:
            prior = set()
            for info in original.infolist():
                old = info.filename
                if old in prior:
                    raise ValueError(f"Duplicate baseline path: {old}")
                prior.add(old)
                dest.writestr(info, overlay.pop(old, original.read(info)))
            for path, data in sorted(overlay.items()):
                info = ZipInfo(path, date_time=(1980, 1, 1, 0, 0, 0))
                info.create_system = 3
                info.external_attr = 0o100644 << 16
                info.compress_type = ZIP_DEFLATED
                dest.writestr(info, data)
        with ZipFile(base) as original:
            existing = set(original.namelist())
        allowed_add = reviewed - existing
        allowed_change = reviewed & existing
        report = validate(base, stage, allowed_add, allowed_change, expected_base_sha256)
        if report["errors"]:
            raise ValueError("Candidate JAR failed structural release guard: " + "; ".join(report["errors"][:10]))
        # Report filename should identify the delivered candidate, not the temporary ZIP.
        report["candidate"]["name"] = output.name
        if report_path:
            report_path.parent.mkdir(parents=True, exist_ok=True)
            report_fd, report_temp = tempfile.mkstemp(prefix=".lfb-report-", suffix=".json",
                                                    dir=report_path.parent)
            os.close(report_fd)
            staged_report = Path(report_temp)
            staged_report.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        # Hard-linking a completed staged archive fails if the destination appeared in the
        # meantime. os.replace() would silently overwrite a pre-existing user file.
        os.link(stage, output)
        candidate_linked = True
        if staged_report:
            os.replace(staged_report, report_path)
        return report
    except Exception:
        if candidate_linked:
            output.unlink(missing_ok=True)
        raise
    finally:
        stage.unlink(missing_ok=True)
        if staged_report:
            staged_report.unlink(missing_ok=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", required=True, type=Path)
    parser.add_argument("--overlay-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--expect-base-sha256", required=True)
    parser.add_argument("--allow-file", action="append", required=True)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    try:
        result = build(args.base, args.output, args.overlay_root, set(args.allow_file),
                       args.expect_base_sha256, args.report)
    except (OSError, ValueError) as exc:
        print(f"BLOCKED: {exc}", file=sys.stderr)
        return 1
    print("STRUCTURAL_ONLY: complete candidate written: " + str(args.output))
    print("SHA-256: " + result["candidate"]["sha256"])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
