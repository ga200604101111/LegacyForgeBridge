# Session 2026-09-16 - Legacy FML transient-seat remote spawn bridge

Branch: `feature/generic-conversion-bamboo-corpus2`

Exact corpus:

```text
Bamboo-2.6.8.5.jar
SHA-256 = bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402
```

## Purpose

The previous Huton core slice made the converted two-part bed and transient seat executable inside a
modern world. This slice closes the corresponding Forge/FML 1.7.10 remote entity-spawn boundary so a
legacy server's registered transient chair can be represented by the same LFB-owned modern seat type.

No general legacy Entity conversion is claimed. The bridge remains fail-closed for every entity that
does not have a source-proven seat-bed rule and an exact mod-local EntityRegistry id.

## Source identity

Forge 1.7.10 `FMLMessage.EntitySpawnMessage` writes:

```text
entityId
modId
EntityRegistration.getModEntityId()
fixed-point x/y/z
rotation bytes
DataWatcher stream
throwerId (+ velocity when non-zero)
IEntityAdditionalSpawnData bytes when implemented
```

The checksum-pinned Bamboo lifecycle proof resolves the Huton seat base registration as:

```text
legacy mod id        BambooMod
entity class         ruby/bamboo/entity/EntityDummyChair
entity name          DummyChair
mod entity type id   23
```

`LegacySeatBedPass` schema 3 now writes the legacy mod id and mod-local entity id into the seat-bed
sidecar. `remoteEntitySpawnRuntimeComplete` is true only when the seat core proof is complete and the
matching lifecycle registration supplies one unambiguous numeric mod-local id/name pair.

A missing remote id does not disable the already-proven local core runtime; it only keeps remote spawn
mapping closed.

## Runtime key

`LegacySeatBedRegistry` indexes admitted remote entities by:

```text
(lower-case legacy mod id, mod-local entity type id)
```

This is intentionally not a global Minecraft entity id and not a class-name guess. Incoming
`EntitySpawnMessage` values must match this exact pair.

For Bamboo's exact source that pair is:

```text
(BambooMod, 23)
```

## Strict FML spawn-tail parsing

The existing runtime codec already decoded the stable EntitySpawn header. The new bounded decoder
also walks the 1.7 DataWatcher wire format until the 127 terminator, then reads the Forge thrower id.

Admitted watcher types for this simple entity boundary are scalar/string/coordinates types 0,1,2,3,4,6.
ItemStack watcher type 5 is rejected. Duplicate watcher ids and more than 32 entries are rejected.

Remote seat construction requires:

```text
throwerId == 0
additionalSpawnBytes == 0
```

Therefore projectile velocity and `IEntityAdditionalSpawnData` payload cannot be silently ignored.
Future entity families need their own proven decoder rather than widening this seat rule.

## Client construction

During PLAY only, when the remote key matches an admitted seat rule and the strict tail is plain:

1. create `legacyforgebridge:converted_legacy_seat` in the current `ClientLevel`;
2. preserve the server entity id;
3. apply the legacy fixed-point position and yaw/pitch;
4. initialize the modern packet-position baseline;
5. insert it into `ClientLevel`.

Subsequent translated vanilla entity/passenger packets can then target the same server entity id.
The entity stays invisible through the renderer added by the previous core runtime slice.

Unknown Bamboo entities and all unrelated Forge entities continue to be decoded/logged only.

## Time acceleration boundary

Huton's source `TimeAccel` changes server world time and weather. That is server-authoritative gameplay.
A real 1.7.10 Bamboo server already executes it; the modern client must not independently apply another
+1000 ticks. This slice therefore does not enable `timeAccelerationRuntimeComplete`.

## Regression coverage

- `FmlRuntimeCodecTest` proves structural skipping of the 1.7 base DataWatcher stream, thrower parsing,
  additional-data preservation and fail-closed ItemStack watcher handling;
- `LegacySeatBedRegistryTest` proves the `(legacyModId, modEntityTypeId)` key is exact except for
  mod-id case normalization;
- `FmlRuntimeClientSeatSpawnBytecodeTest` locks the proof-gated registry lookup, strict spawn decoder,
  converted seat allocation and `ClientLevel.addEntity` wiring;
- `BambooSeatBedExactTest` additionally verifies the exact Bamboo lifecycle registration resolves
  `DummyChair` to mod-local id 23.

## Remaining Huton gates

- special Huton TESR/block/item presentation remains incomplete;
- local/offline emulation of source TimeAccel is not enabled because current LFB use is the client
  side of a legacy authoritative server connection;
- broader Bamboo entity families still require separate source semantics/data-watcher/render proof.

```text
VERSION = 0.2.0-alpha.27
CONVERTER_REVISION = 2026-09-16.58
```
