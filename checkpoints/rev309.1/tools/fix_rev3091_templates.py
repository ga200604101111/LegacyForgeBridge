#!/usr/bin/env python3
"""Repack corrected rev309.1 from the exact rev309 binary; Java21 BuildInfo.class required.

This edits only 14 template particle references, BuildInfo.class and fabric.mod.json.
Usage: python fix_rev3091_templates.py /path/to/rev309-main.jar /path/to/new-BuildInfo.class /path/to/output.jar
"""
import hashlib,json,re,sys,zipfile
from pathlib import Path
BASE_SHA="6d78a7fbdd9efd64b49fc1200b218ad3cb5cb89c3af5f7dd6e6dcc97bcb29f81"
VERSION="0.2.0-alpha.27-corpus4-local.63-rev309.1-critter-identifier-fix.1"
REVISION="2026-10-09.309.1-critter-texture-identifier-fix"
VALID=re.compile(r"^[a-z0-9_.-]+:[a-z0-9_./-]+$")
BUILD="dev/yinghuang/legacyforgebridge/BuildInfo.class"
PREFIX="legacyforgebridge/rev309/templates/"
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def repack(source,compiled,destination):
    if sha(source)!=BASE_SHA:raise ValueError("wrong rev309 baseline")
    binary=compiled.read_bytes()
    if VERSION.encode() not in binary or REVISION.encode() not in binary:raise ValueError("wrong BuildInfo")
    with zipfile.ZipFile(source) as original:
        entries=original.namelist()
        if len(entries)!=len(set(entries)):raise ValueError("duplicate original ZIP entries")
        models=sorted(name for name in entries if name.startswith(PREFIX) and name.endswith(".json"))
        if len(models)!=14:raise ValueError("expected 14 critter models")
        replacements={BUILD:binary}
        for name in models:
            data=json.loads(original.read(name))
            old=data["textures"]["particle"]
            valid=data["textures"]["model"]
            if "TF" not in old or not VALID.fullmatch(valid):raise ValueError("unexpected model "+name)
            data["textures"]["particle"]=valid
            replacements[name]=(json.dumps(data,ensure_ascii=False,indent=2)+"\n").encode()
        meta=json.loads(original.read("fabric.mod.json"))
        if "rev309-critter-model-fx-tooltips.1" not in meta["version"]:raise ValueError("wrong mod metadata")
        meta["version"]=VERSION
        meta["description"]="rev309.1: correct uppercase-invalid model particle texture identifiers; force source conversion refresh; experimental client runtime not live tested."
        replacements["fabric.mod.json"]=(json.dumps(meta,ensure_ascii=False,indent=2)+"\n").encode()
        with zipfile.ZipFile(destination,"w") as target:
            for entry in original.infolist():
                target.writestr(entry,replacements.get(entry.filename,original.read(entry.filename)))
    with zipfile.ZipFile(destination) as result:
        assert result.testzip() is None
        assert result.namelist()==entries
        assert all(VALID.fullmatch(v) for name in models for v in json.loads(result.read(name))["textures"].values())
    print("valid_model_templates",len(models))
    print("main_sha256",sha(destination))
if __name__=="__main__":
    if len(sys.argv)!=4:raise SystemExit(__doc__)
    repack(Path(sys.argv[1]),Path(sys.argv[2]),Path(sys.argv[3]))
