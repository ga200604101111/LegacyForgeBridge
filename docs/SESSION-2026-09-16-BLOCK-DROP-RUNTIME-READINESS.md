# Session 2026-09-16 — Static block self-drop runtime readiness

Branch: `feature/generic-conversion-bamboo-corpus2`

## Purpose

Schema v6 can now prove harvest eligibility for the exact Forge 1.7.10 no-tool Material fast-path.
This slice advances toward gameplay drops without generating any modern loot/runtime yet.

The goal is to identify a deliberately narrow subset whose ordinary and silk-touch results are both
one metadata-independent converted BlockItem. Explosion execution remains a separate mapping proof.

## New readiness artifact

`LegacyBlockDropRuntimeReadinessPass` consumes the final schema-v6 block-drop plan and writes:

```text
legacyforgebridge/block-drop-runtime-readiness.json
```

Pass id:

```text
legacy-block-drop-runtime-readiness
```

The pass is registered immediately after `LegacyBlockHarvestMaterialProofPass` in the normal
conversion pipeline.

## Admission rules

A plan is admitted as:

```text
normalSilkStaticSelfDropReady = true
```

only when all of the following are proven:

- modern block/item identity is complete;
- the source ordinary drop path is default-compatible;
- final normal drop proof is complete;
- harvest eligibility proof is complete;
- ordinary drop item is `SELF_BLOCK_ITEM`;
- ordinary quantity is exactly `1`;
- all 16 legacy metadata values produce item damage `0`;
- silk-touch proof is complete;
- when silk harvest is eligible, the silk stack is also the same self BlockItem, quantity `1`, damage `0`.

This deliberately excludes metadata-dependent drops and any alternate item/quantity.

## Explosion boundary

Even if the source-side explosion proof is complete, this artifact always records:

```text
explosionRuntimeMappingReady = false
explosionRuntimeBlocker = legacy-1.7.10-explosion-chance-to-modern-loot-condition-proof-pending
```

The reason is semantic, not implementation convenience: the proven legacy branch uses a
`1 / explosionSize` drop chance. A future modern loot condition must be proven equivalent before it
can be used as the explosion runtime mapping.

The readiness pass therefore does **not** infer that Minecraft 1.21.11
`survives_explosion` is equivalent.

## No runtime side effects

The artifact also records:

```text
lootRuntimeGenerated = false
```

No loot-table JSON is written, no `Block.getDrops` behavior is overridden, and no runtime registry
is loaded from this sidecar yet.

## Regression coverage

Focused pass tests cover:

- one fully-ready metadata-independent self-drop;
- metadata-dependent normal damage rejection;
- incomplete harvest eligibility rejection;
- silk stack mismatch rejection;
- explicit explosion mapping gate even when source explosion proof is complete.

The full candidate integration regression is extended to prove that normal conversion embeds the
readiness sidecar, applies the pass, admits the proven wood fixture, and still reports both loot
runtime and explosion mapping as disabled.

## Converter identity

```text
VERSION = 0.2.0-alpha.27
CONVERTER_REVISION = 2026-09-16.45
```

## Exact Bamboo boundary

The checksum-matched `Bamboo-2.6.8.5.jar`

```text
bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402
```

is still not preserved in the current CI workspace. This slice is generic proof infrastructure and
does not add a new exact whole-JAR Bamboo compatibility claim.
