# LegacyForgeBridge

LegacyForgeBridge is an experimental Fabric 1.21.11 compatibility and conversion layer for legacy Minecraft Forge 1.7.10 mods.

## Latest source checkpoint: rev199 (not integrated or installable)

The iYAMATO feature branch now adds a generic static-field fluent-texture evidence analyzer and standalone tests. The supplied corpus yields 101 literal texture-field records with existing PNGs; an independent direct-registerItem scan matches 99 registered fields. Two extra unregistered fields are not counted as supported items. 101 synthetic assertions passed using an isolated JDK-internal-ASM test copy, not a complete production ASM9/Gradle/Minecraft build.

**This is source-only groundwork. It is not wired into model generation or runtime admission, and no rev199 bridge or converted-mod JAR is delivered.** The rev198 unresolved-model/entity boundary below is unchanged. Main and the Bamboo branch remain untouched. See [rev199 scope, reproducible validation and next integration boundary](checkpoints/rev199/README.md) and [actual verification](checkpoints/rev199/verification.json). Restore the optional source utility with `python checkpoints/rev199/restore.py --output ../LegacyForgeBridge-rev199-source` from the complete checkout.

## Previous branch trial: iYAMATO experimental corpus (rev198)

The previous Bamboo/rev197 branch was merged into main through PR #17, merge commit `912cb905ce61e97ccdd6e54e2a5a26b3b6ae8c5f`. This new branch, `feature/generic-conversion-iyamato-corpus3`, starts from that merge and contains only experimental follow-up work. Main remains the rev197 baseline.

**iYAMATO is not yet playable through the converter.** The first diagnostic trial is PARTIAL: 99 item and 4 block registrations are recovered, but 103 model identities and all 54 entity candidates remain unresolved. No installable converted iYAMATO JAR is delivered. The local main-bridge experimental artifact fixes generic Block-name extraction and constructor-argument validation; it does not hardcode this mod's identity or pretend unsupported materials/entities work.

Experimental artifact: `legacyforgebridge-0.2.0-alpha.27-rev198-experimental.jar` (3,735,329 bytes), SHA-256 `0208f6d22e19875bbbff52d8d65fef2ebfb455866fff548ba72b64f4cf31cdb9`.

See [implementation and local validation](checkpoints/rev198/README.md), [Traditional Chinese trial report](checkpoints/rev198/Trial-Report.zh-TW.md), and [verification summary](checkpoints/rev198/verification.json). The full engine could not run without the real Mojang Codec dependency in this container; 104/105 diagnostic pass calls completed with one explicit omission, not 104 supported features. No complete Gradle/Loom build, Minecraft/Fabric/Mixin launch or live-server test was performed. No Actions build was requested.

## Previous delivered baseline: rev197

Main replacement: `legacyforgebridge-0.2.0-alpha.27-rev197.jar` (3,736,135 bytes).
SHA-256: `e3c28cccbe3fab0b6ac2d003ad26a7e3565a6bf38f22bd4c1b1544b109a885c5`.

rev197 fixes repeated reading after the cookbook becomes a real written book, replaces its exact return-method fingerprint with bounded symbolic recognition, and hardens shared source/FML identity, version, alias and numeric-map handling. Existing rev195 ABI and rev196 presentation changes remain. Do not use rev194, which has a confirmed GridPot VerifyError. No separate hotfix mod is required. Preserve dependencies, settings and old-mods; regenerate converted output and restart.

**Genericity is limited:** many renderer/menu/steam/head families still use constrained source templates. The dormant RPGTool1 profile is not selected by the default engine but still exists. Successful handshake is not universal mod compatibility. Read the [genericity and identity audit](checkpoints/rev197/Generality-Audit.zh-TW.md).

**Source storage note:** cumulative changes remain in source checkpoints; root src is the rev188 base. Do not compile that old tree and label it rev197 or rev198. Restore into a new directory outside this checkout:

```sh
# Baseline source:
python checkpoints/rev197/restore.py --output ../LegacyForgeBridge-rev197
# Experimental source:
python checkpoints/rev198/restore.py --output ../LegacyForgeBridge-rev198
```

See [rev197 changes, local verification and limits](checkpoints/rev197/README.md). Its prior local checks were not complete Minecraft/Fabric/Mixin or live-server verification. Optional GridPot insertion conflict and previously incomplete gameplay remain unresolved.

## Project goals

1. Let a modern Fabric 1.21.11 client interoperate with a clean Forge 1.7.10 server as the first networking milestone.
2. Scan legacy Forge 1.7.10 mod JARs from `minecraft/old-mods`.
3. Analyze and convert supported legacy bytecode/API usage into modern equivalents.
4. Write converted artifacts to `minecraft/mods` for loading on the next launch.
5. Cache source hashes so unchanged legacy mods are not converted again.
6. Prefer modern Fabric/vanilla APIs and Mixins over preserving obsolete implementation details such as direct OpenGL or old CoreMod transformers.

## Status

Experimental, incomplete compatibility. The project includes diagnostics, legacy-mod discovery, conversion caching, bytecode analysis and FML handshake/registry mapping. The original server continues to own gameplay state; client presentation and required protocol behavior must still be adapted correctly.

> This project does not claim universal Forge 1.7.10 mod compatibility. Unsupported operations must fail explicitly and produce diagnostics instead of silently emitting broken converted JARs.
