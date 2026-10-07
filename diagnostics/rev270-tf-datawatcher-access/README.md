# rev270 Twilight Forest Part 2D — reference-valued watcher writes

Date: 2026-10-07

## Exact corpus blocker

The first full access-surface audit after the Part 2C schema proof admitted 72/77 registered entity
access surfaces and rejected five.

Two failures were generic analysis gaps:

- EntityTFHydraHead inherits EntityTFHydraPart.setPartName(String), which writes watcher index 17
  using a String method parameter.
- EntityTFCharmEffect.setOwner(String) writes watcher index 17 using a String method parameter.

The prior write-type proof understood literals, fields and typed method returns, but returned
unknown for an ALOAD producer even when the JVM method descriptor proved the parameter/reference
type.

## Generic fix

`LegacyEntityDataWatcherAccessAnalyzer` now follows reference-valued ALOAD provenance:

- an exact method parameter slot may derive its type from the JVM descriptor;
- non-parameter reference locals recurse through the verifier source-frame local provenance;
- CHECKCAST may provide an exact supported wrapper/String type or forward to the underlying source;
- merge/cycle/unknown reference provenance still fails closed.

The admitted watcher value kinds remain only Byte, Short, Integer, Float and String.

A synthetic regression covers both direct String-parameter updateObject and one local alias before
the write.

## Remaining exact-corpus blockers

After this fix, the remaining expected entity-family blocker is the Ghast inheritance family:

- EntityTFMiniGhast
- EntityTFTowerGhast
- EntityTFUrGhast

They use DataWatcher index 16 owned by vanilla EntityGhast, not by a Twilight Forest source
entityInit schema. That requires a separate platform/inherited watcher proof and must not be solved
by pretending index 16 is source-owned.

Source-wide closure also sees TFClientEvents reading vanilla EntityLivingBase watcher indices 7 and
8; those platform-owned event reads are a separate boundary.

No renderer/AI behavior is implemented by this revision.
