# Session 2026-09-16 — Proof-gated block-drop runtime rules

Branch: `feature/generic-conversion-bamboo-corpus2`

## Purpose

The preceding readiness artifact now separates normal/silk self-drop proof, per-affected-block
explosion proof, Forge ExplosionEvent absence, the exact decay probability formula, and the still
unresolved modern non-decay interaction mode.

This slice compiles only the fully source-proven subset into a minimal runtime-rule artifact. It does
**not** yet connect those rules to `ConvertedLegacyBlock`; gameplay behavior remains unchanged.

## New artifact

`LegacyBlockDropRuntimeRulePass` writes:

```text
legacyforgebridge/block-drop-runtime-rules.json
```

Pass id:

```text
legacy-block-drop-runtime-rules
```

The pass runs after `LegacyBlockDropRuntimeReadinessPass`.

## Admission gate

A readiness entry becomes a runtime rule only when all of these are true:

```text
normalSilkStaticSelfDropReady
explosionSourceProofComplete
sourceExplosionDestructionOverrideFree
explosionDecayFormulaProofComplete
explosionAffectedSetSourceProofComplete
```

and the proven drop shape is exactly:

```text
dropKind = SELF_BLOCK_ITEM
quantity = 1
legacyDamage = 0
metadataIndependent = true
```

No fallback, approximate, or partially-ready rule is emitted.

## Runtime rule

The admitted mode is:

```text
STATIC_SELF_DROP_LEGACY_EXPLOSION_OVERRIDE
```

Each rule records the converted block id plus the proof facts required by the next runtime slice.
The planned implementation will:

- return one converted BlockItem for ordinary/silk drops;
- override the converted Block explosion hook;
- apply the exact legacy `nextFloat() <= 1.0F / explosion.radius()` chance directly;
- therefore avoid depending on whether modern Minecraft chose `DESTROY` or `DESTROY_WITH_DECAY`.

That implementation is **not** enabled in this slice.

## Explicit non-runtime state

The rules root carries:

```text
runtimeImplementationWired = false
```

The existing block-drop plan still carries:

```text
gameplayDropRuntimeWired = false
block-drop-gameplay-runtime-pending
runtimeComplete = false
```

This makes the intermediate artifact safe to inspect and regression-test before any gameplay method
starts consuming it.

## Regression coverage

Focused tests verify that rules are emitted only for entries with complete normal/silk,
per-affected-block explosion, source destruction, probability formula, and ExplosionEvent affected-set
proof.

The full `LegacyConversionEngine` candidate regression also verifies that:

- the runtime-rule pass is actually applied;
- the final candidate JAR contains the rules artifact;
- the proven wood fixture produces exactly one rule;
- the rule uses `STATIC_SELF_DROP_LEGACY_EXPLOSION_OVERRIDE`;
- `runtimeImplementationWired` remains false.

## Converter identity

```text
VERSION = 0.2.0-alpha.27
CONVERTER_REVISION = 2026-09-16.47
```

## Next boundary

The next slice can now wire a small runtime registry and `ConvertedLegacyBlock` overrides without
re-deciding source eligibility at runtime. Only rules already admitted by this compiler will be
executable.
