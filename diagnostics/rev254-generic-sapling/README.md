# rev254 Fast S12 + generic Legacy Sapling candidate

This directory is the reviewable source mirror for the locally built rev254 candidate:

- artifact: `legacyforgebridge-0.2.0-alpha.27-rev254-fast-s12-generic-sapling-test.jar`
- SHA-256: `10f1192470300a87195a2cf50faca12ca3efe767049e96d41b188d80497c2f22`
- verified rev251 base SHA-256: `01b36aff7d6f5ddc2b0097356c74f568f15173c4fed821f5a6c9788236e65502`
- Bamboo 2.6.8.5 reference SHA-256: `bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402`

## Generic sapling rule

The runtime reads the source-owned 1.7 class files already copied into a converted candidate. A block is admitted only when its inheritance chain can be proven to terminate at `net/minecraft/block/BlockSapling`. It does not use mod-name, registry-name, or texture-name heuristics.

For admitted saplings:

- empty collision is used only when no source-owned collision override is present;
- CUTOUT is installed only when no source-owned `getRenderType` override is present;
- a unique constant constructor `setBlockBounds(...)` is recovered as the selection box;
- otherwise vanilla 1.7 sapling bounds `0.1,0,0.1 -> 0.9,0.8,0.9` are used;
- dynamic/ambiguous bounds fail closed.

Arbitrary `BlockBush`, crops, flowers, and reeds are not treated as saplings.

The exact Bamboo 2.6.8.5 `BlockSakura.class` was used as a real-source regression fixture and resolves to its original bounds with `emptyCollision=true` and `cutout=true`. A Bamboo resource compatibility override remains in the local artifact because already-baked converted resource JSON cannot be safely rewritten generically at runtime.

## Fast S12

Normal server velocity remains enabled. There is no raw-packet bypass and no S12 cancellation. For actual remote protocol 5 (1.7.10), a local-player velocity enqueue only signals an earlier game-thread drain of the existing vanilla FIFO queue before local physics.

## Verification

Offline checks on the produced JAR:

- real Bamboo BlockSakura ancestry/bounds proof passed;
- five synthetic sapling cases passed;
- ASM BasicVerifier: 27 relevant classes / 164 methods;
- Mixin annotation checks: 6;
- 24 packaging/provenance checks;
- `LegacyBehaviorRuntime` and `Rev242LandingBridge` remain byte-identical to the verified rev251 base.

Not claimed: live Minecraft 1.21.11 startup, actual Sponge transformation, GPU/in-game rendering, or proof that Fast S12 fully removes every delayed-jump case.

This commit intentionally does not modify workflows and uses [skip ci] [skip actions].
