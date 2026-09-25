# 2026-09-17 — Plain Entity velocity-tracking admission boundary

## Scope

This slice follows converter revision `2026-09-17.68` and tightens the first admitted entity family before any modern EntityType is generated.

Converter revision: `2026-09-17.69`.

## Legacy registration fact

Forge/FML 1.7 `registerModEntity` carries a boolean velocity-update flag. The generic lifecycle/DataWatcher proof already preserves this value as `velocityUpdates` on each entity runtime-plan rule.

Minecraft 1.21.11 `EntityType` exposes `trackDeltas()` / `alwaysUpdateVelocity()` as a type property, together with client tracking range and update interval. The currently admitted modern builder surface used by this project has no source-proven setter that safely expresses the legacy `velocityUpdates=false` case.

The converter therefore must not silently collapse legacy true/false into one modern behavior.

## Admission rule

`PLAIN_ENTITY_SYNCHED_DATA_ONLY` now additionally requires:

- `velocityUpdates=true`.

A legacy registration with `velocityUpdates=false` receives the explicit blocker:

- `legacy-velocity-updates-disabled`

This is conservative: the rule does not claim that false can never be mapped; it only keeps that case outside the first runtime family until its modern network-tracking semantics are proven and implemented.

The admission sidecar also copies the preserved `velocityUpdates` value into every evaluated rule so the decision remains auditable.

## Regression coverage

`LegacyEntityRuntimeAdmissionPassTest` now proves:

- the previously safe plain-Entity fixture with `velocityUpdates=true` remains admitted;
- the same otherwise-safe fixture with `velocityUpdates=false` is blocked;
- the blocker is exactly `legacy-velocity-updates-disabled`;
- watcher-closure, behavior, and construction blockers continue to operate independently.

## Runtime boundary

This does not yet generate an EntityType. It closes a semantic ambiguity before code generation, so the upcoming runtime slice can assume that every admitted first-family rule requires the modern delta/velocity-tracking behavior rather than inventing a false-case approximation.

The Bamboo candidate remains `PARTIAL`.
