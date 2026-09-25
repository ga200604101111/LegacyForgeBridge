# LegacyForgeBridge

LegacyForgeBridge is an experimental Fabric 1.21.11 compatibility and conversion layer for legacy Minecraft Forge 1.7.10 mods.

## Latest delivered local build: rev196

Main replacement: `legacyforgebridge-0.2.0-alpha.27-rev196.jar` (3,729,098 bytes).
SHA-256: `1ab2c04282987fd67b1ddea4d9e66ff3563e1fd227464e06e57bb35264152f9e`.

rev196 removes an extra world-local tray scale, restores source-proven white spa steam, adds VillagerBlock head/nose and client-motion presentation, and waits for a real server-returned written book before opening the cookbook. It retains the rev195 Property/world-clock fixes. Do not use rev194, which has a confirmed GridPot JVM VerifyError. No separate hotfix mod is required. Preserve dependencies, settings and `old-mods`; regenerate converted output and restart the client.

**Scope:** VillagerBlock gameplay is not complete; spa water still has its distinct source identity and full vanilla fluid-renderer parity is not certified. Cookbook inventory replacement requires the remote server. No invented recipe pages or dummy inventory are used.

**Source storage note:** this branch retains rev189-196 in source checkpoints; the root `src/` tree is still the rev188 base. Do not compile that old tree and label it rev196. Restore all revisions into a new directory first:

```sh
python checkpoints/rev196/restore.py --output ../LegacyForgeBridge-rev196
```

See [rev196 implementation, local verification and limitations](checkpoints/rev196/README.md). Local targeted compilation, 232 recording-fixture assertions, 17 source-mutation/contract assertions and an independent identical rebuild passed. The 17 related conversion passes completed with PARTIAL status; the entire engine could not run locally without the Mojang DFU dependency. This is NOT a full Gradle/Loom build, Minecraft/Fabric/Mixin launch or live server certification. Optional GridPot insertion conflicts remain unchanged. No Actions build was requested.

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
