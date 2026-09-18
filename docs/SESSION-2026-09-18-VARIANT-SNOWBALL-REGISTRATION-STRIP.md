# 2026-09-18 — Variant snowball projectile registration strip

## Scope

This slice follows converter revision 2026-09-18.116, where a source-complete metadata-indexed ItemSnowball family has a complete modern item, projectile, impact and thrown-item renderer runtime.

Converter revision: 2026-09-18.117.

The purpose of this slice is to begin source-cohort retirement conservatively by removing only the exact legacy projectile registration already replaced by the modern EntityType runtime.

## Exact registerModEntity strip

LegacyVariantSnowballRegistrationStripPass consumes only runtime rules that already declare complete item/projectile/impact/renderer implementation.

For each rule it independently joins the source projectile class and exact legacy registration metadata back to LegacyLifecycleAnalyzer:

- source projectile class;
- legacy entity registry name;
- numeric mod entity id;
- tracking range;
- update frequency;
- velocity-updates flag.

Only one exact matching lifecycle registration is accepted.

The pass then reuses LegacyEntityRegistrationStripper, which removes a callsite only when the seven registerModEntity argument producers are a proven pure contiguous stack slice matching those exact values. Computed, ambiguous or helper shapes outside that proof are left untouched.

The pass records:

- projectileRegistrationStripWired=true;
- itemRegistrationStripWired=false;
- sourceClassDeletionWired=false;
- per-rule stripped site count and blockers.

This revision intentionally does not strip GameRegistry.registerItem. Generic registry analysis can trace helper-wrapped item registration identities, but deleting a shared registration helper would remove unrelated items. Item-registration retirement therefore remains a separate proof problem.

## Regression

The unrelated-namespace fixture verifies that the exact projectile registerModEntity call is removed from the staged candidate while the source launch item still remains an incoming projectile reference.

An exact-corpus test runs the Bamboo snowball family through:

1. generic content identity;
2. source-complete selector/impact proof;
3. source-complete launch proof;
4. normalized runtime candidacy;
5. source projectile registration proof;
6. complete modern runtime materialization;
7. projectile registration strip.

The exact test requires the Bamboo snowball family to expose all complete runtime flags, all ten metadata variants, and exactly one safely stripped legacy projectile registration site.

## Next boundary

After this slice is green, retirement work continues in two independent gates:

1. prove a safe exact GameRegistry.registerItem strip for the source item without deleting shared helper registrations;
2. run fresh candidate reference closure for the item/projectile/selector cohort and only then authorize source-class deletion with restore-on-failure semantics.

No source class is deleted by this revision.
