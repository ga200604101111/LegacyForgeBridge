# rev309.1 — fix invalid critter model resource identifiers

This is a **narrow offline correction**, not proof that Forge 1.7.10 -> Fabric 1.21.11 conversion succeeds in game.

## What was broken

The rev309 source checkpoint generates 14 baked `cicada_*.json` and `firefly_*.json` models with `textures.particle` values `twilightforest:blocks/TFCicada` or `twilightforest:blocks/TFFirefly`. Those identifiers are invalid on modern Minecraft because resource paths must be lowercase. The templates may fail to resolve as block models when the converted wrapper loads.

## Correction

- Replace those 14 `textures.particle` fields with the existing lowercase `textures.model` values (`twilightforest:model/cicada-model`, `twilightforest:model/firefly-tiny`). Both reference texture PNGs found in the original source JAR.
- Preserve model `elements`, attachment orientation, source models, particles, tooltip hooks, sword, arrow and config systems unchanged.
- Change `BuildInfo.VERSION` and `fabric.mod.json` together to `0.2.0-alpha.27-corpus4-local.63-rev309.1-critter-identifier-fix.1`.
- Change `BuildInfo.CONVERTER_REVISION` to `2026-10-09.309.1-critter-texture-identifier-fix`, because conversion-cache fingerprint uses the semantic revision; the old rev309 candidate must be regenerated.
- The checkpoint `tools/fix_rev3091_templates.py` performs the same 14-template and metadata correction. The replacement BuildInfo source is under `src/`.

## Binary boundaries

- Input full main: `legacyforgebridge-0.2.0-alpha.27-rev309-critter-model-fx-tooltips.jar` SHA-256 `6d78a7fbdd9efd64b49fc1200b218ad3cb5cb89c3af5f7dd6e6dcc97bcb29f81`.
- Corrected complete main (delivered in conversation): `legacyforgebridge-0.2.0-alpha.27-rev309.1-critter-identifier-fix.jar` SHA-256 `40aace814710d9d5d841c8c58d755f3619567c87c90df35ce1af92cde82c01cf`, 5,224,638 bytes.
- Changed 16 ZIP payloads (14 JSON models, BuildInfo.class, fabric.mod.json), untouched 1,994; ZIP CRC intact, 28 texture identifiers across 14 models valid.
- The four rev309 helper classes and the existing conversion code are byte-identical to rev309. This is **not** a new Minecraft runtime test and should not be presented as full gameplay/model compatibility.
- Source is cumulative: extract the `checkpoints/rev309/source_bundle.zip` from rev309, then apply this checkpoint's template correction and replacement BuildInfo. The untouched original Forge JARs and server must not be modified.

## Open limitations

The model bake is attached to `LegacyJarWriter.writeDeterministic`, but it is guarded on the exact original Twilight Forest source SHA and also requires the generated `converted-content.json` to contain **both** `twilightforest/block/BlockTFCicada` and `twilightforest/block/BlockTFFirefly` as unique source-class entries. If either is not recovered, the code silently skips both models. Actual candidate output and runtime render have **not** been observed. Likewise, the GUI, particles and tooltip placement are not live-tested.

The first true acceptance check is the **converted Twilight Forest candidate**, checking the 14 emitted `assets/twilightforest/models/block/*_lfb_critter_*.json` plus the corresponding `blockstates/*.json`. Then the game must be launched with Fabric 1.21.11 to confirm rendering, particles and tooltip ordering. Retain the previous known-starting rev308/rev309 main for rollback.

Use only `feature/generic-conversion-iyamato-corpus3`; no Actions, release, PR, tag or modifications to other branches.
