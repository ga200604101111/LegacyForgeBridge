# rev239 — render source-proven WATER through vanilla FluidState

Target branch: `feature/generic-conversion-iyamato-corpus3`.

rev239 is cumulative with rev238.

## Live finding

rev237/rev238 added translucent block layer + water tint alpha, but live testing showed Bamboo spa water was still much more opaque than ordinary water.

The reason is architectural: the converted liquid was still world-rendered as a normal block model. Vanilla 1.21.11 water is not rendered by that path; it is rendered from `FluidState` by `LiquidBlockRenderer`.

## rev239 behavior

For every source-proven legacy WATER block:
- retain the converted block registry identity for the unchanged Forge 1.7.10 protocol round-trip;
- expose a real vanilla WATER `FluidState`;
- return `RenderShape.INVISIBLE` for the carrier block so the old generated box model is not world-rendered;
- let vanilla/Fabric/Sodium water rendering own transparency, water sprites, face culling and visible liquid height.

There is no Bamboo registry-name special case.

### Legacy metadata -> vanilla LiquidBlock state

The mapping mirrors Minecraft 1.21.11 `LiquidBlock`'s own `stateCache` exactly:
- metadata 0 -> `water.getSource(false)`
- metadata 1..7 -> `water.getFlowing(8-meta, false)`
- metadata 8..15 -> `water.getFlowing(8, true)`

Vanilla `FlowingFluid#getOwnHeight` is amount / 9, so:
- source/falling visible height is 8/9 when there is no same-fluid block above;
- flowing levels use exactly the same heights as normal modern water;
- if water is directly above, vanilla water rendering promotes the face height to 1.0.

This also matches the old 1.7 BlockLiquid visual rule where falling metadata 8..15 uses the full source-like liquid height.

## Why the rev237 alpha helper remains

The prior block-color alpha compatibility code remains present for fallback/resource presentation, but it no longer determines world WATER transparency once the source-proven block is registered. World rendering is now vanilla fluid rendering.

## Output

- file: `legacyforgebridge-0.2.0-alpha.27-rev239-corpus4-local.23.jar`
- bytes: `4477576`
- SHA-256: `42f7d5e8ba647c8f599fe9d152aeeab24e4fe813a08f55cdea75500766d48a3d`
- internal version: `0.2.0-alpha.27-corpus4-local.23-rev239-local-test.1`
- converter revision: `2026-10-02.239-vanilla-liquid-fluidstate`
- cache compatibility unchanged from rev233.

## Binary audit vs rev238

- base entries: 1689
- output entries: 1691
- duplicates: 0
- removed: 0
- added:
  - `dev/yinghuang/legacyforgebridge/behavior/Rev239VanillaLiquidBridge.class`
  - `legacyforgebridge/rev239-build.json`
- changed existing:
  - `dev/yinghuang/legacyforgebridge/BuildInfo.class`
  - `dev/yinghuang/legacyforgebridge/convert/runtime/ConvertedLegacyBlock.class`
  - `fabric.mod.json`
- manifest byte-for-byte content unchanged
- nested Energy JAR byte-for-byte unchanged

## Validation

- exact LiquidBlock stateCache mapping review against Minecraft 1.21.11 source: pass
- metadata 0..15 mapping harness: pass
- source/falling height 8/9 mapping harness: pass
- patched/new class ASM parse: pass
- `javap -v` parse: pass
- ZIP integrity: pass
- live Minecraft launch: not available in build environment

No Actions dispatch, PR, tag, release, workflow modification, force push, main-branch modification or Bamboo-branch modification was performed.
