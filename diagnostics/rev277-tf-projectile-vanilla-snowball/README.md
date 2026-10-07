# rev277 Twilight Forest Part 2F-2 — vanilla RenderSnowball presentation identity

Date: 2026-10-07

## Problem

The Part 2F census found 15 registered projectile-base candidates and 0 admitted by the existing
projectile presentation analyzer.

Seven of those failures shared one narrow generic cause: the source renderer is vanilla
`RenderSnowball`, but its constructor receives a fixed `net.minecraft.init.Items.*` field.
The analyzer previously resolved only mod-owned registered item FieldBindings.

Twilight Forest examples include wheat_seeds, ender_pearl, paper, magma_cream, slime_ball and
snowball presentation items.

## Generic analyzer change

`LegacyProjectilePresentationAnalyzer.vanillaSnowballBinding` now also resolves an exact
`net.minecraft.init.Items` static field through `LegacyVanillaRegistry1710`.

The rule records:

- source item class = `net/minecraft/init/Items`;
- legacy 1.7.10 vanilla registry name from the pinned SRG table;
- the source RenderSnowball constant metadata.

Unknown fields or non-item vanilla fields remain unresolved.

No Twilight Forest class, entity name, or registry name is used by the rule.

## Materialization change

Candidate-owned presentation items still resolve through the converted content item table exactly as
before.

Pinned vanilla 1.7.10 presentation items instead run through `LegacyVanillaStackDataFix.upgrade`.

The vanilla path is intentionally bounded:

- only fixed/non-watcher-selected RenderSnowball presentation items are accepted;
- DFU must return one modern item id;
- DFU must not require extra ItemStack components, because the current projectile carrier does not
  store an arbitrary component patch;
- once fixed legacy metadata has been absorbed by DFU, runtime legacy metadata is reset to 0 to
  avoid applying the same flattening twice.

The output sidecar additionally records `presentationItemSource` and
`legacyPresentationMetadata` for audit.

## Regression

A renamed synthetic EntityThrowable fixture with no registered launcher item and
`RenderSnowball(Items.field_151126_ay)` must resolve to the pinned 1.7.10 registry name
`snowball`.

A materialization regression then requires DFU to emit `minecraft:snowball` with runtime metadata
0.

Candidate-owned item resolution has a separate regression proving its previous behavior is
unchanged.

## Exact Twilight Forest target

The exact TF 2.3.8 renderer census contains seven candidates matching this newly supported fixed
vanilla RenderSnowball family:

- tfnaturebolt
- tflichbolt
- tftwilightwandbolt
- tftomebolt
- tflichbomb
- tfslimeblob
- tficesnowball

This revision targets only that shared family. The remaining eight projectile candidates stay
fail-closed for their previously classified reasons.
