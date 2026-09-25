# 2026-09-17 — Entity source-wide DataWatcher call closure

## Scope

This slice follows converter revision `2026-09-17.64`. The earlier entity access proof deliberately left general helper dispatch incomplete. Instead of guessing every possible open virtual/interface call target, this gate proves a stronger watcher-specific property directly over the whole source JAR: every direct runtime invocation on legacy `DataWatcher` must already be accounted for by an admitted bounded entity access proof.

Converter revision: `2026-09-17.65`.

## Source-wide closure rule

`LegacyEntityDataWatcherGlobalClosureAnalyzer` loads every source class and scans every method for direct calls whose owner is `net/minecraft/entity/DataWatcher`.

Direct `addObject` / `addObjectByDataType` calls inside `entityInit` / `func_70088_a` are excluded from this runtime-call inventory because they are handled by the independent watcher-definition proof. Every other DataWatcher invocation is a runtime call that must be closed.

For each source method the analyzer compares:

- total direct runtime DataWatcher calls present in bytecode;
- the maximum number of accesses that one admitted entity access rule proves for that exact source owner/method/descriptor;
- whether every direct watcher call uses the currently supported primitive/string getter or `updateObject` surface.

A method is source-wide accounted only when it contains no unsupported watcher call and the bounded proof accounts for every runtime DataWatcher invocation in that method.

Using the per-rule maximum rather than summing rules prevents a helper shared by multiple registered entities from being falsely over-counted.

## What this catches

The closure fails if the source JAR contains, for example:

- a watcher getter/update in an event handler not reached by the bounded entity helper graph;
- an uncalled/global utility that directly manipulates DataWatcher;
- an overridable virtual helper containing a watcher call that bounded dispatch proof could not reach;
- force-dirty, ItemStack getter, definition-outside-entityInit, or another unsupported DataWatcher operation;
- any method where only part of its direct watcher surface was proven.

This turns previously invisible helper/event/global watcher usage into an explicit blocker rather than silently assuming it does not exist.

## Output

`LegacyEntityDataWatcherGlobalClosurePass` writes:

- `legacyforgebridge/entity-datawatcher-global-closure.json`
- schema version 1

The sidecar includes:

- `sourceWideDataWatcherCallClosureComplete`;
- total runtime watcher call count;
- proven runtime watcher call count;
- fully accounted methods;
- unresolved methods with direct/proven counts;
- unsupported call signatures where applicable;
- `runtimeImplementationWired=false`.

## Runtime-plan integration

The generic profile now executes:

1. entity registration/DataWatcher definition proof;
2. bounded lineage/exact-dispatch access proof;
3. source-wide direct DataWatcher call closure;
4. modern synchronized-data mapping plan.

`LegacyEntityRuntimePlanPass` now consumes the source-wide closure sidecar. The older `reachableHelperClosureComplete=false` remains visible because the generic call graph is still intentionally bounded, but a successful source-wide watcher closure supersedes that call-graph gap for the narrower question of whether any direct DataWatcher runtime call was missed.

When source-wide closure is false the runtime plan adds:

- `source-wide-datawatcher-call-closure-incomplete`.

When source-wide closure is true that blocker is absent. `entitytype-syncheddata-runtime-not-materialized` remains, so runtime admission is still false in either case.

## Regression coverage

`LegacyEntityDataWatcherGlobalClosureAnalyzerTest` proves both sides:

- a registered entity whose two runtime watcher calls are both admitted -> source-wide closure complete;
- the same source plus an unrelated, uncalled utility with one extra watcher read -> closure incomplete, with that utility reported as unresolved and only the two admitted calls counted as proven.

`LegacyEntityRuntimePlanPassTest` additionally proves that:

- a completed source-wide closure removes the watcher-closure blocker without opening runtime admission;
- an incomplete source-wide closure remains an explicit plan blocker;
- serializer/type mapping and definition/access mismatch fail-closed behavior are unchanged.

## Remaining boundary

This gate proves completeness of direct source-JAR DataWatcher runtime calls, not complete entity gameplay semantics. Reflection/invokedynamic behavior is not inferred here, and AI, movement/physics, projectile behavior, construction/spawning, NBT, networking, event behavior and rendering remain independent conversion gates.

The next safe milestone is to use this closure plus the synchronized-data mapping plan to define a deliberately narrow generated entity runtime family whose non-watcher behavior is separately proven. The Bamboo candidate remains `PARTIAL` until those runtime/behavior gates and exact-corpus regressions are complete.
