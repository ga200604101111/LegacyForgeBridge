# 2026-10-08 — Twilight Forest Part 2G / rev289: generic TileEntity NBT field-pair provenance

Branch: `feature/generic-conversion-iyamato-corpus3`.

## Goal

Separate **1.7.10 TileEntity NBT persistence** from **server-to-client packet delivery** in the generic Block + TileEntity + TESR preflight. Moonworm-style animated placed blocks may read tick-driven source fields, but the existence of `readFromNBT`, `writeToNBT`, `getDescriptionPacket` or `onDataPacket` does not itself prove the fields reach the modern Fabric renderer.

rev289 remains a **source-only** checkpoint; not a runnable client animation or a complete main JAR.

## Production source additions

`src/main/java/dev/yinghuang/legacyforgebridge/convert/LegacyTileNbtPersistenceAnalyzer.java` is a bounded, source-driven Java 7 ASM analyzer (no legacy class loading). For exact source-owned visual fields requested by existing renderer/yaw/pivot proofs, it:

1. Confirms a source TileEntity subclass and uniquely resolves requested primitive instance fields through its source-owned inheritance chain; hidden/shadowed, static, absent or unsupported fields cannot be admitted.
2. Follows only the **effective** source `writeToNBT(NBTTagCompound)` and `readFromNBT(NBTTagCompound)` bodies and their actual source-owned `INVOKESPECIAL` super calls. A superclass override **not invoked** by the subclass is not imagined to have run.
3. Uses ASM `SourceInterpreter` instruction frames to require the actual `NBTTagCompound` operand is the unchanged method parameter, the field load/store receiver is the original TileEntity `this`, and the source field is the exact declared primitive member.
4. Requires direct matching NBTTagCompound setter/getter methods with identical constant string tag keys and primitive types; MCP/SRG integer, float, long and boolean shapes are included. It rejects unknown helpers, branches, extra arithmetic, field mutations in writers, duplicate field writes, duplicate NBT **keys** across different fields, ambiguous control flow and unsupported method bodies.
5. Observes source packet sender/receiver hook declarations separately, without inferring their payload, scheduling, delivery, client field updates or runtime installation.

Only original source-class ancestors are parsed, rather than all classes in every scanned legacy JAR. Both each admitted `StoredField` and the overall `Audit` remain **persistence-source evidence only**. `networkPayloadProven=false` and `clientRuntimeWired=false` are enforced record invariants.

`src/main/java/dev/yinghuang/legacyforgebridge/convert/pass/LegacyBlockTileModelPreflightPass.java` collects the exact visual field dependencies from earlier rev285 renderer/model reads, rev287 source yaw and rev288 pivot animation. It asks the new analyzer to prove each field's paired source NBT read/write, then writes the result to the **existing non-executable** `legacyforgebridge/block-tile-model-preflight.json`.

New candidate fields: `sourceTileNbtFieldPairAuditPresent`, `sourceTileNbtPairStatus`, `sourceDescriptionPacketHookObserved`, `sourceOnDataPacketHookObserved`, `sourcePairedNbtVisualFields` (source owner, field name, descriptor, exact NBT key and primitive kind), `sourceUnpairedNbtVisualFields`, `sourceAllVisualFieldsNbtPersistent`, `sourceTilePacketPayloadProven=false`, `sourceTileNbtRuntimeWired=false`. Root counters: `sourceTileNbtPairAuditCandidateCount` and `sourceFullyPersistentVisualStateCandidateCount`.

The manifest accepts an audit only when its registered block key, exact source TileEntity and required field partition match that same registered candidate. Earlier manifest overloads still emit no NBT proof. The `AUTO` diagnostics cannot change unrelated conversion readiness.

**Safety gates are unchanged:** `runtimeWired=false`, `blockEntityRuntimeWired=false`, `animationSemanticsProven=false`, `tileStateSyncProven=false`, per candidate `runtimeReady=false` and **no executable `rules` array**.

## Moonworm source relationship, without mod-specific dispatch

In upstream Benimatic Twilight Forest (commit `98b88bde74d6db0aa463dba304f5c13acb6140fb`), `BlockTFMoonworm` is a placed ordinary Block subclass, `TileEntityTFMoonworm` owns `currentYaw`, `desiredYaw`, `yawDelay` and modifies them in `updateEntity`. Its parent `TileEntityTFCritter` declares NBT read/write overrides but only delegates to vanilla `TileEntity`: it does **not** encode the three animation fields in matching NBT tags. Thus there is no source proof those three state fields are persisted/sent. rev289 includes a generic inherited NBT passthrough negative test that does not rely on any Twilight Forest names.

`MoonwormShot` remains a separate projectile Entity, handled by the generic rev281–283 mesh/light/source-launch pipeline. Both may use the same original `ModelTFMoonworm` class but do not share runtime lifecycle, packet synchronization or admission status.

This upstream-source observation is **not** a statement about the byte-exact translated `twilightforest-1.7.10-2.3.8-tw.jar`: the user's exact Twilight Forest binary is still unavailable in this session.

## Executed checks (2026-10-08)

| Source-only Java 21 suite | Passed |
| --- | ---: |
| rev289 strict NBT read/write Java 7 synthetic bytecode + negative cases | 30/30 |
| rev289 manifest/readiness and field-partition negation cases | 11/11 |
| rev284–rev288 pre-existing analyzer/manifest regressions, executed with the rev289 pass first on classpath | 143/143 |
| **Total** | **184/184** |

Compiled against the user-supplied rev260 JAR binary classpath using temporary `jdk.internal.org.objectweb.asm` import substitutions and Gson/JUnit test doubles. This is **not** a full project Gradle/Loom/JUnit run with real external ASM nor a Fabric 1.21.11/ViaFabricPlus startup or native Forge 1.7.10 multiplayer test. No executable model animation or tile networking was activated.

Original rev260 full main JAR remains byte-identical:
`03a7bff020197b275977227ff0ee4dbbf7c9e3df77ea6c1425470f7fde4b93d9`.

All production code uses semantic source bytecode relationships, no Twilight Forest ID/name/number dispatch. No Actions dispatch, PR, release, force push, workflow change, main/Bamboo mutation, original mod binary edit or Forge server change.

## Next hard gates

1. Verify actual **packet payload** source provenance: `getDescriptionPacket` constructing a packet from this source TileEntity's NBT; `onDataPacket` consuming that packet's NBT back into those exact instance fields. A saved NBT pair alone cannot prove the packet contract.
2. Where original tile source does **not** carry dynamic state across the wire, find a genuinely protocol-observable client rendering equivalent, or classify that visual animation as unsupported until it can be proven. Never run source `updateEntity` gameplay logic on the modern client.
3. Finish generic 1.7.10 TESR matrix transform closure and conditional `ModelRenderer` part animation, preserving original texture/UV and authoritative block metadata.
4. Recover rev256–260 cumulative source/build overlays, test against the exact Twilight Forest `-tw.jar`, and execute actual 1.21.11 Fabric client vs unchanged 1.7.10 Forge server acceptance.
5. Only a truly compiled and game-validated complete main JAR can supersede the pinned rev260. Do not relabel the old main binary.

Related: `docs/SESSION-2026-10-08-TWILIGHTFOREST-PART2G-REV288.md`, `docs/TWILIGHT-FOREST-238-ACCEPTANCE.md`, `docs/SESSION-2026-10-08-REV282-BASE-RECOVERY.md`.
