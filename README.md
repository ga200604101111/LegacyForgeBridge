# LegacyForgeBridge

LegacyForgeBridge is an experimental Fabric 1.21.11 compatibility and conversion layer for legacy Minecraft Forge 1.7.10 mods.

## Latest delivered local build: rev193

The latest locally delivered replacement is `legacyforgebridge-0.2.0-alpha.27-rev193.jar`. It integrates vanilla tray/pot display-stack handling into the main bridge; the old `lfb_visual_stack_hotfix` add-on is no longer needed and must be removed.

**Source storage note:** this branch currently retains rev189–193 in checksum-verified source checkpoints; the root `src/` tree is still the rev188 base. Do not build that old tree and label it rev193. Restore all revisions into a new directory first:

```sh
python checkpoints/rev193/restore.py --output ../LegacyForgeBridge-rev193
```

See [rev193 source, installation and validation boundaries](checkpoints/rev193/README.md). The local build was incremental, not a full clean Gradle/Loom or Minecraft integration run. Wind chimes and campfire GUI dispatch remain unresolved. No new Actions build was requested.

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
