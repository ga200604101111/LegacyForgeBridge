# rev225 — ViaFabricPlus auto 1.7.10 family + exact own-item foxfire picking

Target branch: `feature/generic-conversion-iyamato-corpus3`.

## 1. ViaFabricPlus did not auto-select 1.7.10

The cumulative bridge already exposed `ViaFabricPlusBackend.selectMinecraft1710ForNextConnection()`, which calls ViaFabricPlus' public `setTargetVersion(ProtocolVersion.v1_7_6, true)`. In ViaVersion this constant is the shared 1.7.6-1.7.10 protocol family (protocol id 5), which includes Minecraft 1.7.10.

The bug was wiring: nothing called the method.

rev225 calls it immediately after `ViaFabricPlusBackend.initialize(platform)` from the ViaFabricPlus load entrypoint. This makes LegacyForgeBridge select the 1.7.6-1.7.10 family instead of inheriting the user's previous ViaFabricPlus target.

## 2. Bamboo foxfire held-own-item semantics

Exact source: `ruby.bamboo.block.BlockKitunebi`.

Its client random-display logic is source-proven to do all of the following:

- reads the current equipped ItemStack;
- requires that ItemStack's Item is an ItemBlock whose `Block.getBlockFromItem(item)` is the foxfire block itself;
- only in that exact-own-BlockItem branch sets the visible flag true;
- held branch sets block bounds to `[0,0,0 -> 1,1,1]`;
- all other hand states (empty hand or any other item) set block bounds to `[0,0,0 -> 0,0,0]`;
- physical collision is null;
- `RenderKitunebi` draws crossed squares only while the block's source visible flag is true.

Therefore the intended client behavior is:

- holding foxfire itself: visible + ray-selectable + normally breakable;
- empty hand: invisible + no ray-selection target;
- holding any other item: invisible + no ray-selection target.

rev225 makes the client getShape injection resolve the source condition directly from the actual player's main hand, comparing `player.getMainHandItem().is(self.asItem())`. It no longer depends on one exact BlockGetter instance or on `CollisionContext.empty()`, both of which can miss modern picking calls. The source-proven held/unheld selection bounds remain the shape output.

The cumulative renderer's held-model path already compares the block identity against the registry identity of the actual main-hand item, so visual visibility remains exact-own-item only.

## Binary output

- base: `legacyforgebridge-0.2.0-alpha.27-rev224-corpus4-local.8.jar`
- output: `legacyforgebridge-0.2.0-alpha.27-rev225-corpus4-local.9.jar`
- output bytes: `4,436,552`
- SHA-256: `180d0a215c4099757717f6c697db388e17f39237403c82644d86b73faa94aa12`
- internal version: `0.2.0-alpha.27-corpus4-local.9-rev225-local-test.1`
- converter revision remains `2026-09-30.224-flat-item-projectile-blocktexture-blockcarry` because this revision changes runtime protocol/picking behavior, not deterministic conversion output.
- cache compatibility version remains `0.2.0-alpha.27-corpus3-local.3-rev220-local-test.1`.

Compared with rev224 only four entries change:

- `dev/yinghuang/legacyforgebridge/BuildInfo.class`
- `dev/yinghuang/legacyforgebridge/protocol/ViaFabricPlusEntrypoint.class`
- `dev/yinghuang/legacyforgebridge/mixin/client/LegacyHeldItemVisibilityMixin.class`
- `fabric.mod.json`

No entries were added or removed. The nested Energy JAR is byte-for-byte unchanged. ZIP integrity passes. ASM BasicVerifier passes BuildInfo, ViaFabricPlusEntrypoint, LegacyHeldItemVisibilityMixin, ViaFabricPlusBackend, and ConvertedLegacyBlock.

No clean cumulative Loom rebuild is claimed; this is an audited binary overlay on rev224, which itself is based on the exact user-supplied corpus4-local.6 cumulative binary.

No Actions dispatch, PR, tag, release, workflow modification, or main/Bamboo branch modification was performed.
