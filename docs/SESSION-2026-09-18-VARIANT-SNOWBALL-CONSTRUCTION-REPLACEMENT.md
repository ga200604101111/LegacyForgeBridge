# 2026-09-18 — Variant snowball constructor replacement readiness

## Scope

This slice follows converter revision 2026-09-18.119. The complete modern variant-snowball runtime exists, legacy projectile registration is stripped, and the legacy item registration call is neutralized while preserving source construction.

Converter revision: 2026-09-18.120.

This revision proves whether that preserved source item constructor already has a complete generated behavior replacement. It does not remove the original allocation or authorize source-class deletion.

## Generic ItemSnowball behavior base

LegacyBehaviorCompiler now recognizes the vanilla 1.7.10 ItemSnowball parent as a bounded API type.

LegacyBehaviorApi.Snowball reproduces the relevant constructor state by inheriting Item and setting maximumStackSize to 16.

This is generic support for any admitted legacy ItemSnowball subclass and does not depend on Bamboo ids or class names.

## Constructor replacement proof

LegacyVariantSnowballConstructionReplacementReadiness runs after LegacyBehaviorPass and requires, for each complete runtime rule:

- exact item registration neutralization already complete;
- converted-content item identity matches the runtime source item class;
- source constructor descriptor is present;
- exactly one generated behavior ItemBinding matches the same modern id and source class;
- behavior allocation class and constructor descriptor match the converted-content proof;
- constructor argument descriptor/value list matches the converted-content constructor arguments;
- generated behavior bootstrap class exists;
- generated remapped source item class exists in staging.

Only then is constructorReplacementProven=true.

## Retirement boundary

LegacyVariantSnowballRetirementReadiness now independently requires:

- item registration strip;
- constructor replacement proof;
- source allocation strip.

This revision deliberately keeps sourceAllocationStripWired=false, so even a fully proven generated constructor does not authorize deletion while the original NEW/constructor path still remains in staged legacy bytecode.

## Regression

Normal CI covers an unrelated-namespace custom ItemSnowball family and requires:

- generated behavior bootstrap present;
- source constructor descriptor ()V retained;
- generated remapped source item class present;
- constructorReplacementProven=true;
- sourceAllocationStripWired=false;
- retirement still blocked specifically by the unretired source allocation.

The exact Bamboo regression records the real ItemDirtySnowball constructor proof and remains fail closed if any constructor semantics cannot be generated.

## Next boundary

After this revision is green, the next slice can implement a bounded source-allocation stripper. It may remove the original item allocation only when this constructor replacement proof is complete and fresh candidate reference checks show that no unrelated source path still needs the constructed instance.
