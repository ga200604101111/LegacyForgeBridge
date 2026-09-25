# 2026-09-17 — Entity DataWatcher exact-dispatch helper closure

## Scope

This slice follows converter revision `2026-09-17.62` and expands the previously proven static-helper call graph to additional helper calls whose JVM target is still exact from staged source bytecode.

Converter revision: `2026-09-17.63`.

## Exact helper dispatch admitted

`LegacyEntityDataWatcherAccessAnalyzer` now propagates source-entity provenance through three bounded call forms when the exact target method exists in the staged JAR:

- `INVOKESTATIC` source helpers;
- `INVOKESPECIAL` source helpers, including private/special methods and constructors when a source-entity argument or receiver is actually bound;
- `INVOKEVIRTUAL` only when the declared target method is `final` or the declared owner class is `final`.

For non-static calls the analyzer reconstructs the receiver slot plus argument-local layout and marks only receiver/argument locals proven to carry the source entity. Direct DataWatcher access in the reached helper must still use those proven roots.

## Still intentionally excluded

The helper closure remains fail-closed for dispatch or aliasing that cannot yet be proven exactly:

- overridable `INVOKEVIRTUAL` targets;
- `INVOKEINTERFACE` targets;
- receiver/argument aliases recovered through arbitrary fields;
- entity references returned by helper methods;
- reflection / invokedynamic call paths;
- modern `SynchedEntityData` runtime materialization.

A skipped overridable helper is not treated as proof that its behavior is safe; instead the sidecar keeps general helper closure explicitly incomplete.

## Sidecar state

`legacyforgebridge/entity-datawatcher-access.json` remains schema version 1 and each admitted rule now reports:

- `sourceLineageAccessSurfaceComplete=true`;
- `reachableStaticHelperClosureComplete=true`;
- `reachableExactDispatchHelperClosureComplete=true`;
- `reachableHelperClosureComplete=false`;
- `runtimeImplementationWired=false`.

The exact-dispatch flag is bounded to the dispatch forms above. It does not upgrade the general helper closure flag.

## Regression coverage

New unrelated-namespace regression exercises one source entity through:

1. entity method -> static helper -> private `INVOKESPECIAL` helper -> DataWatcher write;
2. entity method -> static helper -> `final` `INVOKEVIRTUAL` helper -> DataWatcher read;
3. entity method -> static helper -> overridable `INVOKEVIRTUAL` helper containing a dynamic watcher index.

The first two operations must be inventoried with their actual helper owner/method provenance. The third must remain outside the bounded exact-dispatch closure instead of being unsafely followed or globally scanned.

Existing regressions continue to prove that a dynamic index inside a reachable exact/static helper closes the entity rule.

## Bamboo boundary

This remains generic infrastructure. The checksum-pinned Bamboo binary is still external to normal CI, so this slice does not invent exact Bamboo entity coverage.

The next Entity milestone should move from source proof IR toward a first bounded modern runtime: materialize a generic entity definition/synchronized-data plan only for registrations whose watcher schema and access surface both satisfy the completed proof gates, while leaving AI, projectile, NBT, renderer, networking, and unresolved dispatch families fail-closed. The Bamboo candidate remains `PARTIAL`.
