# LegacyForgeBridge

Experimental Fabric 1.21.11 client compatibility/conversion layer for Forge 1.7.10 mods. The original server owns gameplay state. Keep original JARs in `old-mods`, not modern Fabric `mods`.

## Latest continuation: rev245 deep motion correlation diagnostics

A new complete installable diagnostic main was built locally from the exact user-supplied rev243 binary. **This remains observation-only; it does not claim the unwanted second upward lift is fixed.**

Artifact delivered in chat: `legacyforgebridge-0.2.0-alpha.27-rev245-corpus4-local.28-deepdiag.jar`, 4,527,570 bytes, SHA-256 `92d00685e9316946353bf410455e57f14a15fd4a903ce5eed749b4fafd658ae6`.

rev245 correlates existing raw 1.7.10 motion, Via output, modern packet dispatch/application and bounded post-apply tick/move/camera context. Exact legacy wire IDs and modern packet-object identities are distinguished from the Via-output-to-modern-packet `vector_time_candidate` gap. Repeated positive-Y applications can be marked `secondLiftCandidate=true`, always with `causalProof=false`. The rev244 `velocityChanged` label and capture-local diagnostic corrections are included; rev241/rev242 velocity-history heuristics remain hard-disabled.

Packaged validation passed 20 existing diagnostic scenarios / 643 assertions, 37 new correlation assertions, ZIP/JDK21 class checks and a real packaged-writer test with 8,006/8,006 records written, zero dropped/truncated/discarded records and TRACE_END present. Two independent offline builds were byte-identical. These results use explicit Minecraft/Fabric test doubles except for the actual packaged disk writer; no live Minecraft/ViaFabricPlus/original Forge 1.7.10 client or server call-site validation was performed.

See [rev245 checkpoint, build and capture instructions](checkpoints/rev245/README.md). Root `src` is still not the cumulative delivered state; continue from the checkpointed sources and exact pinned base rather than compiling root source and calling it rev245.

## Historical source checkpoint: rev206 projectile audit / rejection reporting

**No installable rev206 JAR and no new admitted projectile entities.** This checkpoint audits the latest user log and original source corpus, and fixes silent loss of upstream projectile-candidate rejections. It is not a complete generic projectile converter.

The delivered rev205 analyzer rejects 54 source entity registrations upstream, then previously reports zero projectile rules and zero exclusions. The reporting patch carries 24 hierarchy/interface-classified candidate failures into the existing production pass: zero admitted rules, 24 exclusions and warnings, and one upstream summary. The candidates include invisible attack carriers and non-weapon throwable content. Nineteen have source-owned tick methods; five bind vanilla RenderSnowball. Numeric IDs from logs and config defaults are not substituted for source identity proof.

The original bullet/shell sprite binding uses an actually all-transparent PNG. Missing sprites must not all be replaced by visible arrows. Mesh/UV, source client movement, typed spawn/metadata, lifecycle and source config-dependent identity remain separate implementation boundaries.

See [rev206 scope and reproducible tests](checkpoints/rev206/README.md), [Traditional Chinese audit](checkpoints/rev206/AUDIT.zh-TW.md), and [findings](checkpoints/rev206/audit.json). This turn passed 17 synthetic fixtures / 86 analyzer assertions and 9 actual production-pass assertions, with independent JSON readback. Local dependency provenance is recorded. No full Gradle/Loom, Minecraft/Mixin, original Bamboo or live-server validation was performed.

## Historical local main: rev205

The previously delivered complete main is `legacyforgebridge-0.2.0-alpha.27-rev205-local-test.1.jar`, 3,990,189 bytes, SHA-256 `3ce19e3a6d9b4788251aae95edd0c179c93bc44f4d5f65fba054be67ce4461de`. It preserves rev202-204 source motion observation, item properties/equipment and interaction work; rev205 corrects inherited bow sprite presentation and adds source-event-gated hold-use analysis. These local incremental builds are not clean Gradle/Loom builds. Prior validation counts are historical and were not rerun for this audit.

The branch now retains the previously conversation-only rev202-205 source kits. Their 819-file payload and per-revision source manifests were verified and unpacked locally. This is source preservation, not a fresh build or complete cumulative source restoration. See [rev205 details](checkpoints/rev205/README.md) and [historical verification](checkpoints/rev205/verification.json).

```sh
python checkpoints/rev205/unpack.py --output ../LegacyForgeBridge-rev202-205-kits
```

Use each extracted kit with its pinned base main. **Root `src` is still the old base plus cumulative checkpoints. Do not compile it alone and label it rev205 or rev206.** Source checkpoints contain no proprietary mod/game binaries or dependency JARs.

## Current boundaries and policy

Jump remains OBSERVE_ONLY and is not confirmed fixed. The latest full mod set vetoes some hold-use paths due to unproven Bamboo ArrowNockEvent listeners; do not silently bypass event cancellation. Projectile gameplay/presentation, custom renderers and universal GUI conversion are incomplete. Successful handshake or static analysis is not full compatibility.

All work stays on `feature/generic-conversion-iyamato-corpus3`, with `[skip ci] [skip actions]`. No Actions dispatch, new PR/tag/release, workflow change, server change, or main/Bamboo branch push. See [development policy](AGENTS.md). The [previous rev201 README](docs/README-before-rev205-f3f4cf05.md) and all older checkpoints remain preserved.
