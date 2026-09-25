# 2026-09-17 — Variant snowball item-use launch proof

## Scope

This slice follows converter revision `2026-09-17.110`, where source-complete projectile impact semantics were normalized into a runtime-candidate IR. Review of Bamboo's actual `ItemDirtySnowball.onItemRightClick` exposed one remaining source boundary before a playable runtime can be admitted: launch behavior itself.

Converter revision: `2026-09-17.111`.

This slice proves that item-use shell independently from projectile impact semantics and makes it a mandatory runtime-candidate admission input.

## Bounded launch proof

`LegacyVariantSnowballLaunchAnalyzer` starts only from families already admitted by `LegacyVariantSnowballAnalyzer`, so the spawned projectile and metadata-selected selector lookup are already source-bound.

For the declaring legacy `ItemSnowball` use method it independently requires:

1. the player capability field and creative-mode flag;
2. an `IFNE` skip around an exact one-count decrement of the original `ItemStack.stackSize`;
3. a `World.isRemote` server-only gate;
4. inside that gate, `random.bow` at volume `0.5F` and pitch exactly `0.4F / (itemRand.nextFloat() * 0.4F + 0.8F)`;
5. inside the same gate, construction of the already-proven custom projectile family followed directly by `spawnEntityInWorld`;
6. return of the original ItemStack local after the gate.

MCP and SRG field/method names are admitted. The inherited static `itemRand` field may be referenced through any source-owned subclass proven to reach legacy `Item`, rather than assuming one constant-pool owner.

The aggregate `itemUseSemanticsComplete` is true only when all five behavior facts are proven.

## Independent sidecar

Launch semantics are written to `legacyforgebridge/variant-snowball-launch-proof.json`, schema 1, instead of inflating the already-stable schema-9 projectile-impact proof.

Each rule records:

- `creativeConsumptionGuardProven`;
- `serverOnlyLaunchGateProven`;
- `legacyBowSoundProven`;
- `metadataProjectileSpawnInsideGateProven`;
- `originalStackReturnProven`;
- `itemUseSemanticsComplete`;
- `runtimeImplementationWired=false`.

## Runtime-candidate admission schema 2

`LegacyVariantSnowballRuntimeCandidatePass` now requires both sidecars from the same source hash and exact family identity.

A runtime candidate is admitted only when:

- projectile impact/source semantics are complete;
- the exact launch family exists exactly once;
- `itemUseSemanticsComplete=true`;
- neither proof sidecar claims runtime implementation;
- the family still binds uniquely back to the converted manifest item.

Admitted runtime IR now also records the exact launch constants and policies:

- `launchSound=random.bow`;
- volume `0.5`;
- pitch numerator/random scale/base `0.4 / (rand*0.4 + 0.8)`;
- `consumeOutsideCreative=true`;
- `serverAuthoritativeLaunch=true`;
- `sourceSemanticsComplete=true`.

Missing/stale launch proof, ambiguous launch identity, or any incomplete launch fact fails closed.

## Regression

Normal CI covers:

- the complete bounded launch shell;
- a missing creative guard while retaining independent server/sound/spawn facts;
- a missing server gate, which prevents server-only sound/spawn admission;
- a wrong sound id while retaining independent server/spawn facts;
- launch sidecar materialization separate from impact proof;
- runtime candidacy only when both launch and impact sidecars are complete;
- missing launch sidecar and incomplete creative-consumption semantics failing closed;
- the modern id retaining the source namespace (`foreign:variant_ball`) rather than an unrelated test-context namespace.

The checksum-pinned exact-corpus test requires Bamboo's `snowball` family to prove the complete launch shell from the exact Bamboo JAR.

## Next boundary

With `.111`, the remaining DirtySnowball work is implementation rather than source reverse-engineering:

1. install admitted schema-2 runtime rules before generated item registration;
2. register the modern projectile EntityType;
3. instantiate a specialized item for the exact modern id even though generic item classification is currently `item`;
4. reproduce creative consumption, server-only sound/spawn and metadata carriage;
5. compile base damage, potion/no-op/random-teleport effects and portal presentation;
6. register a thrown-item renderer;
7. validate behavior/bytecode and only then open retirement of the legacy item/projectile cohort.
