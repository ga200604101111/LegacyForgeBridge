#!/usr/bin/env python3
"""Fail-closed release guard for a new LFB main JAR over a known-good old main.

Structural provenance only: does NOT prove Minecraft/FML/runtime compatibility.
Use with an explicitly reviewed change allowlist; never mutate the baseline JAR.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path
from zipfile import BadZipFile, ZipFile

CRITICAL_RESOURCE = "fabric.mod.json"
PROTECTED_PREFIXES = ("META-INF/jars/", "META-INF/lfb/")
DENY_TEST_PARTS = ("/src/test/", "org/junit/", "junit/framework/")
PINNED_BASE_CLASSES = (
    "dev/yinghuang/legacyforgebridge/convert/pass/Corpus3GenericCompletionPass.class",
    "dev/yinghuang/legacyforgebridge/convert/LegacyProjectileLaunchGraph.class",
    "dev/yinghuang/legacyforgebridge/desktop/DesktopConversionSession.class",
    "dev/yinghuang/legacyforgebridge/rev260/HandoffIO.class",
)


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def scan(path: Path) -> tuple[dict[str, dict], dict]:
    entries: dict[str, dict] = {}
    with ZipFile(path) as jar:
        bad = jar.testzip()
        if bad:
            raise ValueError(f"ZIP CRC failure: {bad}")
        for item in jar.infolist():
            name = item.filename
            if name in entries:
                raise ValueError(f"Duplicate ZIP entry: {name}")
            if name.startswith(("/", "\\")) or "\\" in name or ".." in name.split("/"):
                raise ValueError(f"Unsafe ZIP entry path: {name}")
            if item.flag_bits & 1:
                raise ValueError(f"Encrypted ZIP entry: {name}")
            if item.is_dir():
                continue
            data = jar.read(item)
            if name.endswith(".class") and not data.startswith(b"\xca\xfe\xba\xbe"):
                raise ValueError(f"Invalid Java class magic: {name}")
            entries[name] = {"sha256": sha256(data), "size": len(data)}
        try:
            mod = json.loads(jar.read(CRITICAL_RESOURCE).decode("utf-8"))
        except (KeyError, UnicodeDecodeError, json.JSONDecodeError) as exc:
            raise ValueError("Invalid or missing fabric.mod.json") from exc
    if mod.get("id") != "legacyforgebridge":
        raise ValueError("Unexpected Fabric mod id")
    return entries, mod


def inventory(path: Path, expected_hash: str | None = None) -> dict:
    digest = sha256(path.read_bytes())
    if expected_hash and digest.lower() != expected_hash.lower():
        raise ValueError("Baseline SHA-256 differs from explicitly pinned reference")
    entries, mod = scan(path)
    with ZipFile(path) as jar:
        info = json.loads(jar.read("legacyforgebridge/rev260-build.json")) if (
            "legacyforgebridge/rev260-build.json" in entries) else None
    missing = sorted(set(PINNED_BASE_CLASSES) - set(entries))
    required = {name: entries[name]["sha256"] for name in PINNED_BASE_CLASSES if name in entries}
    return {
        "schemaVersion": 1,
        "kind": "legacyforgebridge-base-inventory",
        "file": path.name,
        "sha256": digest,
        "bytes": path.stat().st_size,
        "zipCrc": "PASS",
        "fileEntries": len(entries),
        "classEntries": sum(name.endswith(".class") for name in entries),
        "nestedJars": {name: entry["sha256"] for name, entry in entries.items() if name.endswith(".jar")},
        "requiredClassesPresent": not missing,
        "requiredClassSha256": required,
        "missingRequiredClasses": missing,
        "fabric": {"id": mod.get("id"), "version": mod.get("version"),
                   "environment": mod.get("environment"), "depends": mod.get("depends"),
                   "entrypoints": mod.get("entrypoints"), "mixins": mod.get("mixins")},
        "buildProvenance": info,
        "boundary": "Archive evidence, not a new build or live game compatibility test",
    }


def validate(base: Path, candidate: Path, allow_add: set[str], allow_change: set[str],
             expected_base_sha: str | None = None) -> dict:
    if base.resolve() == candidate.resolve():
        raise ValueError("Baseline and candidate must be separate files")
    base_hash = sha256(base.read_bytes())
    candidate_hash = sha256(candidate.read_bytes())
    if expected_base_sha and base_hash.lower() != expected_base_sha.lower():
        raise ValueError("Baseline SHA-256 differs from explicitly pinned reference")
    left, old_mod = scan(base)
    right, new_mod = scan(candidate)
    original = set(left)
    future = set(right)
    removed = sorted(original - future)
    added = sorted(future - original)
    modified = sorted(name for name in original & future if left[name]["sha256"] != right[name]["sha256"])
    old_classes = {name for name in original if name.endswith(".class")}
    new_classes = {name for name in future if name.endswith(".class")}
    errors: list[str] = []

    for path in removed:
        errors.append(f"REMOVED: {path}")
    for path in added:
        if path not in allow_add:
            errors.append(f"UNREVIEWED_ADDITION: {path}")
        if any(bit in "/" + path.lower() for bit in DENY_TEST_PARTS):
            errors.append(f"TEST_CLASS_OR_RESOURCE_PACKAGED: {path}")
    for path in modified:
        if path not in allow_change:
            errors.append(f"UNREVIEWED_REPLACEMENT: {path}")
        if path.startswith(PROTECTED_PREFIXES):
            errors.append(f"BUNDLED_HELPER_OR_DEPENDENCY_CHANGED: {path}")

    # Existing 1.21.11 Fabric entrypoints, mixins and dependencies must never silently disappear.
    for field in ("id", "schemaVersion", "environment", "depends", "entrypoints", "mixins", "jars"):
        if old_mod.get(field) != new_mod.get(field):
            errors.append(f"FABRIC_METADATA_CHANGED: {field}")
    for path in PROTECTED_PREFIXES:
        if not all(name in future for name in original if name.startswith(path)):
            errors.append(f"BUNDLED_CONTENT_MISSING: {path}")
    if old_mod.get("version") == new_mod.get("version") and candidate_hash != base_hash:
        errors.append("VERSION_NOT_BUMPED: modified main JAR retains the old version")
    if not any(path.endswith(".class") for path in added + modified):
        errors.append("NO_EXECUTABLE_CLASS_CHANGE: a renamed/metadata-only old main is not a feature build")
    # In a source-only checkpoint, reusing an old version or faking a build would be misleading.
    if new_mod.get("environment") != "client":
        errors.append("INVALID_CLIENT_ENVIRONMENT")
    if new_mod.get("depends", {}).get("minecraft") != "1.21.11":
        errors.append("MINECRAFT_TARGET_CHANGED")

    report = {
        "schemaVersion": 1,
        "verdict": "PASS_STRUCTURAL_ONLY" if not errors else "FAIL",
        "baseline": {"name": base.name, "sha256": base_hash, "bytes": base.stat().st_size,
                     "entries": len(left), "classes": len(old_classes), "version": old_mod.get("version")},
        "candidate": {"name": candidate.name, "sha256": candidate_hash, "bytes": candidate.stat().st_size,
                      "entries": len(right), "classes": len(new_classes), "version": new_mod.get("version")},
        "changes": {"added": added, "removed": removed, "modified": modified},
        "reviewedAllowlist": {"added": sorted(allow_add), "changed": sorted(allow_change)},
        "errors": errors,
        "limitations": ["Does not run Minecraft, Fabric, ViaFabricPlus, or Forge 1.7.10",
                        "Does not establish semantic equivalence of modified JVM bytecode",
                        "Does not prove the new features work; requires compile, corpus and live validation"],
    }
    return report


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", required=True, type=Path)
    parser.add_argument("--candidate", type=Path)
    parser.add_argument("--inventory-only", action="store_true")
    parser.add_argument("--expect-base-sha256")
    parser.add_argument("--allow-add", action="append", default=[])
    parser.add_argument("--allow-change", action="append", default=[])
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    try:
        if args.inventory_only:
            if args.candidate:
                parser.error("--inventory-only cannot be combined with --candidate")
            report = inventory(args.base, args.expect_base_sha256)
        else:
            if not args.candidate:
                parser.error("--candidate is required unless --inventory-only is set")
            report = validate(args.base, args.candidate, set(args.allow_add),
                              set(args.allow_change), args.expect_base_sha256)
    except (OSError, BadZipFile, ValueError) as exc:
        print(f"BLOCKED: {exc}", file=sys.stderr)
        return 2
    out = json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(out, encoding="utf-8")
    if args.inventory_only:
        print(f"INVENTORY: SHA-256={report['sha256']}, classes={report['classEntries']}, "
              f"missing critical={len(report['missingRequiredClasses'])}")
        return 0 if report["requiredClassesPresent"] else 1
    print(f"{report['verdict']}: {len(report['changes']['added'])} additions, "
          f"{len(report['changes']['modified'])} replacements, "
          f"{len(report['changes']['removed'])} removals; errors={len(report['errors'])}")
    return 0 if not report["errors"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
