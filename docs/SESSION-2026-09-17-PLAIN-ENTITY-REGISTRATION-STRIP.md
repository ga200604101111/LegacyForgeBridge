# Session 2026-09-17 — Plain Entity legacy registration stripping

Checkpoint target: converter revision `2026-09-17.82`.

## Goal

The `.81` retirement-readiness pass showed that a runtime-complete modern replacement is not enough while source bootstrap bytecode still contains legacy registration references. This slice removes the common-side registration edge for the first admitted plain Entity family without broad bytecode surgery.

## Exact accepted callsite

`LegacyEntityRegistrationStripper` only accepts the standard Forge 1.7.10 call:

`EntityRegistry.registerModEntity(Class, String, int, Object, int, int, boolean)`

The call must be in the exact source owner/method/descriptor proven by `LegacyLifecycleAnalyzer`, and the seven arguments immediately preceding the call must be category-1 pure producers with no label/frame boundary inside the slice.

The following values must exactly equal the executable modern runtime rule:

- source Entity class literal;
- legacy registry name;
- legacy numeric entity id;
- tracking range;
- update frequency;
- velocity-updates flag.

The mod-owner argument is accepted only as a pure `ALOAD`, `ACONST_NULL`, or same-owner object `GETSTATIC`. Calculated arguments, helper calls, field chains, control-flow joins, or ambiguous duplicate callsites are left untouched.

## Pass integration

`LegacyPlainEntityRegistrationStripPass` runs after the entity instantiation inventory and after the modern plain Entity runtime rule exists. It rewrites only the staged source class; the source JAR remains immutable.

Output:

`legacyforgebridge/plain-entity-registration-strip.json`

The pass records exact lifecycle source identity, stripped site count, and fail-closed blockers. The later `.81` current-candidate reference scan therefore sees the rewritten candidate bytes and can remove the corresponding bootstrap-to-Entity incoming reference when stripping succeeds.

## Deliberate non-goals

This slice does not remove:

- direct `new LegacyEntity(...)` / `World.spawnEntityInWorld(...)` sites;
- client `RenderingRegistry.registerEntityRenderingHandler(...)` sites;
- the legacy Entity class itself;
- the legacy renderer class itself.

Client renderer registration requires a separate constructor-side-effect proof before the `new Render(...)` producer chain can be removed safely. Source-class deletion remains unauthorized.
