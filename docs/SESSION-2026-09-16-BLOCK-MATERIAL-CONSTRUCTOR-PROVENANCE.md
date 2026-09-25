# Session 2026-09-16 — Block Material constructor provenance

Branch: `feature/generic-conversion-bamboo-corpus2`

## Purpose

The preceding harvest-eligibility slice proved that a source-owned Block hierarchy did not replace
Forge/Minecraft harvest callbacks or register a `PlayerEvent.HarvestCheck` handler. Complete harvest
eligibility is still intentionally gated because Forge 1.7.10 also depends on the Block material and
held-tool/player state.

This slice establishes the next fact without interpreting it: which raw legacy
`net.minecraft.block.material.Material` static field was passed to `Block.<init>`.

No gameplay runtime is enabled by this work.

## Analyzer

`LegacyBlockMaterialProvenanceAnalyzer` starts from every source-proven registered Block and walks
source-owned superclasses outward. The admitted topology requires the first external superclass to
be exactly:

```text
net/minecraft/block/Block
```

The source class that directly extends Block is then inspected constructor-by-constructor with ASM
`SourceInterpreter` data-flow analysis.

For every direct `Block.<init>(...)` invocation whose first argument is Material, the analyzer traces
the value on the operand stack. Provenance is complete only when that value is produced directly by
one static field read:

```text
GETSTATIC net/minecraft/block/material/Material.<raw-field>
```

and every source constructor that directly invokes Block converges on the same field.

The analyzer records the raw owner, field name and descriptor. It deliberately does not translate
SRG/MCP names or attach semantic meaning to the field yet.

## Fail-closed cases

Material provenance remains incomplete when any of the following is observed:

- the source hierarchy terminates at an external specialized Block subclass such as `BlockOre`;
- the Material value is supplied through a constructor parameter or another computed expression;
- the value is null or otherwise not one direct static Material field;
- multiple direct Block constructors use different Material fields;
- constructor bytecode cannot be analyzed;
- no direct `Block.<init>` call can be proven in the source class that extends Block.

This is intentionally stricter than merely finding a Material reference somewhere in the class.

## Regression coverage

Synthetic JAR tests cover:

- direct source Block with one static Material field;
- registered subclass inheriting a source base that owns the Block constructor;
- a delegating `this(...)` constructor funneling into one proven Block constructor;
- an unresolved null/computed Material value;
- multiple constructors using different Material fields;
- a specialized external `BlockOre` base.

## Remaining harvest boundary

This slice does not yet claim whether a proven Material requires a tool. The following remain
separate proof/runtime work:

- the exact Forge/Minecraft 1.7.10 `Material.isToolNotRequired()` truth table;
- raw Material field-name identity mapping where required;
- per-metadata `setHarvestLevel` requirements;
- held-item Forge tool classes and harvest levels;
- the `EntityPlayer.canHarvestBlock` fallback path;
- modern mining tags/tool-level equivalence;
- silk-touch stacked-item semantics;
- gameplay execution of block-drop plans.

The existing `harvest-eligibility-proof-pending` runtime blocker therefore remains unchanged.

## Converter identity

```text
VERSION = 0.2.0-alpha.27
CONVERTER_REVISION = 2026-09-16.40
```

## Exact Bamboo boundary

The checksum-matched `Bamboo-2.6.8.5.jar`

```text
bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402
```

is still not present in the CI workspace. This analyzer is generic source-bytecode infrastructure;
it does not add a new exact whole-JAR Bamboo compatibility claim.
