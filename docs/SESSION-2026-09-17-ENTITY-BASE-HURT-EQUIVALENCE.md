# 2026-09-17 — Plain Entity base hurt equivalence

## Scope

This slice follows converter revision `2026-09-17.73` and closes a vanilla Entity base-behavior gap in the generated `PLAIN_ENTITY_SYNCHED_DATA_ONLY` class before any EntityType registration is enabled.

Converter revision: `2026-09-17.74`.

## Legacy behavior

Forge/Minecraft 1.7.10 base `Entity.attackEntityFrom` / `func_70097_a` does not merely return `false`.

Its source behavior is:

1. when the entity's invulnerable flag is set, return `false` immediately;
2. otherwise mark the entity as hurt (`func_70018_K`, which sets the legacy attacked/hurt synchronization flag);
3. return `false`.

The first generated Java 21 entity-class checkpoint returned `false` directly because runtime registration was still disabled. That placeholder is not sufficient for a real runtime entity.

## Modern mapping

Minecraft 1.21.11 exposes the matching base primitives on `Entity`:

- `isInvulnerable()` for the entity invulnerable flag;
- protected `markHurt()`, which sets modern `hurtMarked=true`.

Generated `hurtServer(ServerLevel, DamageSource, float)` now emits:

- `if (isInvulnerable()) return false;`
- `markHurt();`
- `return false;`

This is limited to the first admission family, whose behavior proof already rejects source-owned `HURT` overrides. No custom legacy damage callback is replaced by this mapping.

## Verification

The ASM regression for generated plain entity classes now requires exactly one call to modern `Entity.isInvulnerable()` and one call to modern `Entity.markHurt()` in the generated `hurtServer` implementation.

The generated-class sidecar records `legacyBaseHurtSemanticsMapped=true`.

EntityType registration remains intentionally unwired in this checkpoint.
