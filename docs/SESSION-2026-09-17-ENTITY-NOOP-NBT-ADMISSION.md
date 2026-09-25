# 2026-09-17 — Entity no-op NBT admission proof

## Scope

This slice follows converter revision `2026-09-17.69` and closes a structural gap in the first `PLAIN_ENTITY_SYNCHED_DATA_ONLY` family.

A concrete Minecraft 1.7.x class that directly extends `Entity` normally supplies the abstract legacy NBT callbacks even when the entity has no persistent state. Treating every `readEntityFromNBT` / `writeEntityToNBT` declaration as unsupported behavior would therefore make the first runtime family practically unreachable for real concrete entities.

Converter revision: `2026-09-17.70`.

## Proof rule

`LegacyEntityBehaviorSurfaceAnalyzer` now marks every retained source instance method with `trivialNoOp`.

The proof is intentionally strict: a method is trivial no-op only when its executable bytecode consists of exactly one `RETURN` instruction. Labels, frames, and debug nodes are ignored; loads, field access, calls, branches, constants, stores, exception logic, or any other executable instruction make the proof false.

This is not general NBT translation. It proves only the exact empty legacy callback case.

## Admission change

`LegacyEntityRuntimeAdmissionPass` now permits these callback/method kinds in the first family:

- `ENTITY_INIT`;
- `READ_NBT` only when the exact effective source method is `trivialNoOp=true`;
- `WRITE_NBT` only when the exact effective source method is `trivialNoOp=true`.

Non-empty NBT methods remain blocked with explicit callback/source-method blockers. All other behavior restrictions are unchanged.

## Why this is safe

The planned modern plain entity runtime already uses empty `readAdditionalSaveData` and `addAdditionalSaveData` implementations. Mapping a proven one-instruction legacy NBT stub to those empty modern callbacks preserves the source behavior instead of inventing persistence semantics.

This slice still does not register an `EntityType`, generate `SynchedEntityData` accessors, or claim renderer/spawn/runtime completeness. It only makes the existing admission family reachable by concrete no-persistence legacy entities.
