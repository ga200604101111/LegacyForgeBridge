# 2026-09-17 — Variant snowball runtime-candidate admission

## Scope

This slice follows converter revision `2026-09-17.109`, where metadata-indexed variant snowball source semantics became complete through the common impact shell, selector effects and bounded random-teleport presentation.

Converter revision: `2026-09-17.110`.

The purpose of this slice is to cross the boundary from reverse-engineered proof IR into a normalized runtime-candidate IR without yet claiming that the modern projectile/item implementation exists.

## Admission

`LegacyVariantSnowballRuntimeCandidatePass` consumes only the schema-9 `variant-snowball-proof.json` emitted from the same source hash. A family is admitted only when all of these are true:

- metadata lookup and metadata-to-selector binding are complete;
- selector storage and enum constants are complete;
- the selector-independent impact shell is complete;
- selector dispatch/effect edges are complete;
- selector-specific impact semantics are complete;
- aggregate impact semantics are complete;
- the proof sidecar itself still correctly reports that runtime is not wired.

The family is then bound back to exactly one converted manifest item by both `legacyRegistryName` and `sourceItemClass`. This avoids relying on the current generic item classifier, which does not special-case legacy `ItemSnowball` subclasses.

## Normalized runtime IR

The emitted `legacyforgebridge/variant-snowball-runtime-candidates.json` uses schema 1 and contains only modern runtime inputs:

- modern item id;
- deterministic projectile id `<item-path>_projectile` in the same namespace;
- source item/projectile identities for later retirement checks;
- `VARIANT_SNOWBALL` adapter and `THROWN_ITEM` renderer adapter;
- unique legacy metadata values;
- bounded non-negative base damage;
- normalized effects: `NONE`, `POTION`, or `RANDOM_TELEPORT`.

Potion candidates are restricted to the three source-proven effect identities currently admitted by the analyzer: poison, confusion and regeneration, with non-negative constant duration/amplifier.

A random-teleport candidate is admitted only if the proof sidecar contains the complete bounded chain and exact constants: horizontal radius 16, vertical `nextInt(8)-4`, 128 portal particles, `mob.endermen.portal`, exact rollback/safety facts and complete portal presentation.

## Fail-closed behavior

An incomplete family is written to `rejected` instead of being approximated. Normal CI covers:

- a proof-complete potion family becoming a runtime candidate;
- a proof-complete random-teleport family preserving its exact normalized constants;
- a gameplay-safe teleport without portal presentation being rejected because source impact semantics are not complete.

The candidate file explicitly reports `runtimeImplementationWired=false` and `projectileRuntimeWired=false`.

## Next boundary

The next slice can implement the modern generic runtime against this normalized IR rather than the legacy bytecode proof format:

1. load candidate rules before generated item registration;
2. register one modern throwable-item projectile EntityType per admitted family;
3. instantiate a specialized snowball item only when an admitted runtime rule exists;
4. preserve metadata selection on use/spawn;
5. compile base damage, potion effects, random teleport and portal presentation;
6. wire a thrown-item renderer;
7. only then promote the runtime rule and consider retiring the old item/projectile implementation classes.
