# Session 2026-09-17 — Plain Entity empty AdditionalSpawnData admission

Checkpoint target: converter revision `2026-09-17.89`.

## Scope

This slice admits the zero-payload subset of Forge 1.7.10 `IEntityAdditionalSpawnData` without adding any payload interpreter.

The relevant legacy Entity callbacks are:

- `writeSpawnData(ByteBuf)`
- `readSpawnData(ByteBuf)`

A plain Entity may pass this gate only when the effective read/write callbacks are both present and both are exact trivial no-ops. The behavior inventory must also show every source-owned method classified as either spawn-data callback to be trivial, so a hidden non-empty base implementation cannot be ignored.

## Wire proof

The existing `FmlRuntimeCodec.parseSimpleEntitySpawn()` consumes:

1. the stable FML EntitySpawn header;
2. the legacy DataWatcher stream through its `127` terminator;
3. the legacy `throwerId` integer;
4. all remaining bytes as `additionalSpawnBytes`.

`SimpleEntitySpawn.plainNonThrowable()` already requires both:

- `throwerId == 0`;
- `additionalSpawnBytes == 0`.

`FmlRuntimeClient` rejects a matched plain-entity rule whenever either condition fails before constructing the modern entity. Existing codec regression also proves a trailing byte produces `additionalSpawnBytes=1` and fails `plainNonThrowable()`.

Therefore this slice does not relax the wire boundary. It only proves that a source entity whose write callback is an exact no-op is semantically compatible with the already-required zero-byte packet tail, and that an exact no-op read callback requires no client-side reconstruction step.

## Admission rule

`LegacyEntityRuntimeAdmissionPass` now records:

- `emptyAdditionalSpawnDataAdmissionWired=true` at the root;
- `emptyAdditionalSpawnDataPairProven=true` on an admitted paired-no-op rule;
- `additionalSpawnDataMode=EMPTY`;
- `admittedEmptyAdditionalSpawnDataPairs` aggregate count.

The pair proof requires:

- exactly one effective `WRITE_SPAWN_DATA` callback;
- exactly one effective `READ_SPAWN_DATA` callback;
- both callbacks resolve to source methods marked `trivialNoOp=true`;
- every source method classified as either spawn-data callback is also trivial.

One-sided callbacks, non-trivial methods, malformed callback data, or actual wire payload remain blocked.

## Deliberate exclusions

This is not general `IEntityAdditionalSpawnData` support. Any source callback that reads or writes even one byte remains outside the plain family. Throwable spawn data, post-init DataWatcher mutation, local legacy `World.spawnEntityInWorld` caller migration, and complex Entity behavior also remain blocked.
