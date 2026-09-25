# Session 2026-09-16 - Constant-false silk eligibility proof

Branch: `feature/generic-conversion-bamboo-corpus2`

Exact corpus SHA-256:

```text
bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402
```

## Problem

The block-drop pipeline already had executable normal/silk/explosion runtime rules, but the exact
Bamboo corpus still admitted only one static self-drop candidate. A large fraction of otherwise-safe
blocks were rejected only because their source hierarchy overrides `renderAsNormalBlock()`.

Treating every override as unknown was unnecessarily conservative for one exact bytecode shape:

```text
ICONST_0
IRETURN
```

Forge 1.7.10's default old `Block.canSilkHarvest()` is the short-circuit expression
`renderAsNormalBlock() && !hasTileEntity(meta)`. If the effective source render callback is proven to
return false unconditionally, silk eligibility is therefore false for every metadata value. The
right side is unreachable, so unresolved `hasTileEntity(meta)` and interface ancestry are irrelevant
to this eligibility result.

## Generic admission rule

`LegacyBlockSilkTouchAnalyzer` now admits only the exact effective constant-false render callback.
The rule is deliberately narrow:

- the first external superclass still has to be vanilla `net/minecraft/block/Block`;
- source overrides of either `canSilkHarvest` overload still fail closed;
- dynamic `renderAsNormalBlock()` remains fail closed;
- constant-true `renderAsNormalBlock()` remains fail closed;
- stacked-item construction remains an independent proof and custom ItemBlocks are not declared safe;
- no Bamboo registry name, package, class name, texture or modid participates in production admission.

When constant false is proven, the analyzer marks eligibility complete with `silkEligible=false`.
Downstream drop readiness does not require a silk stack when silk is impossible, so an unrelated
custom ItemBlock metadata ambiguity cannot block the non-silk drop path.

## Exact Bamboo evidence

Targeted execution against the checksum-pinned original JAR changes the silk eligibility proof from:

```text
eligibility complete = 5 / 63
```

to:

```text
eligibility complete = 31 / 63
silk-disabled complete = 26 / 63
stacked-item complete = 7 / 63
```

Combining the existing normal-drop, material/harvest, explosion and event gates with this new source
proof raises the current static self-drop readiness set from 1 to 12 source-proven candidates:

```text
singleTexDeco
thickSakuraPillar
thinSakuraPillar
thickOrcPillar
thinOrcPillar
thickSprucePillar
thinSprucePillar
thickBirchPillar
thinBirchPillar
delude_width
delude_height
bambooMoss
```

The first eleven are proven silk-disabled. `bambooMoss` remains the previously proven ordinary
Forge-default silk-enabled candidate.

This count is a source-proof/readiness result. It does not claim that a complete exact Bamboo
candidate has passed loader safety, Fabric launch, world entry, or gameplay validation.

## Regression coverage

A generic unrelated-namespace fixture proves that an exact false render override remains admissible
even when `hasTileEntity(meta)` is overridden and an unknown external interface exists. A dynamic
render callback remains rejected. The checksum-pinned exact-corpus test locks the 31 complete / 26
silk-disabled counts and the eleven newly unlocked static self-drop inputs.

```text
VERSION = 0.2.0-alpha.27
CONVERTER_REVISION = 2026-09-16.52
```
