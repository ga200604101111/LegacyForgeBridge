# 2026-09-17 — Entity DataWatcher reachable static-helper closure

## Scope

This slice follows converter revision `2026-09-17.61` and extends the Entity DataWatcher access proof beyond methods declared directly on the registered source entity lineage.

Converter revision: `2026-09-17.62`.

## Proven helper closure

`LegacyEntityDataWatcherAccessAnalyzer` still seeds analysis only from non-static methods declared on the registered source entity and source-owned superclasses. From those methods it now follows staged-JAR `INVOKESTATIC` calls when an object/array argument is dataflow-proven to be the current source entity instance.

For every admitted static helper:

- the exact helper owner/name/descriptor must exist in the staged source JAR;
- one or more helper parameters must be proven from the source entity instance;
- those bound parameter locals become the only accepted roots for DataWatcher receiver proof;
- helper-to-helper `INVOKESTATIC` calls propagate the same proof recursively;
- visited method + bound-local states are de-duplicated, so helper cycles terminate;
- uncalled helper methods are not scanned and cannot contaminate the source entity rule.

The existing watcher constraints remain unchanged inside helpers:

- watcher index must be constant;
- index must exist in the already-proven source-owned schema;
- typed getter kind must match the schema;
- `updateObject` / `func_75692_b` value kind must match the schema;
- unsupported watcher calls remain fail-closed.

## Why only static helpers in this slice

`INVOKESTATIC` has an exact bytecode target, so the converter can prove which staged method is reached without inventing JVM dispatch semantics.

This slice deliberately does not claim closure for:

- overridable `INVOKEVIRTUAL` helper calls;
- `INVOKEINTERFACE` helper calls;
- arbitrary aliasing through fields or helper-returned entity references;
- modern `SynchedEntityData` runtime materialization.

Those paths require a separate dispatch/alias proof before they can safely be marked complete.

## Materialized IR

`legacyforgebridge/entity-datawatcher-access.json` remains schema version 1 and now records:

- `sourceLineageAccessSurfaceComplete=true`;
- `reachableStaticHelperClosureComplete=true`;
- `reachableHelperClosureComplete=false`;
- `runtimeImplementationWired=false`.

The general helper-closure flag intentionally remains false because virtual/interface helper dispatch is still outside this proof boundary.

## Regression coverage

Synthetic unrelated-namespace tests now additionally cover:

- direct entity -> static helper watcher writes;
- recursive static helper -> static helper watcher reads;
- exact source-owner attribution for helper callsites;
- an unreachable helper containing a dynamic watcher index, proving it is ignored rather than globally scanned;
- a reachable helper containing a dynamic watcher index, proving the entity rule remains fail-closed;
- sidecar output explicitly distinguishing bounded static-helper closure from general helper closure.

## Bamboo boundary

This is generic Entity infrastructure for the Bamboo corpus; it does not claim an exact Bamboo entity count because the checksum-pinned external Bamboo JAR is not part of normal CI.

The next safe Entity gate is either a dispatch-proof extension for bounded virtual/interface helpers or the first modern `EntityType` + `SynchedEntityData` runtime slice for entity families whose access surface is already fully proven. The overall Bamboo candidate remains `PARTIAL`.
