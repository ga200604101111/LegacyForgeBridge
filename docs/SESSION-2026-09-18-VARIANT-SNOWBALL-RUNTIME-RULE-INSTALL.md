# 2026-09-18 — Variant snowball pre-registration runtime rule installation

## Scope

This slice follows converter revision 2026-09-17.111. Launch and impact semantics are already source-complete in schema-2 runtime candidates; this change creates the first runtime-owned boundary without pretending that projectile gameplay is implemented.

Converter revision: 2026-09-18.112.

## Runtime sidecar

LegacyVariantSnowballRuntimePass promotes only source-complete schema-2 candidates into:

legacyforgebridge/variant-snowball-runtime-rules.json

The pass revalidates:

- exact generated-mod item/projectile namespace ownership;
- VARIANT_SNOWBALL and THROWN_ITEM adapter contracts;
- source/item-use/impact semantic completeness;
- exact normalized random.bow launch constants;
- creative-consumption and server-authority policy;
- complete normalized variant set;
- absence of any premature runtimeImplementationWired claim.

The emitted root and each rule explicitly keep itemRuntimeWired, projectileRuntimeWired, rendererRuntimeWired and runtimeImplementationWired false.

## Pre-registration runtime registry

LegacyVariantSnowballRuntimeRegistry parses the normalized rule sidecar into strongly typed Rule and Variant records.

The registry:

- rejects malformed or duplicate metadata variants;
- preserves base damage and NONE / POTION / RANDOM_TELEPORT effect identity;
- validates the exact random-teleport constants already proven by the source analyzers;
- rejects malformed launch constants and premature runtime claims;
- installs rules idempotently per modern item id.

GeneratedModSupport.beginMod now loads this registry before generated block/item registration starts. No generic item kind is changed in this slice, and no EntityType or projectile is registered yet.

## Regression

Normal CI covers:

- source-complete launch+impact candidates becoming one pre-registration runtime rule;
- runtime flags remaining explicitly false;
- a candidate sidecar that unexpectedly claims runtime implementation failing closed;
- typed metadata lookup for NONE and RANDOM_TELEPORT variants;
- malformed launch/teleport contracts failing closed;
- GeneratedModSupport.beginMod containing the runtime-registry load hook.

## Next boundary

The next runtime slice can consume this already-installed rule registry to:

1. register the modern projectile EntityType;
2. select a specialized item implementation by exact modern id while leaving generic classification untouched;
3. reproduce server-authoritative launch, creative consumption and legacy metadata carriage;
4. then wire impact effects and thrown-item presentation before opening legacy cohort retirement.
