# 2026-09-18 — Variant snowball nested companion retirement

## Scope

This slice follows converter revision 2026-09-18.122. The complete modern variant-snowball runtime, exact source registration neutralization, constructor replacement proof, source allocation strip, retirement readiness and atomic three-class retirement path already exist.

Converter revision: 2026-09-18.123.

The remaining blocker was compiler-generated nested companion bytecode such as an enum-switch map class. Those classes are part of the replaced source cohort, but the previous retirement model treated them as external incoming references.

## Generic companion discovery

LegacyVariantSnowballRetirementReadiness now discovers staged class files whose binary names are nested under any of the three primary source classes:

- source item class + "$...";
- source projectile class + "$...";
- selector class + "$...".

No concrete suffix such as "$1" or "$SwitchMap" is hard-coded.

Every discovered companion is added to the same retirement cohort and receives the same conservative checks as a primary source class:

- source dependency evidence must exist;
- candidate state must remain ORIGINAL_BYTES_RETAINED before retirement;
- generated symbolic references must be absent;
- dynamic bytecode evidence must be absent;
- all incoming candidate references must stay inside the expanded cohort;
- resource references must be absent.

The readiness sidecar records nestedCompanionClasses, nestedCompanionClassCount and per-companion evidence.

## Fresh deletion recheck

LegacyVariantSnowballRetirementPass does not trust the readiness companion list blindly.

Immediately before deletion it rescans staging for nested companions and requires the freshly discovered set to exactly match the readiness set. A changed set blocks retirement.

When the set matches, the deletion transaction covers:

- source item;
- source projectile;
- selector enum;
- every discovered nested companion.

All bytes are backed up first. The pass performs a fresh pre-delete reference scan, deletes the complete expanded cohort, performs a post-delete reference scan, and restores every class on any failure.

deletedSourceClassCount and the aggregate deletedSourceClasses count are now dynamic rather than fixed at three.

## Regression

The unrelated fixture explicitly proves that foreign/entity/VariantProjectile$1 is treated as an internal companion rather than an external selector blocker. Retirement tests calculate the expected deletion count from the readiness sidecar and require every reported companion class to disappear atomically.

The exact Bamboo corpus test now also uses the readiness companion list:

- if retirement is authorized, all primary classes and all companions must be absent;
- if any real blocker remains, all primary classes and all companions must remain present.

## Safety boundary

Nested naming alone does not authorize deletion. It only expands the candidate cohort.

A companion with an external incoming reference, resource reference, generated reference, missing dependency evidence or dynamic bytecode evidence still blocks the entire retirement transaction. This preserves the existing fail-closed policy while correctly modeling compiler-generated helper classes that exist only to support the already-replaced source cohort.
