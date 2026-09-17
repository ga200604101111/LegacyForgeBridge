# Session 2026-09-17 — Plain Entity post-spawn metadata mutation gate

Checkpoint target: converter revision `2026-09-17.80`.

## Why this gate is necessary

The `.79` runtime can reconstruct an admitted Forge 1.7.10 FML `EntitySpawn` envelope, instantiate the generated modern Entity, and apply the spawn packet's primitive/string DataWatcher values through a typed generated bridge.

That does **not** prove later DataWatcher mutations are synchronized. In the pinned ViaFabricPlus/ViaLegacy protocol path, the 1.7.10 `SET_ENTITY_DATA` handler checks ViaLegacy's own entity tracker and cancels metadata packets for unknown entity ids. FML-spawned converted entities are created by LegacyForgeBridge from the custom `FML` payload, so they do not have a normal vanilla spawn packet that would populate ViaLegacy's tracker as a known vanilla entity type.

Passing such metadata through as an arbitrary vanilla entity is also not equivalent: source-owned legacy watcher indexes are mapped to generated modern `SynchedEntityData` accessors, not to a vanilla 1.8 entity metadata schema.

## `.80` boundary

The first executable `PLAIN_ENTITY_SYNCHED_DATA_ONLY` family is therefore narrowed to **post-init watcher-write-free** entities.

`LegacyEntityRuntimePlanPass` now materializes:

- `sourceOwnedDataWatcherReadCount`
- `sourceOwnedDataWatcherWriteCount`
- `postInitSourceDataWatcherMutationFree`
- explicit blocker `post-init-datawatcher-writes-require-runtime-sync` when any proven source-owned `DataWatcher.updateObject` write exists outside `entityInit`

`LegacyEntityRuntimeAdmissionPass` requires both the explicit mutation-free proof and an exact zero write count. Missing proof fails closed.

## Effect

- `.79` FML remote spawn and initial typed watcher application remain unchanged.
- Plain entities with immutable source-owned watcher state can remain runtime-complete.
- Plain entities with post-init watcher writes are no longer allowed to claim runtime completeness until a dedicated raw legacy metadata synchronization bridge is implemented and proven.
- No brittle ViaLegacy mixin or fake vanilla entity-type tracking is introduced in this slice.
