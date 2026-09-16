# Session 2026-09-16 - Metadata-mapped block-drop runtime

Branch: `feature/generic-conversion-bamboo-corpus2`

Exact corpus SHA-256:

```text
bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402
```

## Purpose

The existing executable block-drop runtime admitted only quantity-one self BlockItems whose legacy
item damage was zero for all sixteen block metadata states. Exact Bamboo contains another narrow
family whose complete `damageDropped(meta)` result is already source-proven but metadata-dependent.
The missing piece was runtime preservation of that item data.

## Admission boundary

The readiness/rule/runtime chain now accepts a metadata-mapped self-drop only when all existing
normal-drop, material/harvest, explosion and Forge-event gates are complete **and**:

- the drop target is the converted block's own BlockItem;
- quantity is exactly one;
- `itemDamageByBlockMeta` contains exactly sixteen integer entries;
- every entry is within the legacy block metadata domain `0..15`;
- silk-touch proof is complete and `silkTouchEligible=false`.

Silk-enabled metadata-dependent blocks remain fail-closed until their custom stacked-item behavior
is separately proven. Malformed, fractional, negative, out-of-range or incomplete damage tables are
rejected by both conversion and runtime parsing.

## Runtime mapping

`LegacyBlockDropRuntimeRulePass` emits a second rule mode:

```text
METADATA_SELF_DROP_LEGACY_EXPLOSION_OVERRIDE
```

The existing static mode remains unchanged and schema 1 remains backward compatible. The metadata
mode carries `legacyDamageByBlockMeta[16]` and `metadataIndependent=false`.

`LegacyBlockDropRuntimeRegistry.Rule` stores an immutable 16-entry table. `ConvertedLegacyBlock`
reads its opaque `legacy_meta` BlockState value, resolves the source-proven item damage, and writes
non-zero values to the existing `lfb:legacy_meta` ItemStack component. Normal harvesting and the
proven legacy explosion path use the same stack materialization helper. A zero result intentionally
omits the component because `LegacyStackComponents.get()` defines absence as legacy value zero.

## Exact Bamboo scope

The checksum-pinned Bamboo JAR has nine source-proven metadata-dependent self-drop candidates whose
legacy damage table is the identity mapping `0..15` and whose silk eligibility is now proven false:

```text
crossLamp
bambooLiangThick
bambooLiangVLogThick
bambooLiangVLog2Thick
bambooLiangVWoodThick
bambooLiangThin
bambooLiangVLogThin
bambooLiangVLog2Thin
bambooLiangVWoodThin
```

Together with the twelve static candidates from converter revision `.52`, this raises the current
source-proven executable block-drop family to **21 candidates** once the full conversion sidecar is
materialized under the modern runtime classpath.

This is not a claim that the entire Bamboo candidate is loader-safe or gameplay-complete. Entity,
worldgen/dimension, remaining BlockEntity families, custom silk stacks and source-class dependency
closure remain independent gates.

## Regression coverage

- generic pass test proves a silk-disabled identity damage table reaches the runtime sidecar;
- runtime parser accepts exact 16-entry tables and rejects short, out-of-range and fractional data;
- runtime lookup preserves every metadata entry;
- bytecode regression proves normal and explosion drops both route through legacy damage lookup and
  the LFB-owned stack metadata component;
- exact-corpus regression pins the nine Bamboo source candidates and their identity tables.

```text
VERSION = 0.2.0-alpha.27
CONVERTER_REVISION = 2026-09-16.53
```
