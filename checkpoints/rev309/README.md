# LegacyForgeBridge rev309 — Cicada / firefly model, particles, top-of-tooltip order

## Complete main candidate and source

- Base full main: `legacyforgebridge-0.2.0-alpha.27-rev308-twilight-sword-arrow-equipment.jar`, SHA-256 `40fd1ae599c1a0ca07346ebd4336df3584f9385dc284e14e9d59d55ab7722dbd`.
- Rev309 full main delivery: `legacyforgebridge-0.2.0-alpha.27-rev309-critter-model-fx-tooltips.jar`, 5,224,850 bytes, SHA-256 `6d78a7fbdd9efd64b49fc1200b218ad3cb5cb89c3af5f7dd6e6dcc97bcb29f81`.
- **Full rev309 source and template checkpoint:** [source_bundle.zip](source_bundle.zip), 26,747 bytes, SHA-256 `f218efb75351e48611fcd0af4cc616eea9ca8bb6bcad43e172c5beee70c75f1b`. Contains 24 entries: five Java source files, 14 cuboid JSON models, four build scripts, a README and verification JSON. Extract ZIP at repository root to obtain `checkpoints/rev309/src/`, `resources/`, and `tools/`. The binary ZIP is a **source bundle**, not an installable Minecraft JAR.
- Original source asset: `twilightforest-1.7.10-2.3.8-tw.jar`, SHA-256 `1aa2c191170ff707499c42cd58bdcc69f2772b59014cb684ba99a6d1e6540589`. User-supplied original mod is not committed or altered.
- Branch: `feature/generic-conversion-iyamato-corpus3` only. No GitHub Actions, PR or release; main and Bamboo branches unchanged.

## Changes

1. **Model**: source-proven `ModelTFCicada` six cuboids and `ModelTFFirefly` body/legs/glow reconstructed as 14 attachment-specific cuboid JSON templates. The packaging hook at `LegacyJarWriter.writeDeterministic` resolves converted block IDs by source class and exact source SHA, then generates source-oriented blockstate entries `legacy_meta=0..15`; no hardcoded numeric block dispatch.
2. **Presentation**: converted cicada/firefly blocks are non-colliding, use face-oriented smaller selection bounds, CUTOUT render layer for transparent sprite pixels, and firefly level-15 luminance. This is client-side visualization; original server/block logic remains unchanged.
3. **Particles**: source cicada sound-note presentation uses vanilla NOTE particles; firefly emits modern ambient FIREFLY particles, aligned with the attached face. Model glow itself is static; the ambient particle provides motion.
4. **Tooltip**: generic LFB tooltip callback preserves title and preexisting tooltip components, then moves the lines appended by LFB to immediately below the title before other attributes and enchantment descriptions. Not a one-item allowlist.
5. **Compatibility**: rev308 sword, seeker arrow and enchant bridges plus rev307 Cloth/Mod Menu are kept. `BuildInfo.CONVERTER_REVISION` advances to `2026-10-09.309-critter-model-uv-and-attachment-proofs` so conversion cache invalidates and corrected model resources are regenerated; a second Minecraft launch may be necessary once new converted candidates are staged.

## How this was built / boundaries

- The bundled `tools/generate_critter_models.py` generated all fourteen baked models from the original TESR cuboid/UV source; `tools/TransformRev309.java` inserts 8 guarded hook categories into 4 old compiled classes. `tools/package_rev309.py` overlays six baseline entries (including `BuildInfo.class` and `fabric.mod.json`) and adds 18 new classes/resources on the exact rev308 full JAR.
- New intermediary Java helper classes compiled with Java 21 and temporary signature-compatible Fabric/Minecraft/Gson stubs; the stubs and ASM are **not shipped**. This is an offline exact-JAR overlay build, not a real Loom/Gradle compilation against actual game libraries.
- Packaged ZIP CRC valid, no duplicates and no shaded ASM; **1,986 non-target rev308 ZIP entry payloads byte-identical**. This is a packaging inspection, not real game runtime verification.
- No live Minecraft 1.21.11, ViaFabricPlus, Forge 1.7.10 server, in-world rendering, particle playback, block state, or tooltip UI test performed. Runtime behaviors remain experimental until the user confirms in game.

**Install:** replace the previous single LFB main with rev309; keep the original Forge JARs in `old-mods/`; regenerate converted candidates via converter cache revision and restart after it stages the updated wrappers. Do not install rev308 and rev309 simultaneously.
