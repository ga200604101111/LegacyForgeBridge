# 2026-10-07 — Twilight Forest 2.3.8 compatibility, Part 2E checkpoint

Branch: `feature/generic-conversion-iyamato-corpus3`

Corpus:
`twilightforest-1.7.10-2.3.8-tw.jar`

SHA-256:
`1aa2c191170ff707499c42cd58bdcc69f2772b59014cb684ba99a6d1e6540589`

Scope: inherited vanilla Minecraft 1.7.10 DataWatcher schema and initial Forge/FML remote-spawn
watcher-envelope proof.

This checkpoint does **not** claim that all 77 Twilight Forest entities are admitted executable
modern runtimes. Behavior, construction, rendering, AI and specialized entity-family support remain
separate gates.

## 2E-1 — exact external vanilla base census

The 77 source-proven registrations use 18 first external vanilla base families:

- EntityMob: 32
- EntityThrowable: 14
- EntitySpider: 4
- EntityAnimal: 4
- EntityWolf: 3
- EntityGhast: 3
- Entity: 3
- EntityCreature: 2
- EntityFlying: 2
- EntityLiving: 2
- EntityPig: 1
- EntitySheep: 1
- EntityCow: 1
- EntitySlime: 1
- EntityZombie: 1
- EntityAmbientCreature: 1
- EntityTameable: 1
- EntityArrow: 1

Checkpoint commit:

`f37ff1a41bd415a78a8defe76889fd8352881f8e`

## 2E-2 — pinned transitive vanilla 1.7.10 watcher schema

Vanilla 1.7.10 source was checked against the Bukkit mc-dev 1.7.10 NMS source and mapped to the MCP
class families used by the converter.

Pinned watcher owners:

- EntityLivingBase: 6 float=1.0, 7 int=0, 8 byte=0, 9 byte=0
- EntityLiving: 10 string="", 11 byte=0
- EntityAgeable: 12 int=0
- EntityTameable: 16 byte=0, 17 string=""
- EntityWolf: 18 float=1.0, 19 byte=0, 20 byte=14
- EntityPig: 16 byte=0
- EntitySheep: 16 byte=0
- EntitySpider: 16 byte=0
- EntitySlime: 16 byte=1
- EntityGhast: 16 byte=0
- EntityZombie: 12 byte=0, 13 byte=0, 14 byte=0
- EntityArrow: 16 byte=0

Intermediary classes such as EntityCreature, EntityMob, EntityFlying, EntityAnimal, EntityCow,
EntityAmbientCreature and EntityThrowable add no watcher entry and inherit transitively.

Legacy Entity base watcher 0/1 are deliberately **not** duplicated into generated LFB accessors.
The existing FML runtime handles:

- 0 / wire type 0 / Byte flags
- 1 / wire type 1 / Short air

as platform-owned Entity base fields.

Commit:

`5ed7dc8a5bcbb253d68db73c88200f4c83251446`

## 2E-3 — exact platform/source merge

Exact-corpus merge audit:

- registered entities: 77
- platform watcher entry instances, excluding Entity 0/1: 396
- source-owned watcher entry instances: 37
- platform/source index conflicts: 0
- total non-0/1 bridge entry instances: 433

Audit:
`/mnt/data/tf_part2e3_audit.json`

Runtime-family audit:

- plain generated entities consume the complete DataWatcher analyzer schema;
- visible-entity analysis also consumes the same complete analyzer schema through
  `sourceWatcherTypes`;
- EntityThrowable contributes no extra watcher beyond Entity 0/1;
- EntityArrow contributes only 16/byte, already represented by the projectile base-watcher path.

An unknown first external Entity base now fails closed rather than being treated as an empty vanilla
schema. This prevents a foreign dependency or an unpinned vanilla class from silently losing
inherited metadata.

Commit:

`17c8e99b1ccc051621e28e655a29630ec3f49483`

## 2E-4 — proof preservation through executable runtime

The watcher-envelope proof is now explicit and carried through the full plain-entity pipeline.

### Definition sidecar

`LegacyEntityDataWatcherPass` writes per entity:

- `entityBaseWatchersHandledExternally=true`
- `initialFmlWatcherEnvelopeComplete=true`
- `platformWatcherEntryCount`
- `sourceWatcherEntryCount`
- `nonBaseWatcherBridgeEntryCount`

Each entry records:

- `declaredBy`
- `platformOwned`
- `ownership = platform|source`

Commit:
`c4ffcbc0587b6e91d25ccfec14a117d084dc667e`

### Runtime plan

`LegacyEntityRuntimePlanPass` requires and validates:

- the initial-envelope proof marker;
- the Entity-0/1 external handler marker;
- ownership provenance on every entry;
- platform/source counts;
- `platform + source == nonBase == dataWatcherEntries.size()`.

It carries the same ownership metadata into `synchedDataEntries`.

Commit:
`522908c5eb2ea9fdedfd1bacebe0e76e694ba9ba`

### Admission

`LegacyEntityRuntimeAdmissionPass` blocks when any of the following is absent/inconsistent:

- `initial-fml-watcher-envelope-incomplete`
- `legacy-entity-base-watcher-handler-missing`
- `initial-fml-watcher-bridge-coverage-mismatch`

Commit:
`763e803cf4b9a0207b8c6b1f3bba590a24b18050`

### Codegen / candidate propagation

Proof/count fields are preserved through generated-class and runtime-candidate sidecars.

Commits:

- `d94a19ce8165fccae5cf3a05985402c275d9222c`
- `fbe9d65436b005c66a8c40c95ba1f231014ba305`

### Final executable runtime gate

`LegacyPlainEntityRuntimePass` requires:

- `initialFmlWatcherEnvelopeComplete=true`
- `entityBaseWatchersHandledExternally=true`
- `synchedDataAccessorCount == nonBaseWatcherBridgeEntryCount`

before it can emit an executable runtime rule.

Successful rules carry:

`initialFmlWatcherEnvelopeRuntimeComplete=true`

Commit:
`230b0dc1bffcfa0921b4095458b8e4a2d94a1000`

## Regression lock

Regression fixtures now cover:

- definition-side ownership and counts;
- RuntimePlan preservation and validation;
- missing envelope proof as an admission blocker;
- proof propagation through codegen/runtime candidates;
- final runtime rejection when generated accessor count does not match the envelope;
- RuntimePlan rejection when persisted watcher counts are deliberately corrupted.

Commits:

- `653429e5866f39fcfe2a5f0cdc51b3c4ac0d3fab`
- `990704b3dd49fa1ea542f52a6c0297bdac6eee80`

These tests are committed as regression source. No GitHub Actions run was triggered, per repository
policy, and no full Gradle/Loom test run is claimed.

## What Part 2E proves

For the exact Twilight Forest corpus, the converter now has a collision-free representation of the
complete **non-Entity-base** legacy watcher schema for every registered entity, including inherited
vanilla state, and the plain runtime pipeline cannot silently discard that proof.

Part 2D remains:

- 77/77 registered source access surfaces proven;
- 94/94 source runtime DataWatcher calls accounted;
- 0 unresolved source methods.

Part 2E adds:

- 396 inherited platform watcher entry instances;
- 37 source-owned watcher entry instances;
- 433 non-0/1 bridge entry instances;
- zero platform/source index conflicts.

## What Part 2E does not prove

The existing `PLAIN_ENTITY_SYNCHED_DATA_ONLY` admission family still deliberately requires
behavior/construction conditions that many Twilight Forest living/mob/animal entities do not meet.
In particular, an entity being watcher-complete does not mean its AI, movement, dimensions,
constructor effects, renderer, interactions or additional spawn data have been converted.

The next checkpoint should audit those blockers rather than weakening the existing gates.

## Next: Part 2F — behavior/construction admission census

Run the existing behavior, construction, constant-override, presentation and additional-spawn-data
proofs against all 77 registered entities and classify the exact blocker distribution.

The first 2F checkpoint should be census-only:

1. how many are already admitted by an existing entity runtime family;
2. how many are blocked solely by external vanilla base;
3. how many have unsupported source-owned callbacks/methods;
4. how many have constructor/size/additional-spawn-data blockers;
5. how many are already handled by projectile/visible special families.

Do not implement AI or renderer support until this blocker census is complete.
