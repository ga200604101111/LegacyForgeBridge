# 2026-09-17 — Entity instantiation / world-spawn surface

## Scope

This slice follows converter revision `2026-09-17.76`, where the first proof-complete plain Entity family gained real EntityType and no-op renderer registration.

Converter revision: `2026-09-17.77`.

## Why this gate exists

Registering a modern replacement is not sufficient to make an old source Entity loader-safe. Retained source code may still construct the old class directly or pass it to `World.spawnEntityInWorld` / `func_72838_d`.

Before rewriting or pruning legacy Entity classes, the converter now inventories those creation/use sites without loading any source class.

## Analyzer

`LegacyEntityInstantiationAnalyzer` starts from lifecycle-proven `EntityRegistry.registerModEntity` classes and scans every readable source method.

It records:

- direct constructor calls whose owner is a registered Entity class;
- exact constructor descriptor and source callsite;
- direct `World.spawnEntityInWorld(Entity)` / `func_72838_d(Entity)` calls;
- spawn arguments whose concrete registered Entity type is dataflow-proven;
- unresolved spawn arguments separately rather than assigning them by guess.

Spawn argument type proof currently accepts exact source evidence such as:

- `NEW` of a registered Entity class;
- local aliases whose source value remains provable;
- exact registered-Entity field descriptors;
- exact registered-Entity method return descriptors;
- safe checkcast propagation.

Ambiguous merges and generic `Entity` parameters remain unresolved.

## Sidecar

`LegacyEntityInstantiationPass` writes:

`legacyforgebridge/entity-instantiation-surface.json`

For each registered source Entity it includes direct construction sites, proven world-spawn sites, whether an executable modern runtime replacement now exists, and whether source instantiation rewriting is still required.

The root also records unresolved world-spawn calls. `spawnRewriteWired=false` remains explicit: this checkpoint is an inventory/proof gate, not a source-bytecode rewrite.

## Fail-closed boundary

This slice does not yet:

- rewrite `new LegacyEntity(...)` to a generated modern EntityType factory;
- rewrite `World.spawnEntityInWorld` callsites;
- infer a registered type from a generic `Entity` parameter;
- authorize deletion of a legacy Entity class merely because a modern replacement exists.

Those actions require the source-use evidence introduced here.
