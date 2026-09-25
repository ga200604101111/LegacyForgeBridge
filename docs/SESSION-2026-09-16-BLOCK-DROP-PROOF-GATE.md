# Session 2026-09-16 — Generic block drop proof gate

Branch: `feature/generic-conversion-bamboo-corpus2`

## Purpose

This slice hardens the generic Minecraft 1.7.x Block drop pipeline before any normal Block drop
runtime is enabled.

`LegacyBlockDropPlanCompiler` previously described its composed item / quantity / item-damage result
as runtime-safe. That was too broad: Forge 1.7.x has surrounding harvest/drop callbacks that can
replace or bypass those component methods.

The compiler now treats a source-owned override of any of these paths as fail-closed evidence:

- `getDrops(World, ...)`
- `dropBlockAsItem(...)`
- `dropBlockAsItemWithChance(...)`
- `harvestBlock(...)`
- `canSilkHarvest()` and the Forge contextual overload
- `createStackedBlock(int)`

The check walks the complete source-owned superclass chain. It is generic source-shape proof and
contains no Bamboo class names, registry IDs or mod-specific exceptions.

## New conversion evidence

`LegacyBlockDropAnalysisPass` still writes independent evidence to:

`legacyforgebridge/block-drop-analysis.json`

It now also writes composed, fail-closed normal-drop plans to:

`legacyforgebridge/block-drop-plans.json`

A complete plan records:

- resolved converted Block identity when available;
- drop target kind and modern identity when already proven;
- constant quantity;
- all 16 legacy metadata-to-item-damage results;
- which values came from direct vanilla `Block` defaults;
- `normalDropProofComplete=true`;
- `sourceDropPathOverrideFree=true`;
- `runtimeComplete=false`.

The runtime flag deliberately stays false. The remaining explicit blockers are:

1. harvest eligibility proof;
2. silk-touch stacked-item proof;
3. explosion drop-chance proof.

No modern loot/drop override is installed by this slice.

## Regression coverage

Synthetic source JAR tests cover direct and SRG-named overrides for the surrounding drop path.
A plain direct-`Block` fixture remains admissible, while custom getDrops, direct drop, chance drop,
harvest, silk and stacked-block paths are rejected.

A second conversion-pass fixture proves that the normal-drop plan sidecar is emitted with the
runtime blockers and that an unsafe silk override is kept in the incomplete set.

## Exact Bamboo boundary

The checksum-matched `Bamboo-2.6.8.5.jar`
(`bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402`) is not preserved in the
current CI workspace. This slice therefore does **not** claim new exact whole-JAR Bamboo coverage.

It improves the generic conversion capability that the exact corpus will use when that source JAR
is available again. Candidate status / loader safety must not be promoted from this proof alone.
