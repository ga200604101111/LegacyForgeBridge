# rev256 — sapling evidence survives client-only source stripping

Reviewable production-source mirror of a binary overlay on the exact rev255 candidate. This directory is NOT added to the main Gradle source set. The full compile-only declaration/test kit is delivered in the conversation as `LFB-rev256-source-test-kit-20261006.zip`.

Artifact: `legacyforgebridge-0.2.0-alpha.27-rev256-sapling-proof-fix.jar`
SHA-256: `b08c3dbc12f5f436e48e8e4f2337aeacdc8d3e6ee78ebdc4e6a2a906152c96f9`
Base SHA-256: `11928e03f5e7c5801ebd311a49f887931ffb0a2bc4195f228f06c2f1bcfbbd28`

## Defect and correction

rev254/255 discovered BlockSapling ancestry by reading source `.class` files from loaded converted containers. The actual converter's `LegacyClientOnlySourceStripPass` removes those classes before the client artifact is published. A test against the unconverted original therefore did not establish working runtime admission. The old shipped analyzer returns a valid Bamboo candidate on the original class and `NONE` against a source-stripped fixture.

`SaplingProofCompiler.capture` now runs immediately before source stripping. It reads the untouched input JAR (no source class loading), emits `legacyforgebridge/sapling-client-proof.properties`, and optionally materializes cross/flat-item models when the existing icon interpreter provides complete, uniform, untinted 16-metadata evidence. Complex/unresolved icon/render paths remain unchanged. This is presentation/shape adaptation, NOT a sapling growth or arbitrary renderer port.

`SaplingRegistry` reads persisted data validated against manifest source hash and exact ID/source identity, not removed class files. Collision, selected bounds, and cutout gates are independent. A source collision override no longer receives an unconditional noCollision property.

For the user's already-converted Bamboo 2.6.8.5, a bundled data record generated from the exact raw input SHA `bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402` supplies the missing proof without reconversion. An automatically activated, hash-gated built-in resource pack replaces the prior low-priority ordinary-mod blockstate override and covers world and inventory metadata models. It uses the original sakura.png bytes and separate item/block sprite IDs.

Other old stripped candidates without matching proof require reconversion; do not claim universal retroactive recovery. No hardcoded generic registry-name guessing. No remote server files/state or growth mechanics are changed.

## Verification visible in-game

`/lfbtrace sapling` reports the targeted actual block ID/carrier, actual collision emptiness, outline shape, proof origin and whether the adapter installed CUTOUT. A registered built-in pack is not proof that another user pack has not overridden its assets. Existing `/lfbtrace` status, FPS, S12 timing, exports and Fast ON/OFF are unchanged.

## Executed / not executed

- 51 assertions on shipped sapling classes with actual Bamboo input, persisted/stripped-candidate proof, runtime gates and model materialization.
- Those tests use explicit Minecraft/Fabric/Gson/icon-result test doubles; the original icon interpreter/full conversion engine is NOT validated by those fixtures.
- Previous S12/FPS suite: 67 assertions, unchanged.
- ASM BasicVerifier: new/relevant sapling/conversion/carrier classes 16/146 methods; S12 suite 19/175 methods and prior annotation/wrapper checks.
- 1,782 existing entries unchanged; 32 protected S12/FPS/mixin/Bamboo/gameplay entries checked explicitly.
- All 16 block states and 48 metadata item-definition aliases checked; both generated sapling texture copies match original PNG bytes.
- Same-environment rebuild is byte-identical.

NOT executed: live Minecraft/Fabric startup, actual Sponge transformation, real Brigadier parser, actual Fabric pack activation/priority, GPU/in-game rendering, or the full real conversion pipeline. This remains a test candidate. No test/compile-only declarations are packaged.

## Build

JDK 21. Full kit: `python build.py --base <rev255.jar> --bamboo <original Bamboo-2.6.8.5.jar> --output <rev256.jar>`.
For this source mirror use `--classpath <actual intermediary Minecraft/Fabric/Gson dependencies>`; compile-only declarations are in the full kit. `Patch256.java` uses JDK-internal ASM only as an offline build tool, not as a game dependency.

Only three preexisting entries change: `fabric.mod.json`, `LegacySaplingSupport.class`, and `LegacyClientOnlySourceStripPass.class`. Old nested sapling parser classes and the prior ordinary-mod Sakura blockstates override are removed. Everything else is new code/proof/pack/provenance.

Commit uses `[skip ci] [skip actions]`; workflows are not modified.
