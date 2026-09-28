#!/usr/bin/env python3
"""Restore a verified rev211 SOURCE-ONLY kit. No network, main build, game or Actions."""
import argparse,base64,hashlib,json,lzma,sys
from pathlib import Path,PurePosixPath
PIN='84f96bb16b8421b074bdb048f669d1abcf7285754868ed22c495a98818be4475'
PARTS=['source-payload.json.xz.b64.part00', 'source-payload.json.xz.b64.part01', 'source-payload.json.xz.b64.part02', 'source-payload.json.xz.b64.part03']
COUNT=46
COMPRESSED=28336
DECODED=153375
def sha(b):return hashlib.sha256(b).hexdigest()
def safe(name):
    if not isinstance(name,str):raise ValueError("Path is not text")
    p=PurePosixPath(name)
    if not name or name=="." or p.is_absolute() or ".." in p.parts or str(p)!=name or any(c in name for c in ("\\",":","\0")):raise ValueError("Unsafe path: "+repr(name))
    return p
def restore(output):
    here=Path(__file__).resolve().parent;out=Path(output).absolute()
    if out.exists() or out.is_symlink() or any(p.is_symlink() for p in out.parents):raise ValueError("Output must be a new non-symlink directory")
    manifest=json.loads((here/"payload-manifest.json").read_text(encoding="utf-8"))
    if manifest["sha256"]!=PIN or manifest["parts"]!=PARTS or manifest["files"]!=COUNT or manifest["compressed_bytes"]!=COMPRESSED or manifest["decoded_bytes"]!=DECODED or manifest["installable"] is not False or manifest["native_tick_enabled"] is not False:raise ValueError("Manifest mismatch")
    raw=base64.b64decode("".join((here/p).read_text(encoding="ascii").strip() for p in PARTS),validate=True)
    if len(raw)!=COMPRESSED or sha(raw)!=PIN:raise ValueError("Payload checksum mismatch")
    decoder=lzma.LZMADecompressor(memlimit=128*1024*1024);decoded=decoder.decompress(raw,max_length=DECODED+1)
    if len(decoded)!=DECODED or not decoder.eof or decoder.unused_data:raise ValueError("Decompression boundary")
    payload=json.loads(decoded);checked={}
    if payload["schema"]!=1 or len(payload["files"])!=COUNT:raise ValueError("Payload schema/count")
    for entry in payload["files"]:
        name=entry["path"];safe(name);b=entry["content"].encode("utf-8")
        if name in checked or sha(b)!=entry["sha256"]:raise ValueError("Duplicate path or content checksum mismatch")
        checked[name]=b
    for name in checked:
        if any(str(p) in checked for p in PurePosixPath(name).parents):raise ValueError("File/directory conflict")
    for name,expected in json.loads(checked["source-sha256.json"]).items():
        if name not in checked or sha(checked[name])!=expected:raise ValueError("Build input checksum mismatch")
    for name,expected in json.loads(checked["baseline-sha256.json"]).items():
        if name not in checked or sha(checked[name])!=expected:raise ValueError("Baseline input checksum mismatch")
    out.mkdir(parents=True,exist_ok=False)
    for name,b in checked.items():
        p=out/name;p.parent.mkdir(parents=True,exist_ok=True)
        with p.open("xb") as f:f.write(b)
    print("Restored",COUNT,"verified source/test/report files. SOURCE ONLY; no native tick, main JAR or game validation.")
    return out
if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("--output",required=True,type=Path);args=parser.parse_args()
    try:restore(args.output)
    except (OSError,ValueError,KeyError,TypeError,lzma.LZMAError) as error:parser.exit(1,str(error)+"\n")
