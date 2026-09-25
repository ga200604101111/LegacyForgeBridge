# Session 2026-09-17 — Plain Entity constant targeting margin

Checkpoint target: converter revision `2026-09-17.88`.

## Scope

This slice extends the constant Entity override framework from booleans to one exact float mapping:

- legacy 1.7.10 `Entity.getCollisionBorderSize()F`
- modern 1.21.11 `Entity.getPickRadius()F` (Yarn: `getTargetingMargin`)

In 1.7.10 the mouse/entity ray selection path expands an entity bounding box by `getCollisionBorderSize()` after checking `canBeCollidedWith()`. Modern Entity exposes the targeting-margin value as `getPickRadius()`. Physical entity collision methods remain separate and are not mapped here.

## Source proof

`LegacyEntityConstantOverrideAnalyzer` now has a typed `FloatProof` in addition to `BooleanProof`.

The first admitted float family requires source descriptor `()F` and exactly two executable instructions:

- `FCONST_0`, `FCONST_1`, `FCONST_2`, or `LDC <finite Float>`
- `FRETURN`

Field reads, arithmetic, helpers, branches, non-finite values and extra executable instructions remain unproven.

## Typed constant IR

Boolean mappings retain the existing `constantBoolean` field for backward compatibility and now also emit `constantKind=boolean` from newly generated sidecars.

The targeting-margin mapping emits:

- `sourceKind=COLLISION_BORDER_SIZE`
- `targetMethod=getPickRadius`
- `targetDescriptor=()F`
- `mappingSemantics=PICK_RADIUS_FLOAT_IDENTITY`
- `constantKind=float`
- `constantFloat=<finite source value>`

## Admission and codegen

Admission validates exact source identity, modern target/mapping identity, proof completion, runtime-codegen readiness and typed constant value. Float mappings require an explicit finite `constantFloat`; a boolean field cannot accidentally satisfy the float gate.

`LegacyPlainEntityConstantOverrideCodegenPass` now emits typed constant returns:

- boolean -> `ICONST_0/1; IRETURN`
- float -> `LDC <Float>; FRETURN`

Existing boolean sidecar fixtures without `constantKind` remain accepted for compatibility, while float mappings require `constantKind=float`.

## Fail-closed boundary

This slice preserves only constant targeting margin. Dynamic border calculations, bounding-box overrides, physical collision callbacks, mutable watcher behavior and legacy local spawn callers remain outside the admitted family.
