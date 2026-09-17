# Session 2026-09-17 — Plain Entity constant attackability

Checkpoint target: converter revision `2026-09-17.90`.

## Scope

This slice extends the proof-gated constant Entity override family with a historical name-stable mapping:

- legacy 1.7.10 `Entity.canAttackWithItem()Z` / SRG `func_70075_an`
- modern 1.21.11 `Entity.isAttackable()Z`

Forge/MCP 1.7.10 documents `func_70075_an` as the gate where returning false prevents an item from inflicting damage against the entity. In later mappings the same SRG method is named `isAttackable`, and 1.21.11 still exposes `Entity.isAttackable()Z`.

## Source proof

`LegacyEntityBehaviorSurfaceAnalyzer` now classifies both source names:

- `canAttackWithItem`
- `func_70075_an`

as callback kind `CAN_ATTACK_WITH_ITEM`.

The existing constant boolean analyzer remains unchanged: only exact `ICONST_0/1; IRETURN` source bodies are admitted. Dynamic logic, helper calls, field reads and branches remain blocked.

## Mapping IR

`LegacyEntityConstantOverridePass` adds:

- `sourceKind=CAN_ATTACK_WITH_ITEM`
- `targetMethod=isAttackable`
- `targetDescriptor=()Z`
- `mappingSemantics=ATTACKABILITY_BOOLEAN_IDENTITY`
- `constantKind=boolean`

The existing mapping-driven admission and typed codegen gates validate this mapping without adding a Bamboo-specific branch.

## Runtime codegen

For an admitted plain Entity, `LegacyPlainEntityConstantOverrideCodegenPass` emits:

- `public boolean isAttackable()`
- exact proven `ICONST_0/1`
- `IRETURN`

No attack/damage callback is otherwise synthesized. This preserves only the source's constant attackability gate.

## Deliberate exclusions

This slice does not map dynamic `canAttackWithItem` logic, `attackEntityFrom`, interaction callbacks, projectile behavior, local legacy spawn sites or mutable DataWatcher metadata.
