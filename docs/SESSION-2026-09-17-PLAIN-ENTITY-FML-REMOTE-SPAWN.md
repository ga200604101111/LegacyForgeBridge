# Session 2026-09-17 — Plain Entity FML Remote Spawn

Checkpoint: converter revision `2026-09-17.79`.

## Scope

This slice extends the first admitted `PLAIN_ENTITY_SYNCHED_DATA_ONLY` family from local `EntityType`/renderer registration to Forge 1.7.10 FML remote entity spawning.

## Runtime boundary

- The executable runtime rule now carries the exact legacy `modId` and `registerModEntity` numeric id.
- `LegacyPlainEntityRegistry` indexes only rules that explicitly prove the watcher bridge and remote-spawn runtime boundary. Older core-only plain-entity sidecars remain loadable, but are not exposed through the remote-spawn lookup.
- `FmlRuntimeCodec` retains typed legacy DataWatcher values for byte, short, int, float, string, and coordinates. ItemStack watcher entries remain rejected by this bounded family.
- Generated plain Entity subclasses implement `LegacyPlainEntityWatcherBridge`, validating the legacy source index and wire type before writing the corresponding modern `SynchedEntityData` accessor.
- The client FML spawn bridge accepts only non-throwable packets with no additional spawn bytes. Legacy `Entity` base watcher ids 0 and 1 are accepted only at their 1.7.10 defaults (`flags=0`, `air=300`). Every other watcher must be consumed by the generated source-owned watcher bridge; otherwise spawning fails closed before insertion into the client world.

## Regression coverage

- Candidate/runtime sidecars require and preserve legacy numeric identity plus watcher-bridge readiness.
- Runtime registry tests cover remote-field gating and compatibility with pre-remote core rules.
- FML codec tests verify typed watcher retention.
- FML client bytecode tests verify remote lookup, strict envelope parsing, generated entity creation, watcher bridge dispatch, and `ClientLevel.addEntity` insertion.
- Codegen regression verifies the generated class implements the watcher bridge and calls modern `SynchedEntityData.set`.
