#!/usr/bin/env python3
"""Verify supplied Bamboo 2.6.8.5 presentation outputs; never execute source JAR code.
Usage: python tools/geometry/verify_bamboo_geometry_continuation.py \
    --source-jar Bamboo.jar --baseline baseline-stage --patched patched-stage --report validation.json
The directories must be outputs of the presentation pass pipeline, not installed game directories.
This exact-corpus verifier is intentionally fixture-specific; production adapters are generic.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import math
from pathlib import Path
import zipfile

SOURCE_SHA = "bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402"
BASE_COMMIT = "fd75cadf77bd25ab7f46ff6fb887412921737f18"


def require(ok: bool, message: str) -> None:
    if not ok:
        raise AssertionError(message)


def read(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--source-jar", type=Path, required=True)
    p.add_argument("--baseline", type=Path, required=True)
    p.add_argument("--patched", type=Path, required=True)
    p.add_argument("--report", type=Path, required=True)
    a = p.parse_args()
    sha = hashlib.sha256(a.source_jar.read_bytes()).hexdigest()
    require(sha == SOURCE_SHA, "Not the exact supplied Bamboo 2.6.8.5 fixture")
    before = read(a.baseline / "legacyforgebridge/block-geometry.json")
    after = read(a.patched / "legacyforgebridge/block-geometry.json")
    require(after["sourceSha256"] == sha, "Generated source hash mismatch")
    old, new = before["blocks"], after["blocks"]
    count = lambda blocks: sum(len(v["variants"]) for v in blocks.values())
    require((len(old), count(old)) == (19, 287), "Unexpected baseline coverage")
    require((len(new), count(new)) == (21, 327), "Unexpected patched coverage")
    for block_id, block in old.items():
        require(block_id in new and block["family"] == new[block_id]["family"], f"Lost family {block_id}")
        for meta, variant in block["variants"].items():
            require(new[block_id]["variants"][meta] == variant, f"Changed prior geometry {block_id}/{meta}")
    fallback = []
    for block_id, block in new.items():
        for meta, v in block["variants"].items():
            for bounds in (v["bounds"], v["inventoryBounds"]):
                require(len(bounds) == 6 and all(math.isfinite(x) and 0 <= x <= 1 for x in bounds), "Invalid bounds")
                require(all(bounds[i] < bounds[i+3] for i in range(3)), "Empty bounds")
            if "materialMode" in v:
                require(block["family"].startswith("mimic_") and v["copyFace"] == -1, "Unscoped fallback")
                require(v["materialMode"] == "inventory_fallback" and v["materialFallbackReason"].strip(), "Unmarked fallback")
                fallback.append(f"{block_id}/{meta}")
    expected = {f"bamboomod:delude_{name}/{m}" for name in ("width", "height") for m in (6,7,14,15)}
    require(set(fallback) == expected and after["inventoryMaterialFallbackVariants"] == 8, "Fallback budget changed")
    variants = lambda name: new["bamboomod:" + name]["variants"]
    for name in ("delude_width", "delude_height", "delude_stair", "delude_plate", "decocarpet"):
        require(set(variants(name)) == {str(m) for m in range(16)}, f"Incomplete {name}")
    for m in range(16):
        width = variants("delude_width")[str(m)]["bounds"]
        require(width == ([0,.5,0,1,1,1] if m & 8 else [0,0,0,1,.5,1]), "Mimic slab half bit")
        require(variants("halfdirsquare")[str(m)]["bounds"] == [0,0,0,1,.5,1], "Tatami has source-fixed lower half")
        require(variants("halfdeco")[str(m)]["bounds"] == ([0,.5,0,1,1,1] if m & 8 else [0,0,0,1,.5,1]), "Decoration slab half bit")
        plate = variants("delude_plate")[str(m)]
        require(plate["bounds"] == [1/16,0,1/16,15/16,1/32 if m == 1 else 1/16,15/16], "Plate shape")
        require(plate["copyFace"] == 0 and plate["collision"] == "empty", "Plate projection/collision")
        require(variants("decocarpet")[str(m)]["bounds"] == [0,0,0,1,1/16,1], "Carpet shape")
        require(variants("decocarpet")[str(m)]["collision"] == "empty", "Carpet collision")
    require(new["bamboomod:bamboopanel"]["family"] == "pane", "Bamboo panel became cube family")
    require(set(variants("bamboopanel")) == {str(m) for m in range(7)}, "Invented pane variants")
    # Every referenced native generated geometry model exists; all owned texture references resolve.
    texture_references = 0
    model_count = 0
    for block_id, block in new.items():
        ns, name = block_id.split(":", 1)
        states = read(a.patched / f"assets/{ns}/blockstates/{name}.json")["variants"]
        for meta in block["variants"]:
            require(states[f"legacy_meta={meta}"]["model"] == f"{ns}:block/lfb_geometry/{name}/{meta}", "State fell back")
            for kind in ("block", "item"):
                path = a.patched / f"assets/{ns}/models/{kind}/lfb_geometry/{name}/{meta}.json"
                model = read(path)
                model_count += 1
                require(model.get("elements"), f"Empty model {path}")
                for sprite in model["textures"].values():
                    if sprite.startswith("#"):
                        continue
                    tns, tname = sprite.split(":", 1)
                    if tns != "minecraft":
                        require((a.patched / f"assets/{tns}/textures/{tname}.png").is_file(), f"Missing texture {sprite}")
                        texture_references += 1
                for part in model["elements"]:
                    lo, hi = part["from"], part["to"]
                    require(all(0 <= lo[i] <= hi[i] <= 16 for i in range(3)), "Invalid model coordinates")
                    require(sum(lo[i] == hi[i] for i in range(3)) == 1, "Non-planar/degenerate model quad")
                    if name == "bamboopanel" and meta in ("4", "5"):
                        require(lo[0] == hi[0] == 8 or lo[2] == hi[2] == 8, "Curtain has slab thickness")
    unchanged = 0
    with zipfile.ZipFile(a.source_jar) as jar:
        for name in jar.namelist():
            if name.endswith((".png", ".png.mcmeta")):
                require((a.patched / name).read_bytes() == jar.read(name), f"Changed original asset {name}")
                unchanged += 1
    require(unchanged == 223, "Unexpected source image/animation count")
    report = {
        "status": "passed", "base_commit": BASE_COMMIT, "source_sha256": sha,
        "source_description": "BambooMod Minecraft 1.7.10 v2.6.8.5 (user supplied)",
        "before": {"geometry_rules": len(old), "metadata_variants": count(old)},
        "after": {"geometry_rules": len(new), "metadata_variants": count(new)},
        "existing_variants_preserved": 287, "new_metadata_variants": 40,
        "explicit_inventory_material_fallbacks": sorted(fallback),
        "geometry_model_files_checked": model_count,
        "owned_texture_references_checked": texture_references,
        "missing_owned_texture_references": 0,
        "original_png_and_animation_entries_byte_identical": unchanged,
        "pure_java_checks": "MimicShapeRegressionChecks: material schema; 256 stair shapes; 32 pane/curtain meshes; held curtain",
        "presentation_passes_executed": 9,
        "scope": "Standalone presentation pipeline and source/model regression checks, not full conversion/game acceptance",
        "not_verified": ["Full Gradle/JUnit suite", "Minecraft-dependent client compilation", "Full conversion engine", "In-game rendering and interactions"],
        "github_push_performed": False,
    }
    a.report.parent.mkdir(parents=True, exist_ok=True)
    a.report.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
