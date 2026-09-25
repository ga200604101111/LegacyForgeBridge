# LegacyForgeBridge

LegacyForgeBridge is an experimental Fabric 1.21.11 compatibility and conversion layer for legacy Minecraft Forge 1.7.10 mods.

## Latest delivered local build: rev195

**Do not use rev194: its GridPot renderer has a confirmed JVM VerifyError.** The replacement is `legacyforgebridge-0.2.0-alpha.27-rev195.jar`, SHA-256 `e5e7a60ae49ee1b934badd9e791c82ae13fd4d20c15877619a2caa39d9c2ec78`.

rev195 corrects the Property/BooleanProperty ABI error in the locally compiled GridPot renderer and an obsolete world-clock method in the suspended-model renderer. Only the two extraction bodies and BuildInfo change; 1,451 other archive entries are retained byte-for-byte. No companion hotfix mod is required. Preserve `old-mods`, dependencies and settings; finish conversion and restart when requested.

**Source storage note:** this branch retains rev189-195 in source checkpoints; the root `src/` tree is still the rev188 base. Do not build that old tree and label it rev195. Restore all revisions into a new directory first:

```sh
python checkpoints/rev195/restore.py --output ../LegacyForgeBridge-rev195
```

See [rev195 source, JVM regressions and validation boundaries](checkpoints/rev195/README.md). Both old-release defects are reproduced with corrected documented API fixtures; rev195 passes the corresponding negative/positive controls and 176 recording-fixture assertions. The local targeted rebuild is NOT a full Gradle/Loom build, Minecraft/Fabric/Mixin launch or live server certification. Optional GridPot insertion conflicts remain unchanged. No Actions build was requested.

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
