# 2026-09-17 — Generated plain Entity subclasses

## Scope

This slice follows converter revision `2026-09-17.70` and materializes the first modern class artifact for rules admitted to `PLAIN_ENTITY_SYNCHED_DATA_ONLY`.

Converter revision: `2026-09-17.71`.

## Why one generated class per entity rule

Modern `SynchedEntityData.defineId` allocates accessor ids through a class-tree registry. Reusing one generic runtime Entity class for unrelated legacy watcher schemas would therefore mix their accessor declarations in one class identity and make schema isolation unsafe.

`LegacyPlainEntityCodegenPass` instead emits one Java 21 subclass per admitted rule under the converted mod's generated package:

`dev.yinghuang.legacyforgebridge.generated.<mod>.entity.PlainEntity_<safe-id>_<hash>`

Every generated class owns its own static `EntityDataAccessor` fields and therefore its own modern synchronized-data declaration surface.

## Generated class contract

For each admitted rule the generated class:

- extends modern `net.minecraft.world.entity.Entity`;
- exposes the modern `(EntityType, Level)` constructor and delegates directly to `Entity`;
- creates one static accessor per mapped legacy watcher entry with `SynchedEntityData.defineId`;
- maps `BYTE`, `INT`, `FLOAT`, and `STRING` to the exact modern `EntityDataSerializers` constants;
- represents a proven legacy `short` through its already-admitted lossless `signed_short_widen -> INT` mapping;
- defines every mapped default in `defineSynchedData`;
- uses empty `readAdditionalSaveData` / `addAdditionalSaveData` callbacks, matching the no-op NBT admission proof;
- provides a conservative `hurtServer(...)=false` implementation required by modern abstract `Entity`.

The legacy watcher numeric index is retained only in the generated field name/IR provenance. It is not forced into the modern accessor id; modern accessor ids remain owned by the class-tree allocation contract.

## Output IR

`legacyforgebridge/entity-generated-classes.json` records the generated binary/internal class identity and registration metadata for the later `EntityType` stage.

This checkpoint deliberately states:

- `runtimeClassGenerationWired=true`;
- `entityTypeRegistrationWired=false`;
- `runtimeImplementationWired=false`.

No EntityType is registered and no client renderer is installed in this slice.

## Validation

Regression coverage reads the emitted class bytes through ASM and verifies:

- Java 21 class version;
- direct modern `Entity` superclass;
- one accessor field and one `defineId` call per watcher entry;
- serializer selection including two distinct `INT` accessors for source `short` and source `int`;
- one `Builder.define` call per mapped default;
- required modern abstract method implementations;
- blocked admission rules emit no generated entity class.

The next runtime gate is `EntityType` registration plus a proof-safe client presentation strategy. Registration must consume only this generated-class sidecar and must not reopen blocked entity families.
