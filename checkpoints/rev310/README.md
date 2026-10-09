# LegacyForgeBridge rev310 — reuse generic atlas converter for source TESR critters and flat inventory icons

## Delivered complete main

- Base: `legacyforgebridge-0.2.0-alpha.27-rev309.1-critter-identifier-fix.jar` SHA-256 `40aace814710d9d5d841c8c58d755f3619567c87c90df35ce1af92cde82c01cf`.
- Complete rev310 main (conversation download): `legacyforgebridge-0.2.0-alpha.27-rev310-critter-atlas-inventory.jar`, 5,230,335 bytes, SHA-256 `606d46c942af40a5a49ef00244d233e2abdf946c195a4bd18527ce65b984eb08`.
- Source code, ASM transform, packaging script and offline fixture: [source_bundle.zip](source_bundle.zip), 9,852 bytes, SHA-256 `c33c71e5312b265b742afbd581899a1b1062cdaefe21e1e2736c1631a70461b0`. Extract its `src/` and `tools/` into this checkpoint folder; temporary API stubs, Forge source mod binaries and compiled JARs are NOT bundled.
- `BuildInfo.VERSION` = `0.2.0-alpha.27-corpus4-local.64-rev310-critter-pre-atlas-flat-item.1`; `BuildInfo.CONVERTER_REVISION` = `2026-10-09.310-pre-atlas-critter-source-flat-icon`. Cache identity advances to regenerate source-owned output; the existing converter-cache compatibility constant is unchanged.

## Root cause, evidence and correction

rev309/rev309.1 generated the two TESR insect block model families at `LegacyJarWriter.writeDeterministic`, **after** `LegacyTextureAtlasPass.apply` completed. That bypassed the existing universal legacy sprite resource migration and modern atlas registration; lowercase-valid resource identifiers were not sufficient.

rev310 removes the late bake hook from `LegacyJarWriter` and calls `Rev310CritterPreAtlas.apply(context)` at the start of the existing **generic** `LegacyTextureAtlasPass.apply`, after normal mod content/resource passes but before sprite indexing. `CopyLegacyJarPass` has already copied source PNGs into staging. The generic pass then:
- Finds `twilightforest:model/cicada-model` and `twilightforest:model/firefly-tiny` from source 64x32 textures, copies them to candidate-owned, lowercase hashed `textures/block/lfb_legacy/` paths, rewrites all fourteen model templates to those IDs, and registers only the block-atlas sprites.
- For each source-proven 2D critter BlockItem, provides `models/item/<id>.json` (`minecraft:item/generated`) referencing the original **16x16 block icon** `textures/blocks/TFCicada.png` or `TFFirefly.png`. Generic atlas migration copies each sprite separately into `textures/item/lfb_legacy/` and registers the **items** atlas. This respects 1.21.11's block/item atlas split.
- Retains 16 legacy metadata blockstate variants for each critter with source attachment orientation. It does not execute 1.7 TESR or block logic or change the Forge server.

The original `twilightforest-1.7.10-2.3.8-tw.jar` has SHA-256 `1aa2c191170ff707499c42cd58bdcc69f2772b59014cb684ba99a6d1e6540589`; those exact source-class identities and hash gate admission. In the original renderer `RenderBlockTFCritters.shouldRender3DInInventory(int)` returns constant false, so the 2D inventory presentation is source-proven. Earlier generic `LegacyBlockInventoryRenderModeAnalyzer` and `GenericContentPass` already support certain source-proven flat items, but this original icon lives in `textures/blocks` and its custom proxy render-ID path can evade default family detection. The new bridge reuses the common atlas rather than duplicating its texture uploader.

## Build, validation and remaining boundaries

- Java 21 `javac --release 21` compiles only `Rev310CritterPreAtlas` and `BuildInfo` against the exact base JAR and **temporary Gson signature-compatible test stubs**, which are NOT packaged. Existing compiled code is patched using JDK-internal ASM during packaging only; no ASM is shaded.
- `tools/TransformRev310.java` changes only the generic atlas pass call point and removes the outdated post-atlas hook; `tools/package_rev310.py` verifies expected exact base hash and overlays the main.
- Main JAR ZIP CRC: PASS; no duplicate entries/stubs/embedded ASM; 2,006 pre-existing non-target ZIP payloads byte-identical; four payloads replaced (`LegacyJarWriter`, `LegacyTextureAtlasPass`, `BuildInfo`, `fabric.mod.json`), two new Java class payloads.
- Basic bytecode dataflow verified across **30 methods / four modified or new classes**.
- Independent exact original PNG/atlas **simulation**, using PNG bytes from the supplied source JAR and the built model templates, verifies 14 block models, two distinct flat item models, 32 metadata variants and zero missing generated sprite paths; no item uses a block-atlas sprite.
- **No live Minecraft 1.21.11, Fabric, ViaFabricPlus 4.4.15, model bake/render/particle, old Forge server, or actual full mod-candidate conversion pipeline has been executed.** This is an experimental complete JAR candidate; a static simulation is not a game/runtime pass.
- Existing rev309 particles/tooltip placement and rev308 bow/sword/enchants and rev307 Cloth/Mod Menu configuration are byte-identical to the old main where unmodified.

## Acceptance test

Replace rev309.1 with rev310 in the 1.21.11 client `mods/` (do not install two LFG mains), preserve original 1.7.10 JARs in `old-mods/`. Let cache rebuild and restart if required. Verify the converted Twilight Forest candidate contains the 14 `_lfb_critter_` JSON models with **rewritten block sprite hashes**, plus two flat item models with **item sprite hashes**, and its `assets/minecraft/atlases/blocks.json` and `items.json` contain the appropriate registrations. Then verify placed insects and inventory in game. For any remaining magenta blocks, supply the **generated Twilight Forest `-lfb.jar`** and relevant `latest.log` so we can inspect the actual runtime assets.

Work only in `feature/generic-conversion-iyamato-corpus3`; no CI dispatch, PR, release, force push, original-mod or server changes.
