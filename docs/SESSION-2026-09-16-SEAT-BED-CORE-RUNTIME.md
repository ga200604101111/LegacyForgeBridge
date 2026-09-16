# Session 2026-09-16 - Two-part bed / transient seat core runtime

Branch: `feature/generic-conversion-bamboo-corpus2`

Exact source proof remains pinned to:

```text
Bamboo-2.6.8.5.jar
SHA-256 = bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402
```

## Scope

This slice crosses the source-proven bed/seat family from proof-only into a bounded executable core.
It does not claim the source time-acceleration callback or special TESR/item presentation.

## Runtime mapping

Schema 2 of `legacyforgebridge/seat-bed-rules.json` admits the core only when the previous source proof is complete.
The runtime registry rejects rules unless all three execution gates are true:

```text
twoPartPlacementRuntimeComplete = true
sleepSeatRuntimeComplete = true
seatEntityRuntimeComplete = true
```

It continues to reject claims for:

```text
timeAccelerationRuntimeComplete = false
presentationRuntimeComplete = false
runtimeComplete = false
```

### Separate placement item

The source family has a dedicated placement item identity distinct from the block ItemBlock identity.
`ConvertedLegacySeatBedItem` replays the proven ItemBed override rather than treating the item as a generic BlockItem:

- only the upward face is accepted;
- placement occurs one block above the clicked support;
- both target positions must be air;
- both supports must have a sturdy top face;
- adventure-mode editing remains fail-closed rather than guessed across versions;
- source yaw quadrants map SOUTH/WEST/NORTH/EAST to raw metadata 0/1/2/3;
- foot is written with metadata 0..3 and head with metadata 8..11;
- one item is consumed outside infinite-material mode.

### Block state and foot-only BlockEntity

`ConvertedLegacySeatBedBlock` subclasses the modern BedBlock so Minecraft 1.21.11 sleeping/wakeup lifecycle remains native, while also carrying `ConvertedLegacyBlock.LEGACY_META`.
The raw 1.7 metadata is authoritative for pair direction and head/foot identity; modern FACING/PART are a semantic projection.
Only the foot half creates `ConvertedLegacySeatBedBlockEntity`.

The source transient occupancy flag is intentionally not persisted. It is only a runtime lock preventing duplicate seat actors.

### Failed-sleep seat fallback

The exact 1.7 source mounts the transient chair only for `NOT_POSSIBLE_NOW`.
The modern equivalent is the ordinary non-explosive bed rule where spawn is allowed but sleeping is currently disallowed:

```text
!bedRule.explodes()
&& bedRule.canSetSpawn(level)
&& !bedRule.canSleep(level)
```

This boundary is checked before delegating to modern BedBlock, preventing the modern daytime path from setting spawn before the source seat fallback is selected.
Other sleep outcomes continue through modern BedBlock behavior.

### Transient seat actor

LFB owns one shared `legacyforgebridge:converted_legacy_seat` entity type.
The actor:

- is effectively invisible/no-physics;
- accepts the mounted player as a passenger;
- anchors to the source-proven foot BlockEntity;
- discards itself if the rider disappears or the anchor/occupancy disappears;
- releases occupancy and ejects passengers when removed;
- persists no custom anchor data, matching the source empty custom NBT contract.

A client renderer is registered solely to make the synchronized entity type render-safe; it emits no visible model or shadow.

### Pair removal/drop

The foot half owns the dedicated placement-item drop. Head removal checks whether the matching foot still exists and emits the source item before the orphaned foot disappears. Creative BedBlock cleanup removes the foot first, preventing an extra drop.

## Still gated

- source `TimeAccel=true` behavior (+1000 world time and rain/thunder countdown handling after the seat timer) remains non-executable;
- Huton TESR/model/item presentation remains non-executable;
- the family therefore remains `runtimeComplete=false` even though its placement/sleep-seat/entity core is executable.

## Regression coverage

- strict schema-2 runtime registry parsing;
- exact legacy direction/head-bit mapping;
- generated block/item registration bytecode must allocate the specialized seat-bed classes;
- the previous checksum-pinned source-proof regression remains the exact-corpus semantic guard.

```text
VERSION = 0.2.0-alpha.27
CONVERTER_REVISION = 2026-09-16.57
```
