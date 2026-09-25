# 2026-09-17 — Plain Entity runtime candidate plan and no-op renderer adapter

## Scope

This slice follows converter revision `2026-09-17.74`. The generated plain Entity classes now carry source-equivalent base hurt behavior, and the presentation analyzer can prove exact empty legacy renderers. This checkpoint joins those facts without yet mutating the modern entity/client registries.

Converter revision: `2026-09-17.75`.

## Modern no-op renderer adapter

`ConvertedLegacyNoOpEntityRenderer` is a shared LFB client adapter for legacy entity renderers whose effective source `doRender` callback was proven to contain no executable behavior beyond `RETURN`.

Modern 1.21.11 `EntityRenderer` has renderer-level presentation paths that exist outside a subclass model submission, so an empty `submit()` alone is not sufficient. The adapter therefore:

- extends `EntityRenderer<Entity, EntityRenderState>`;
- uses a plain `EntityRenderState`;
- calls base `extractRenderState` so `entityType` and core dispatcher state remain valid;
- clears `displayFireAnimation` after extraction;
- clears name-tag and leash state;
- forces `shadowRadius=0` and clears shadow pieces;
- overrides `finalizeRenderState` without calling the base shadow extractor;
- implements `submit()` as an exact no-op.

This prevents the dispatcher from reintroducing fire/shadow/name/leash presentation around an otherwise empty legacy renderer.

## Runtime candidate join

`LegacyPlainEntityRuntimeCandidatePass` writes:

`legacyforgebridge/plain-entity-runtime-candidates.json`

It joins:

- `entity-generated-classes.json`;
- `entity-presentation-surface.json`.

A rule is `runtimeCandidateReady=true` only when:

- the isolated Java 21 entity class was generated;
- vanilla legacy base hurt semantics were mapped;
- generated class identity is present;
- width/height are positive;
- tracking range and update frequency are positive;
- legacy `velocityUpdates=true`;
- exactly the admitted source presentation path is proven as a no-op renderer.

Ready rules record `presentationAdapter=NOOP_RENDERER` and preserve the generated class, dimensions, tracking/update metadata, watcher-accessor count, and source renderer provenance.

## Still intentionally closed

This checkpoint explicitly keeps:

- `entityTypeRegistrationWired=false`;
- `clientRendererRegistrationWired=false`;
- `runtimeImplementationWired=false`.

The next slice may register EntityTypes and their no-op client renderers, but it must consume only `runtimeCandidateReady=true` rules from this joined sidecar. It must not reopen blocked entity families or rediscover source facts at runtime.
