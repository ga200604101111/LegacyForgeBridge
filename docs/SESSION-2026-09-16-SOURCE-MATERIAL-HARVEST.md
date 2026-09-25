# Session 2026-09-16 - Source-owned Material harvest proof

Branch: `feature/generic-conversion-bamboo-corpus2`

Exact corpus SHA-256:

```text
bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402
```

## Problem

`kitunebi` already has a source-proven normal self drop, exact constant-false silk eligibility and an
override-free explosion path, but its harvest proof remained blocked because its constructor passes
`ruby/bamboo/block/MaterialBamboo.instance` rather than one of Minecraft's static Material fields.
The previous material-provenance analyzer admitted only direct vanilla `Material.field_*` values.

The exact source bytecode is not ambiguous: `MaterialBamboo` directly extends 1.7.10 `Material`, its
`instance` field is initialized once in `<clinit>` with `new MaterialBamboo()`, and its constructor
calls the ordinary Material constructor plus `func_76219_n` (setNoPushMobility). It does not call
`func_76221_f` (setRequiresTool) and does not override `func_76229_l` (isToolNotRequired).

MCP 908 Material initializes the private requires-no-tool flag to true. `func_76221_f` is the setter
that flips it false, while `func_76229_l` returns the flag. This is the same version-locked fact used
by the existing vanilla Material table.

## Generic provenance extension

`LegacyBlockMaterialProvenanceAnalyzer` now admits a source-owned Material field only when all of the
following are proven from bytecode:

- the field's object type is exactly its owning source class;
- that class directly extends `net/minecraft/block/material/Material`;
- the field is static;
- the entire source JAR contains exactly one write to that field;
- that write is in the owning class's `<clinit>`;
- the assignment is the exact local `NEW / DUP / <init>() / PUTSTATIC` singleton shape.

Mutable/reassigned source Material fields remain unresolved.

## Generic no-tool harvest proof

New `LegacySourceMaterialHarvestAnalyzer` proves only the inherited-default case. For a stable source
Material singleton it additionally requires:

- no source override of `isToolNotRequired()/func_76229_l()`;
- no source invocation anywhere in the input JAR of
  `setRequiresTool()/func_76221_f()` on a Material-compatible owner.

If those conditions hold, the inherited 1.7.10 default is exactly `toolNotRequired=true`. The proof
is deliberately global/conservative: an unrelated source call to setRequiresTool blocks source-owned
Material fast-path admission rather than attempting receiver alias analysis.

`LegacyBlockHarvestMaterialProofPass` composes this source rule beside the existing pinned vanilla
Material table. The source rule receives mode:

```text
source_material_tool_not_required_1_7_10
```

and records `sourceOwnedRule=true` plus the exact source Material owner/field/descriptor. Tool/player
fallback semantics remain gated for any material not proven no-tool.

## Exact Bamboo impact

The checksum-pinned original JAR now source-proves the harvest fast path for:

```text
kitunebi -> ruby/bamboo/block/MaterialBamboo.instance
```

Combined with the already-proven drop, silk, explosion and event gates, this adds one more static
self-drop candidate after the `.53` metadata-runtime slice. The current proof-gated executable
block-drop family therefore rises from **21 to 22 candidates** once the exact conversion sidecar is
materialized.

This remains a bounded block-drop/harvest improvement. It does not claim whole-mod loader safety,
entity/worldgen/dimension conversion, remaining BlockEntity families, custom silk stack semantics,
or gameplay-complete Bamboo conversion.

## Regression coverage

- stable direct source Material singleton with harmless Material mutation keeps inherited no-tool
  default;
- any setRequiresTool call fails closed;
- source override of isToolNotRequired fails closed;
- later source reassignment of the singleton fails material provenance before harvest interpretation;
- synthetic pass integration proves the source Material rule closes the no-tool harvest fast path;
- checksum-pinned exact-corpus test locks `kitunebi`'s Material/drop/silk/explosion evidence.

```text
VERSION = 0.2.0-alpha.27
CONVERTER_REVISION = 2026-09-16.54
```
