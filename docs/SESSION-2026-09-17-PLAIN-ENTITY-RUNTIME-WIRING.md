# 2026-09-17 — Plain Entity runtime wiring

## Scope

This slice follows converter revision `2026-09-17.75`. The converter already had isolated generated Java 21 Entity subclasses plus a fail-closed runtime-candidate join requiring source-proven no-op presentation. This checkpoint wires only those ready candidates into modern EntityType registration and client renderer registration.

Converter revision: `2026-09-17.76`.

## Runtime-rule promotion

`LegacyPlainEntityRuntimePass` consumes only:

`legacyforgebridge/plain-entity-runtime-candidates.json`

Rules with `runtimeCandidateReady=false` are never promoted. Ready rules are revalidated and written to:

`legacyforgebridge/plain-entity-runtime-rules.json`

The runtime sidecar records:

- generated entity class identity;
- proven width / height;
- legacy tracking range in blocks;
- mapped modern tracking range in chunks;
- update frequency;
- velocity update proof;
- no-op presentation adapter;
- EntityType registration wiring;
- client renderer wiring;
- runtime-complete status for this deliberately narrow family.

## Tracking-range unit mapping

Legacy Forge `registerModEntity(..., trackingRange, updateFrequency, sendsVelocityUpdates)` expresses tracking range in blocks, while modern vanilla `EntityType.Builder.clientTrackingRange(...)` uses chunks.

The runtime plan therefore maps:

`modernClientTrackingRangeChunks = ceil(legacyTrackingRangeBlocks / 16)`

Examples:

- 1 block -> 1 chunk;
- 16 blocks -> 1 chunk;
- 17 blocks -> 2 chunks;
- 80 blocks -> 5 chunks.

The original block value remains in IR so the conversion is auditable.

## Common runtime registration

`LegacyPlainEntityRegistry` loads only the runtime-rule sidecar from the converted mod container.

For every accepted rule it:

- requires the rule namespace to match the converted mod;
- rejects pre-existing EntityType ID collisions;
- loads the generated class and proves it is an `Entity` subclass;
- requires the generated `(EntityType, Level)` constructor;
- creates a `MobCategory.MISC` EntityType factory;
- applies proven dimensions;
- applies mapped chunk tracking range;
- applies the proven update interval;
- registers the EntityType in `BuiltInRegistries.ENTITY_TYPE`.

Reflection is limited to resolving and invoking the converter-generated constructor. No legacy source class is loaded or executed.

The generated Fabric entrypoint invokes this common registry before generated content initialization.

## Client presentation registration

`ConvertedPlainEntityPresentationRuntime` runs only from the generated client initializer. It obtains already-registered EntityTypes from `LegacyPlainEntityRegistry` and binds each one to `ConvertedLegacyNoOpEntityRenderer`.

This keeps all client rendering classes out of the common/server registration path.

## Fail-closed boundaries

The runtime still excludes:

- entities whose candidate sidecar is blocked;
- custom/non-no-op legacy renderers;
- custom Entity callbacks outside the admitted plain family;
- legacy `velocityUpdates=false` registrations;
- malformed generated class identities or constructors;
- registry ID collisions;
- other Entity base families such as LivingEntity/projectiles/vehicles.

This slice wires only the already-proven `PLAIN_ENTITY_SYNCHED_DATA_ONLY + NOOP_RENDERER` family; it does not broaden admission.
