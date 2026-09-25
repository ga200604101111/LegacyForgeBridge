# Session 2026-09-16 — Proof-gated block-drop runtime wiring

Branch: `feature/generic-conversion-bamboo-corpus2`

## Purpose

The previous slice compiled source-proven static self-drop plans into
`legacyforgebridge/block-drop-runtime-rules.json`, but deliberately left gameplay unwired.

This slice consumes only those admitted rules at runtime and connects them to
`ConvertedLegacyBlock`. No new source behavior is inferred here.

## Runtime registry

`LegacyBlockDropRuntimeRegistry` lazily loads the converted mod's runtime-rule sidecar on the first
drop lookup for that namespace.

Loading is fail-closed. A rule is accepted only when:

- the sidecar schema is exactly `1`;
- `runtimeImplementationWired = true`;
- the rule mode is exactly `STATIC_SELF_DROP_LEGACY_EXPLOSION_OVERRIDE`;
- the shape remains `SELF_BLOCK_ITEM`, quantity `1`, legacy damage `0`, metadata-independent;
- normal/silk proof is complete;
- explosion source proof is complete;
- source explosion destruction overrides are absent;
- the inverse-radius explosion formula proof is complete;
- source `ExplosionEvent` affected-set proof is complete;
- the generated modern block and BlockItem both exist and match the rule id.

Anything else keeps the modern fallback behavior.

## Ordinary and silk drops

For an admitted rule, `ConvertedLegacyBlock#getDrops` returns exactly one self BlockItem.

This is safe for the admitted subset because the compiler already proved:

- ordinary drop result = self BlockItem ×1, damage 0;
- all sixteen legacy metadata values map to the same item damage;
- the silk-touch result is either inapplicable or exactly the same stack;
- the no-tool Material harvest fast path is source-proven;
- source drop-path overrides and harvest-drop event mutations are absent.

Blocks without a runtime rule still delegate to the normal modern implementation.

## Explosion drops

For destructive modern explosion interactions (`DESTROY` and `DESTROY_WITH_DECAY`) an admitted
block no longer inherits the modern loot-decay distinction.

Instead it evaluates the legacy Forge 1.7.10 per-affected-block probability directly:

```text
random.nextFloat() <= 1.0F / explosion.radius()
```

When the roll succeeds, one self BlockItem is emitted through the modern explosion drop consumer.
The block is then replaced with air using update flags `3`, matching the source default
`Block#onBlockExploded` removal path for the already-proven override-free subset.

`KEEP` and `TRIGGER_BLOCK` are not reinterpreted by this bridge and still delegate to the modern
implementation.

## Sidecar state

`LegacyBlockDropRuntimeRulePass` now writes:

```text
runtimeImplementationWired = true
```

The older block-drop plan and readiness artifacts intentionally retain their broader proof-only
markers. They describe the whole unbounded drop/explosion problem; this runtime implements only the
strict rule subset emitted by the final rule pass.

## Regression coverage

The slice adds coverage for:

- fail-closed absence of runtime rules;
- the exact inclusive inverse-radius probability threshold;
- `ConvertedLegacyBlock` bytecode wiring for normal/silk drops and explosion drops;
- executable runtime marker emission;
- full conversion candidate embedding of an executable proof-gated rule.

## Converter identity

```text
VERSION = 0.2.0-alpha.27
CONVERTER_REVISION = 2026-09-16.48
```

## Deliberate boundary

This still does not claim arbitrary block-drop compatibility. Dynamic quantities, metadata-dependent
drops, custom harvest/drop callbacks, custom explosion callbacks, specialized external block bases,
source harvest/explosion event handlers, and any plan that failed an earlier proof gate remain
inactive.

The checksum-pinned Bamboo whole-JAR corpus is not present in the current CI workspace, so this slice
does not claim new exact Bamboo gameplay validation. The next corpus step should continue from this
runtime base and re-run the exact Bamboo candidate/runtime proof when the pinned binary is available.
