# LegacyForgeBridge

LegacyForgeBridge is an experimental Fabric 1.21.11 compatibility and conversion layer for legacy Minecraft Forge 1.7.10 mods.

## Latest delivered local build: rev194

The latest locally delivered replacement is `legacyforgebridge-0.2.0-alpha.27-rev194.jar`. It integrates tray item height/camera-facing presentation, world-model pot contents, source-proven wind-chime rendering, initial client block-entity hydration and source-proven remote campfire GUI dispatch into the main bridge. Remove the obsolete `lfb_visual_stack_hotfix` add-on. Preserve `old-mods`; complete conversion and restart before testing the new contracts.

**Source storage note:** this branch retains rev189–194 in checksum-verified source checkpoints; the root `src/` tree is still the rev188 base. Do not build that old tree and label it rev194. Restore all revisions into a new directory first:

```sh
python checkpoints/rev194/restore.py --output ../LegacyForgeBridge-rev194
```

See [rev194 source, installation and validation boundaries](checkpoints/rev194/README.md). Local incremental JDK 21 compilation, recording-double tests and selected source-conversion checks passed. This is not a full clean Gradle/Loom build, Minecraft/Fabric/Mixin launch or live complete inventory packet-pipeline certification. The selected Bamboo pipeline remains PARTIAL. Optional GridPot insertion-predicate conflicts are not changed. No new Actions build was requested.

## Project goals

1. Let a modern Fabric 1.21.11 client interoperate with a clean Forge 1.7.10 server as the first networking milestone.
2. Scan legacy Forge 1.7.10 mod JARs from `minecraft/old-mods`.
3. Analyze and convert supported legacy bytecode/API usage into modern equivalents.
4. Write converted artifacts to `minecraft/mods` for loading on the next launch.
5. Cache source hashes so unchanged legacy mods are not converted again.
6. Prefer modern Fabric/vanilla APIs and Mixins over preserving obsolete implementation details such as direct OpenGL or old CoreMod transformers.

## Status

Early development. The first milestone is a buildable Fabric 1.21.11 bridge foundation with diagnostics, legacy-mod discovery, conversion caching, bytecode analysis, and an FML handshake state-machine foundation.

> This project does not claim universal Forge 1.7.10 mod compatibility yet. Unsupported operations must fail explicitly and produce diagnostics instead of silently emitting broken converted JARs.
