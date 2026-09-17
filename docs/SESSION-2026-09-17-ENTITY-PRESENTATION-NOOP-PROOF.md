# 2026-09-17 — Entity presentation inventory and no-op renderer proof

## Scope

This slice follows converter revision `2026-09-17.71`. Generated plain Entity subclasses now exist, but registering them as real modern EntityTypes is still unsafe until the client presentation surface is understood.

Converter revision: `2026-09-17.72`.

## Source-wide renderer registration inventory

Minecraft Forge 1.7.10 exposes:

`RenderingRegistry.registerEntityRenderingHandler(Class<? extends Entity>, Render)`

`LegacyEntityPresentationAnalyzer` scans every source method for that exact registration call instead of requiring the call to be reachable from a common FML lifecycle root. This is intentional: old mods frequently place entity renderer registration in client proxy methods reached through sided-proxy dispatch that is not statically recoverable from the common proxy callsite.

For each direct registration, the analyzer conservatively proves:

- entity class identity from a class literal;
- renderer implementation identity from a directly constructed source object;
- whether the renderer class is present in the source JAR;
- the effective source-owned `doRender` / `func_76986_a` override;
- whether that override is an exact no-op.

## Exact no-op renderer proof

A renderer is considered no-op only when the effective non-synthetic render callback has exactly one executable bytecode instruction: `RETURN`.

Any load, store, field access, call, branch, constant operation, or other executable instruction makes the no-op proof false. Synthetic/bridge dispatch methods are ignored when locating the source render implementation.

This is deliberately narrower than general renderer conversion. It targets invisible/helper entities whose legacy renderer intentionally draws nothing.

## Materialized IR

`legacyforgebridge/entity-presentation-surface.json` records, per watcher-proven entity registration:

- source renderer registration count;
- each renderer implementation class and registration callsite;
- renderer class presence;
- exact no-op render proof and source render method;
- presentation blockers.

Current blocker states include:

- `source-entity-renderer-registration-missing`;
- `ambiguous-source-entity-renderer-registration`;
- `source-renderer-class-missing`;
- `source-renderer-not-proven-noop`;
- `modern-noop-renderer-runtime-not-materialized`.

The sidecar keeps `presentationRuntimeWired=false` and `presentationRuntimeReady=false`. A proven source no-op renderer is evidence for the next runtime gate, not permission to register EntityTypes yet.

## Next gate

The first real EntityType registration slice may consume only entities that have all of the following:

- `PLAIN_ENTITY_SYNCHED_DATA_ONLY` admission;
- generated isolated modern Entity class;
- proven source no-op renderer;
- a generated/registered modern no-op client renderer.

Visible or otherwise active legacy renderers remain outside this first runtime family until their presentation semantics receive their own conversion proof.
