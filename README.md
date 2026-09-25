# LegacyForgeBridge

LegacyForgeBridge is an experimental Fabric 1.21.11 compatibility and conversion layer for legacy Minecraft Forge 1.7.10 mods.

## Latest delivered local build: rev197

Main replacement: `legacyforgebridge-0.2.0-alpha.27-rev197.jar` (3,736,135 bytes).
SHA-256: `e3c28cccbe3fab0b6ac2d003ad26a7e3565a6bf38f22bd4c1b1544b109a885c5`.

rev197 fixes repeated reading after the cookbook becomes a real written book, replaces its exact return-method fingerprint with bounded symbolic recognition, and hardens shared source/FML identity, version, alias and numeric-map handling. Existing rev195 ABI and rev196 presentation changes remain. Do not use rev194, which has a confirmed GridPot VerifyError. No separate hotfix mod is required. Preserve dependencies, settings and old-mods; regenerate converted output and restart.

**Genericity is limited:** many renderer/menu/steam/head families still use constrained source templates. The dormant RPGTool1 profile is not selected by the default engine but still exists. Successful handshake is not universal mod compatibility. Read the [genericity and identity audit](checkpoints/rev197/Generality-Audit.zh-TW.md).

**Source storage note:** rev189-197 remain in cumulative source checkpoints; root src is the rev188 base. Do not compile that old tree and label it rev197. Restore into a new directory outside this checkout:

```sh
python checkpoints/rev197/restore.py --output ../LegacyForgeBridge-rev197
```

See [rev197 changes, local verification and limits](checkpoints/rev197/README.md). Targeted local compilation, old-release negative controls, 391 checks plus 17 source-mutation/contract assertions and an identical fresh rebuild passed. The 17 selected conversion passes remain PARTIAL. No complete Gradle/Loom build, actual Minecraft/Fabric/Mixin launch or live server test was performed. Optional GridPot insertion conflict and previously incomplete gameplay remain unresolved. No Actions build was requested.

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
