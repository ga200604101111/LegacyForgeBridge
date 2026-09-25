# 2026-09-17 — Entity synchronized-data runtime mapping plan

## Scope

This slice follows converter revision `2026-09-17.63`. It does not register a modern entity runtime yet. Instead it joins the already-proven entity registration/DataWatcher definition IR with the bounded DataWatcher access IR and emits the first explicit modern synchronized-data mapping plan.

Converter revision: `2026-09-17.64`.

## Input proof gates

`LegacyEntityRuntimePlanPass` requires both existing schema-version-1 sidecars for the same source hash:

- `legacyforgebridge/entity-datawatcher-rules.json`;
- `legacyforgebridge/entity-datawatcher-access.json`.

For each entity registration the pass requires:

- matching legacy registry name and source class;
- completed direct lineage access proof;
- completed reachable static-helper closure;
- completed bounded exact-dispatch helper closure;
- every access index to exist in the source-owned DataWatcher definition;
- every access value kind to exactly match its definition;
- access operation to be either `read` or `write`.

Any mismatch skips the whole entity plan rather than emitting a partial synchronized-data layout.

## Modern mapping vocabulary

The emitted mapping is proof IR. Serializer names are stable LFB plan vocabulary and do not yet instantiate Minecraft `EntityDataAccessor` objects.

Current primitive/string mapping:

| Legacy DataWatcher kind | Planned modern value kind | Serializer | Adapter |
| --- | --- | --- | --- |
| byte | byte | BYTE | identity |
| short | int | INT | signed_short_widen |
| int | int | INT | identity |
| float | float | FLOAT | identity |
| string | string | STRING | identity |

The `short -> int` mapping is lossless widening. Future runtime code must preserve legacy signed-short semantics at legacy-facing write boundaries; this plan does not silently reinterpret it as an arbitrary modern integer field.

ItemStack and other unsupported legacy DataWatcher kinds remain outside the currently proven analyzer surface and therefore cannot enter this plan.

## Output

The generic profile now runs `LegacyEntityRuntimePlanPass` after DataWatcher definition and access proof.

Output:

- `legacyforgebridge/entity-runtime-plan.json`
- schema version 1

Root state remains explicitly closed:

- `runtimeAdmissionReady=false`;
- `runtimeImplementationWired=false`.

Each admitted mapping rule records:

- modern entity id and source registration provenance;
- tracking range/update frequency/velocity-update registration facts;
- source watcher/access proof gates;
- `synchedDataMappingComplete=true`;
- one mapped synchronized-data entry per proven source watcher index;
- source kind, planned modern kind, serializer, adapter and default value;
- proven read/write call counts for that source index;
- explicit runtime blockers.

With the current access proof the principal blocker remains `reachable-helper-closure-incomplete`. The plan also records `entitytype-syncheddata-runtime-not-materialized` because this slice intentionally generates no EntityType or SynchedEntityData implementation.

## Regression coverage

`LegacyEntityRuntimePlanPassTest` uses sidecar fixtures independent of Minecraft runtime loading and proves:

- byte -> BYTE identity;
- short -> INT signed-short widening;
- int -> INT identity;
- float -> FLOAT identity;
- string -> STRING identity;
- per-index read/write counts;
- bounded exact-dispatch closure is required;
- general helper closure remains false;
- runtime admission remains false;
- a definition/access value-kind mismatch skips the entity rule fail-closed.

## Boundary before runtime materialization

This plan is not evidence that an entity is otherwise playable. AI, physics, projectile behavior, spawn construction, NBT, event integration, networking and rendering remain independent semantic gates.

The next safe step is to complete a broader whole-source DataWatcher reachability/escape proof, or to define a deliberately narrow generated entity family whose behavior semantics are separately proven. Only after the applicable gates are closed should `EntityType` and concrete `SynchedEntityData` accessors be emitted. The Bamboo candidate remains `PARTIAL`.
