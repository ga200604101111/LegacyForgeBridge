# 2026-09-17 — Entity construction and physical-size proof

## Scope

This slice follows converter revision `2026-09-17.66`. Entity watcher semantics and source-owned behavior surfaces now have explicit proof/inventory IR, but a modern `EntityType` still needs trustworthy construction and dimensions. This step proves a deliberately narrow constructor/size surface without executing legacy entity classes.

Converter revision: `2026-09-17.67`.

## Construction analyzer

`LegacyEntityConstructionAnalyzer` starts only from entity registrations already admitted by the DataWatcher-definition proof.

For each source entity it looks for the canonical legacy constructor:

- `(Lnet/minecraft/world/World;)V`

If present, the analyzer follows exact source-owned constructor delegation through `INVOKESPECIAL <init>` until the chain reaches the first superclass outside the source JAR. It records the complete source constructor chain and retains direct constructor effects rather than assuming the constructor is otherwise inert.

Recorded effects currently include:

- source constructor delegation;
- external superclass constructor boundary;
- writes to fields on `this`;
- non-constructor method calls;
- proven `setSize` calls;
- unproven/dynamic `setSize` calls.

## Conservative size proof

A constant legacy `setSize(float width, float height)` / `func_70105_a(FF)V` result is admitted only when all of the following are true:

- the registered entity has a `(World)V` source constructor;
- the constructor delegation chain is exact and complete;
- every source constructor in the chain has simple control flow (no jumps, switch, or exception handlers);
- the first superclass outside the source JAR is exactly vanilla `net/minecraft/entity/Entity`;
- no source-owned class in the lineage declares/overrides `setSize` / `func_70105_a(FF)V`;
- every encountered size call is made on proven `this`;
- both dimensions are source-proven constants;
- the effective final dimensions are finite and positive.

This intentionally does not yet infer inherited size semantics for `EntityLivingBase`, `EntityThrowable`, vehicles, projectiles, or other external vanilla/Forge entity families.

Constructor delegation executes before the remainder of the current constructor. The analyzer therefore preserves execution order for size effects: parent sizes are applied first and a later child `setSize` correctly becomes the effective dimensions.

## Constant surface

The first slice accepts:

- float LDC constants;
- `FCONST_0/1/2`;
- source `static final float` ConstantValue fields.

Dynamic field values, parameters, arithmetic, helper-returned values, or merged control-flow values remain unproven and close the size proof.

## Output

`LegacyEntityConstructionPass` writes:

- `legacyforgebridge/entity-construction-surface.json`
- schema version 1

Each rule records:

- modern id inherited from the proven entity definition sidecar;
- legacy registry/source identity;
- first external superclass;
- whether a `(World)V` constructor exists;
- source constructor chain and simple-control-flow state;
- source `setSize` override presence;
- `sizeProofComplete` plus width/height when proven;
- explicit `sizeProofReason` when incomplete;
- complete retained constructor effect list;
- `unmappedConstructorEffectCount` for field writes, arbitrary method calls, or unproven size calls;
- `runtimeConstructionReady=false`;
- `runtimeImplementationWired=false`.

A successful dimension proof is therefore not treated as proof that all constructor behavior is translated.

## Regression coverage

`LegacyEntityConstructionAnalyzerTest` proves:

- exact child -> source-parent -> vanilla Entity constructor chaining;
- parent size followed by child size uses the child dimensions as the effective result;
- constant dimensions are recovered without source class loading;
- direct `this` field writes and arbitrary constructor method calls remain inventoried;
- dynamic/non-constant size input closes the size proof while retaining construction evidence.

`LegacyEntityConstructionPassTest` proves sidecar materialization and that an entity may simultaneously have:

- `sizeProofComplete=true`;
- nonzero unmapped constructor effects;
- `runtimeConstructionReady=false`.

This prevents physical-dimension recovery from being mistaken for complete construction equivalence.

## Remaining boundary

The next runtime gate should join synchronized-data closure, behavior-surface inventory, and construction proof into an explicit entity-family admission analysis. Only entities whose external base and all required behavior/construction effects are supported should become candidates for generated `EntityType` + `SynchedEntityData` code. Complex living entities, projectiles, vehicles, custom NBT/network behavior and rendering remain separate fail-closed families.

The Bamboo candidate remains `PARTIAL` until those gates and exact-corpus regressions are complete.
