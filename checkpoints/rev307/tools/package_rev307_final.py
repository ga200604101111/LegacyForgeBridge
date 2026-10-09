#!/usr/bin/env python3
"""Repack final rev307 .2 from the earlier corrected full rev307 JAR.

Inputs are user-supplied, unmodified original Forge mods and the previously
built complete corrected rev307 intermediate. This final packaging step changes
only compiled BuildInfo.class to align it with fabric.mod.json (.60/.2).
No live Minecraft test or real Fabric compilation is implied.
Usage:
  python package_rev307_final.py /path/to/rev307-corrected.jar /path/to/rev306.jar /path/to/output.jar
"""
import hashlib
import json
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

REV306_SHA256 = "3144da4956c06765b9798de699bdcb8f62f89ed07d9f219cdc452ff3ddf37df8"
FINAL_SHA256 = "42c12ff87acd5261935d7d079513b777b2fd8ded4907c73e315e27fa374a909f"
VERSION = "0.2.0-alpha.27-corpus4-local.60-rev307-legacy-config-menu.2"
CLASS_NAME = "dev/yinghuang/legacyforgebridge/BuildInfo.class"
ROOT = Path(__file__).resolve().parents[1]
BUILD_INFO = ROOT / "src/main/java/dev/yinghuang/legacyforgebridge/BuildInfo.java"

def sha256(path):
    with path.open("rb") as input_file:
        return hashlib.file_digest(input_file, "sha256").hexdigest()

def package(intermediate, baseline, target):
    if sha256(baseline) != REV306_SHA256:
        raise RuntimeError("Wrong rev306 original; refusing to package")
    with tempfile.TemporaryDirectory(prefix="lfb-rev307-") as td:
        folder = Path(td)
        subprocess.run(["javac", "--release", "21", "-encoding", "UTF-8",
                        "-d", str(folder), str(BUILD_INFO)], check=True)
        compiled = (folder / CLASS_NAME).read_bytes()
        with zipfile.ZipFile(intermediate) as original, zipfile.ZipFile(target, "w") as packaged:
            entries = original.namelist()
            if len(entries) != len(set(entries)) or CLASS_NAME not in entries:
                raise RuntimeError("Invalid intermediate ZIP")
            for info in original.infolist():
                packaged.writestr(info, compiled if info.filename == CLASS_NAME else original.read(info.filename))
    with zipfile.ZipFile(baseline) as base, zipfile.ZipFile(target) as result:
        if result.testzip() is not None or len(result.namelist()) != len(set(result.namelist())):
            raise RuntimeError("ZIP corruption or duplicate entries")
        except_set = {CLASS_NAME, "dev/yinghuang/legacyforgebridge/config/LegacyForgeBridgeModMenu.class", "fabric.mod.json"}
        for name in base.namelist():
            if name not in except_set and result.read(name) != base.read(name):
                raise RuntimeError("Original gameplay class was changed: " + name)
        if json.loads(result.read("fabric.mod.json"))["version"] != VERSION:
            raise RuntimeError("Mismatch: fabric.mod.json version")
        if VERSION.encode("utf-8") not in result.read(CLASS_NAME):
            raise RuntimeError("Mismatch: BuildInfo version")
        profiles = [n for n in result.namelist() if n.startswith("legacyforgebridge/legacy-config-profiles/") and n.endswith(".json")]
        world_fields = [x for n in profiles for x in json.loads(result.read(n))["properties"] if x.get("scope") == "world-save"]
        if len(profiles) != 3 or len(world_fields) != 1 or world_fields[0]["key"] != "dimensionId":
            raise RuntimeError("Missing profile or world-save boundary")
    print("Created:", target)
    print("SHA-256:", sha256(target))
    if sha256(target) != FINAL_SHA256:
        print("Note: ZIP metadata may make independently packaged bytes differ; inspect packaging inputs.")

if __name__ == "__main__":
    if len(sys.argv) != 4:
        raise SystemExit(__doc__)
    package(Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3]))
