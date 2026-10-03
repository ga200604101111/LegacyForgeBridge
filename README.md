# LegacyForgeBridge

Experimental Fabric 1.21.11 client compatibility/conversion layer for Forge 1.7.10 mods. The original server owns gameplay state. Keep original JARs in `old-mods`, not modern Fabric `mods`.

## Latest continuation: rev246 desktop status/support UI

A new complete main was built from the exact rev245 deep-diagnostic artifact. The conversion-status desktop window now presents **詳細資訊** as a real button, adds a **目前支援模組列表** child window, and dynamically titles the main window as `LegacyForgeBridge | [目前的狀態] | by YingHunag09`.

The support dialog currently documents RPGTool1 1.1 as live-validated and BambooMod 2.6.8.5 / iYAMATO's Mod 1.7.10-1.6.8 as major compatibility targets under continued verification. It explicitly states that this is not an allow-list: LegacyForgeBridge remains a generic source-driven Forge 1.7.10 converter, so other mods with similar API/bytecode structures may also convert successfully but still require actual validation.

Artifact delivered in chat: `legacyforgebridge-0.2.0-alpha.27-rev246-corpus4-local.29-deepdiag-ui.jar`, 4,534,358 bytes, SHA-256 `6fc64f88351db277ef438b8eb226d313baa5410bf5a8e1a007c65b85bed414c4`.

The same new UI class is installed in both the outer main JAR and its embedded desktop-helper JAR. Swing/Xvfb validation passed 19 assertions against source, 19 against the packaged main and 19 against the packaged helper; an independent rebuild was byte-identical. rev245 motion-correlation core classes remain content-identical and the motion diagnostic schema remains `rev245-deep.1`.

See [rev246 checkpoint](checkpoints/rev246/README.md). Root `src` is still not the cumulative delivered state; use the checkpointed source/build chain and exact pinned base.

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
