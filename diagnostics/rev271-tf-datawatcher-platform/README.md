# rev271 Twilight Forest Part 2D — inherited vanilla Ghast watcher schema

Date: 2026-10-07

## Exact corpus blocker after rev270

After reference-valued String writes were fixed, exact-corpus access proof reached 74/77 registered
entities. The only three rejected registrations were:

- EntityTFMiniGhast
- EntityTFTowerGhast
- EntityTFUrGhast

All three failures reduce to DataWatcher index 16 inherited from vanilla Minecraft 1.7.10
`EntityGhast`.

Twilight Forest does not declare index 16 in its own entityInit methods, so treating it as a
source-owned TF entry would be incorrect.

## Pinned platform proof

Vanilla Minecraft 1.7.10 `EntityGhast.entityInit()` defines:

- index 16
- Byte
- default 0

and vanilla Ghast runtime reads/updates the same index for attack state.

## Generic implementation

`LegacyVanillaEntityDataWatcher1710` is a narrow pinned platform table.

The DataWatcher definition analyzer now:

1. walks the readable source-mod inheritance chain;
2. identifies the first external vanilla base;
3. imports only entries pinned for that exact external base;
4. merges them with source-owned definitions;
5. runs the existing duplicate-index/type/range validation.

The first admitted platform entry is only:

`net/minecraft/entity/monster/EntityGhast -> index 16, byte, default 0`.

No mod class/name/registry selector is used.

Because platform entries flow through the ordinary DataWatcher sidecar/runtime-plan/codegen stages,
the existing generated watcher bridge receives a normal modern SynchedEntityData accessor for
legacy index 16. A special Ghast runtime subclass is not required merely to transport this metadata.

Synthetic regressions cover both definition inheritance and read/write access through the inherited
index.

## Remaining Part 2D boundary

Once the exact corpus is rerun, the expected entity access result is 77/77. The remaining
source-wide closure exception should then be the two direct vanilla EntityLivingBase watcher reads
in `TFClientEvents.renderLivingPost` (legacy indices 7 and 8). Those are platform-owned event-side
reads and are intentionally deferred to the next small checkpoint.
