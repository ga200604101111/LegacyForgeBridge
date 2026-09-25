# LegacyForgeBridge — ItemReed placement runtime slice (2026-09-16)

Base branch: `feature/generic-conversion-bamboo-corpus2`

Base commit: `7d0c236c75c65f7b3b4dc4f90617bd05a48fc7a2`

## Scope

This slice extends the already-proven plant pipeline from `ItemSeeds` / `ItemSeedFood` to source-proven inherited Minecraft 1.7.10 `ItemReed` placement.

It does not make arbitrary BlockItem placement executable. Runtime admission remains fail-closed and requires all of the following:

- source item constructor proves an `ItemReed(Block)` target binding;
- source item lineage reaches vanilla `ItemReed` without source instance-method overrides;
- target family is proven `reed`;
- target plant runtime / survival proof is complete;
- target placement callbacks are inherited vanilla lifecycle only;
- source proof hashes align;
- the emitted placement rule is marked runtime-complete.

## Runtime semantics

`ConvertedLegacyPlantingItem` now has a dedicated `REED` branch rather than reusing seed rules.

Preserved 1.7.10 behavior:

- one-layer snow is replaced in place and normalizes placement side to `UP`;
- vine / short grass / dead bush are replaced in place;
- other clicked blocks shift the target coordinate by the clicked face;
- edit permission and non-empty stack are checked on the resolved target coordinate;
- target raw metadata starts at `0`;
- the stack shrinks only after a successful block set;
- after edit/non-empty checks, the interaction is handled even when placement viability rejects the block, matching legacy `ItemReed#onItemUse` return behavior.

The converted target must already be a proven `ConvertedLegacyPlantBlock`; no generic or unknown block target is admitted.

## Materialization

`GeneratedModSupport` now selects `ConvertedLegacyPlantBlock` for any source-proven plant-placement target (crop or reed), and selects `ConvertedLegacyPlantingItem` for any runtime-complete planting rule.

Existing seed-specific APIs remain seed-specific so Reed cannot accidentally satisfy crop-only checks.

## Regression coverage

- parser accepts runtime-complete Reed only when the target plant runtime family is `REED`;
- Reed rules reject invented constructor soil identity;
- crop-target and generic plant-target readiness stay distinct;
- pure placement geometry covers in-place, face-offset and thin-snow side normalization;
- a synthetic inherited `ItemReed` source with inherited target callbacks must become `runtimeComplete=true` and emit `item_reed_1_7_10`.

## Remaining boundary

This does not claim exact Bamboo whole-JAR coverage until the checksum-pinned Bamboo corpus is available to CI/conversion staging again. Unknown/custom `ItemReed` subclasses and targets with source placement/lifecycle overrides remain fail-closed.
