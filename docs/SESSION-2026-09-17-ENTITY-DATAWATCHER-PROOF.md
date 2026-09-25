# 2026-09-17 — Generic Entity registration / DataWatcher source proof

## Scope

This slice starts the generic Entity/DataWatcher migration stage for the whole-JAR Forge 1.7.10 conversion path. It does not add Bamboo-specific production logic and does not claim that a source entity is runtime-complete merely because `EntityRegistry.registerModEntity` was recovered.

Converter revision: `2026-09-17.60`.

## New proof boundary

`LegacyEntityDataWatcherAnalyzer` consumes the already-generic lifecycle semantics directly from source bytecode and admits an entity registration only when the legacy `registerModEntity` payload proves:

- source entity class;
- registry name;
- numeric legacy id;
- tracking range;
- update frequency;
- velocity-update flag.

For source-owned DataWatcher initialization, the analyzer then follows the effective `entityInit` / `func_70088_a` implementation and source-super chain. A watcher definition is admitted only when all of the following are proven:

- receiver is `this.dataWatcher`;
- watcher index is a constant in the legacy `0..31` range;
- call is ordinary `DataWatcher.addObject(int,Object)`;
- default value has a supported exact source type (`byte`, `short`, `int`, `float`, or `string`);
- wrapper conversion/default dataflow is constant and unambiguous;
- each watcher index is defined exactly once.

The following remain fail closed in this first Entity slice:

- dynamic watcher indices;
- `addObjectByDataType` without a source default-value proof;
- watcher definitions hidden behind source helpers;
- unsupported object defaults such as legacy ItemStack/ChunkCoordinates until their migration contract is implemented;
- ambiguous registration arguments.

This is deliberately stricter than generating a placeholder entity.

## Materialized IR

`LegacyEntityDataWatcherPass` writes:

- `legacyforgebridge/entity-datawatcher-rules.json`
- schema version 1

Each admitted rule contains a deterministic modern entity identity, the complete recovered `registerModEntity` transport parameters, and the source-owned watcher defaults. Both the root and each rule explicitly keep `runtimeImplementationWired=false`.

The pass is wired into the generic Forge 1.7.10 profile. It does not modify the RPGTool-specific profile.

## Regression coverage

Unrelated-namespace synthetic regressions prove:

- helper/lifecycle `registerModEntity` extraction feeds the new analyzer;
- direct byte/int/float watcher defaults are recovered without loading legacy classes;
- velocity/tracking/update registration metadata remains exact;
- dynamic watcher indices fail closed;
- the sidecar never claims runtime wiring.

## Bamboo boundary

Bamboo's earlier exact lifecycle baseline contains 24 mod-entity registrations. This commit does not invent an exact DataWatcher-ready count when the checksum-pinned Bamboo binary is not supplied to the current CI workspace. The new analyzer/pass is generic infrastructure that will classify those 24 registrations the next time the exact SHA-256 corpus is present.

The whole Bamboo candidate remains `PARTIAL`: actual EntityType class behavior, watcher mutation/read semantics, AI/projectiles, persistence/networking, renderers, worldgen/dimension conversion, and legacy-class loader closure still remain separate gates.
