# rev233 — client armor HUD projection and source-proven liquid semantics

Target branch: `feature/generic-conversion-iyamato-corpus3`.

rev233 is a cumulative local binary overlay on the exact rev232 artifact.

## Armor HUD

Live rev232 testing proved that converted iY armor still did not populate the 1.21.11 armor HUD. The reason is architectural: the modern HUD reads the local player's entity `ARMOR` attribute through `LivingEntity#getArmorValue`, while an unchanged remote Forge 1.7.10 server does not provide the modern equipment-derived entity attribute state expected by the 1.21.11 client.

rev233 therefore treats armor as a client presentation compatibility concern:
- only the local player while ViaFabricPlus targets Minecraft 1.7.10 is overridden;
- the four equipped armor slots are inspected;
- source-proven 1.7.10 `armorPoints` are the base value for converted legacy armor;
- additional modern stack `ARMOR` modifiers are accumulated;
- LFB-owned `source_armor`, `compat_armor` and converted base carriers are excluded from the additional-modifier sum so the base value is not counted twice;
- the result is floored and clamped to the modern 0..30 armor attribute domain;
- the unchanged Forge 1.7.10 server remains authoritative for real combat/damage.

## Bamboo spa water source evidence

Exact source corpus:
- `Bamboo-2.6.8.5.jar`
- SHA-256: `bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402`

Direct original-bytecode inspection proves `ruby/bamboo/block/BlockSpaWater`:
- extends `net.minecraft.block.BlockLiquid`;
- calls the BlockLiquid constructor with vanilla `Material.water`;
- returns render type 4;
- is non-opaque and non-normal;
- returns `null` collision AABB;
- returns a zero-volume selected AABB;
- returns `null` from `getPickBlock`;
- drops zero items;
- uses the inherited water liquid presentation and a default color value of `0xFFFFFF` (no extra color multiplication), rather than a white solid-block texture.

The legacy registry analyzer normalizes its registration to `spa_water` and the existing generic liquid analyzer already admits it as a WATER rule. The remaining bug was runtime/presentation loss: it was materialized as an ordinary box-model block whose outline was still selectable, and the modern water texture lacked a tint index/color provider.

## rev233 liquid repair

For every source-proven legacy liquid rule, not Bamboo by name:
- generated liquid model faces receive `tintindex: 0`;
- WATER rules register a block color provider that uses the modern biome water color;
- the source-proven liquid ID is registered at client initialization;
- a client mixin returns `Shapes.empty()` from the converted block's outline/selection shape for those registered liquids;
- existing empty collision and translucent render-layer behavior is retained.

This restores the intended water-like blue presentation and removes direct mouse selection/break targeting of spa water while keeping the behavior source-driven.

## Cache invalidation

Cache compatibility is intentionally advanced to:
`0.2.0-alpha.27-corpus4-local.17-rev233-cache.1`

This forces Bamboo/iY/RPGTool converted candidates to be regenerated so the liquid-model tint data is not hidden behind an older cached candidate.

## Output

- file: `legacyforgebridge-0.2.0-alpha.27-rev233-corpus4-local.17.jar`
- bytes: `4,439,272`
- SHA-256: `d5ffb4f6aef123774747335953230123b3c96a6405b666e465153d348ea28907`
- internal version: `0.2.0-alpha.27-corpus4-local.17-rev233-local-test.1`
- converter revision: `2026-10-01.233-armor-hud-liquid-semantics`

## Binary audit vs rev232

- base entries: 1675
- output entries: 1679
- duplicate entries: 0
- removed entries: 0
- added:
  - `Rev233ArmorHudCompat.class`
  - `Rev233LiquidCompat.class`
  - `LegacyLiquidSelectionMixin.class`
  - `legacyforgebridge/rev233-build.json`
- changed existing: 6
  - `BuildInfo.class`
  - `LegacyLiquidPresentationPass.class`
  - `LegacyLivingBehaviorMixin.class`
  - `ConvertedLiquidPresentationRuntime.class`
  - `fabric.mod.json`
  - `legacyforgebridge.client.mixins.json`
- nested Energy JAR SHA-256 remains `072cd9fad2ec00c3b11b5862f34bb12f0554f727958ad685ac7b81b4fd79eac3`
- ZIP integrity: pass
- javap parse of all changed/new runtime classes: pass
- live Minecraft launch: not available in the build environment

No Actions dispatch, PR, tag, release, workflow modification, force push, main-branch modification or Bamboo-branch modification was performed.
