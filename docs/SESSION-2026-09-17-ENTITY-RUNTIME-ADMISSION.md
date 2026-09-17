# 2026-09-17 — First Entity runtime-family admission gate

## Scope

This slice follows converter revision `2026-09-17.67`. It joins the existing watcher mapping/closure, source-owned behavior inventory, and constructor/size proof into the first explicit entity-family admission decision. It still generates no EntityType or Entity subclass.

Converter revision: `2026-09-17.68`.

## First admitted family

The first deliberately narrow family is:

- `PLAIN_ENTITY_SYNCHED_DATA_ONLY`

An entity is admitted only when every applicable proof gate is already complete and its source behavior fits the minimal generated-runtime shape.

## Admission requirements

Watcher/runtime-plan evidence:

- `synchedDataMappingComplete=true`;
- `sourceWideDataWatcherCallClosureComplete=true`.

Behavior evidence:

- behavior surface exists and is complete;
- first external superclass is exactly `net/minecraft/entity/Entity`;
- there are no unclassified source instance methods;
- the only classified source callback is `ENTITY_INIT`;
- every retained source instance method is the `ENTITY_INIT` method.

Construction evidence:

- first external superclass is exactly `net/minecraft/entity/Entity`;
- `(World)V` constructor exists;
- source constructor chain is exact/complete;
- constructor control flow is simple;
- no source-owned `setSize` override exists;
- physical dimensions are proven;
- `unmappedConstructorEffectCount=0`.

These criteria intentionally exclude living entities, projectiles, vehicles, ticking entities, interaction entities, NBT-bearing entities, and any constructor that performs behavior beyond proven source-constructor delegation, vanilla super construction, and constant size setup.

## Output

`LegacyEntityRuntimeAdmissionPass` writes:

- `legacyforgebridge/entity-runtime-admission.json`
- schema version 1

For every synchronized-data runtime-plan rule it records:

- modern id and legacy/source identity;
- legacy registration tracking/update facts;
- family name;
- `admitted` boolean;
- explicit deduplicated blocker list;
- mapped synchronized-data entries;
- proven width/height when available;
- `runtimeImplementationWired=false`.

Root counters record evaluated, admitted, and blocked registrations.

## Important boundary

`admitted=true` means only that the source evidence fits the first generated-runtime family. It does **not** mean runtime code already exists. `runtimeImplementationWired` remains false until a later pass actually emits/registers the modern EntityType and synchronized-data implementation.

This split is intentional: proof/admission and runtime code generation remain independently testable and fail-closed.

## Regression coverage

`LegacyEntityRuntimeAdmissionPassTest` proves:

- a direct-Entity source with complete watcher closure, only `entityInit`, proven dimensions, and no unmapped constructor effects is admitted;
- a tick callback blocks admission;
- an unmapped constructor effect blocks admission;
- incomplete source-wide DataWatcher closure blocks admission;
- admitted rules retain synchronized-data mapping and dimensions while runtime remains unwired.

## Next gate

With this admission IR in place, the next safe step is to generate a modern runtime **only** for `PLAIN_ENTITY_SYNCHED_DATA_ONLY` rules and verify exact 1.21.11 EntityType/SynchedEntityData bytecode/API shapes. More complex entity families stay blocked until their behavior-specific proof and runtime implementations exist.

The Bamboo candidate remains `PARTIAL`.
