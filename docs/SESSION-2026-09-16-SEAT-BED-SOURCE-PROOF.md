# Session 2026-09-16 - Two-part bed / transient seat source proof

Branch: `feature/generic-conversion-bamboo-corpus2`

Exact Bamboo corpus:

```text
Bamboo-2.6.8.5.jar
SHA-256 = bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402
```

## Purpose

This slice inventories a legacy `BlockBed` family whose normal bed interaction falls back to a
transient invisible seat entity when the sleep attempt returns `NOT_POSSIBLE_NOW`. It is a source
proof/readiness slice only. It does **not** enable a modern seat entity, two-part placement runtime,
time acceleration, or special renderer.

The production analyzer is generic. No Bamboo registry name, package, class name, tile id, entity
name, texture, or mod id is used as an admission condition.

## Exact source semantics checked

The checksum-pinned Bamboo 2.6.8.5 bytecode contains one matching family:

```text
block       ruby/bamboo/block/BlockHuton
item        ruby/bamboo/item/ItemHuton
tile        ruby/bamboo/tileentity/TileEntityHuton
seat base   ruby/bamboo/entity/EntityDummyChair
seat impl   ruby/bamboo/tileentity/TileEntityHuton$1
```

The exact bytecode shows:

- a dedicated `ItemBed` subclass performs the two-block placement using the ordinary 1.7 bed
  direction formula, two edit checks, two air checks, two solid-top checks and two block writes;
- the block is a `BlockBed`, reports `isBed=true`, uses special render type `-1`, and has a 0.25-high
  source bound;
- only the foot half creates the source TileEntity, gated by `(metadata & 12) == 0`;
- activation performs the source sleep attempt and invokes the TileEntity seat path only when the
  returned enum is exactly `NOT_POSSIBLE_NOW`; other cases retain the inherited bed fallback;
- the TileEntity prevents duplicate seats with a boolean occupancy guard, spawns one transient seat,
  mounts the player, and the block clears occupancy when the seat dies;
- the registered seat base is no-clip, server-authoritative, removes itself when it has no rider or
  its chair block disappears, unmounts its rider on death, notifies the chair block, stores only
  transient coordinates, has no NBT payload and zero mounted Y offset.

Java bytecode sometimes records inherited members with the source subclass as the constant-pool
owner. The analyzer therefore accepts a member owner only when that owner is itself source-proven to
inherit the required external base (`BlockBed`, `ItemBed`, or `Entity`). This keeps the rule generic
without widening it to arbitrary matching descriptors.

## Optional time acceleration

The Huton seat implementation also contains a separately proven source behavior guarded by a source
static boolean configuration field. After a mounted player remains seated for the source threshold,
it advances world time by 1000 ticks and reduces active rain/thunder timers by 1000 when those timers
are above 1000, then reports the resulting time/weather to the rider.

This is recorded as `timeAccelerationSourceProven=true`; it is **not** executed by the modern bridge
in this slice. Exact Bamboo's default configuration enables this feature, so the Huton family must
not be declared gameplay-complete until the authoritative modern mapping is implemented.

## Sidecar

`LegacySeatBedPass` writes:

```text
legacyforgebridge/seat-bed-rules.json
schemaVersion = 1
```

Each admitted rule records the block/item/tile/seat provenance and the individual source proofs.
All execution gates deliberately remain false:

```text
twoPartPlacementRuntimeComplete = false
sleepSeatRuntimeComplete = false
seatEntityRuntimeComplete = false
timeAccelerationRuntimeComplete = false
presentationRuntimeComplete = false
runtimeComplete = false
```

Special TESR presentation is explicitly recorded as required rather than silently replaced with a
generic cube.

## Regression coverage

- hierarchy/branch unit regressions prove inherited source-member ownership remains bounded;
- checksum-pinned exact-corpus regression locks the Bamboo Huton provenance and proof flags;
- normal CI compiles the analyzer/pass and runs the generic helper regressions; the exact-corpus test
  remains opt-in through the existing `lfb.exactCorpus.jar` mechanism.

## Next boundary

The next Huton slice should materialize the already-proven core semantics in bounded stages:

1. modern two-part placement/state and foot-only BlockEntity topology;
2. server-authoritative transient seat actor with occupancy reclamation;
3. exact `NOT_POSSIBLE_NOW` fallback behavior;
4. authoritative time/weather acceleration mapping;
5. source-proven special block/item presentation.

None of those stages should claim completion until its own proof/runtime gate is true.

```text
VERSION = 0.2.0-alpha.27
CONVERTER_REVISION = 2026-09-16.56
```
