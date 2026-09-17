# 2026-09-17 — Variant snowball selector-effect dispatch proof

## Scope

This slice follows converter revision `2026-09-17.104`, where the selector-independent DirtySnowball impact shell became source-proven.

Converter revision: `2026-09-17.105`.

The remaining impact body contains an enum switch. This slice proves the compiler-generated enum dispatch and bounded selector effects without implementing the custom Ender teleport helper or generating a modern projectile runtime.

## Compiler switch proof

`LegacyVariantSnowballSelectorEffectAnalyzer` accepts two common Java 7 compiler families:

- javac-style synthetic switch-map array fields, including companion synthetic classes;
- ECJ-style synthetic `()[I` switch-table helpers.

For an admitted switch it requires:

1. the switched ordinal comes from the projectile's already-proven selector field;
2. every explicit switch key is source-bound back to an exact selector enum constant by the synthetic switch-table population program;
3. duplicate/missing case bindings fail closed;
4. the living target local is proven from the current `MovingObjectPosition.entityHit` after an `EntityLiving` guard;
5. every selector not present as an explicit switch case reaches a proven no-op default.

Compiler case tags are deliberately kept separate from selector ids / legacy metadata. A selector whose source id is `7` may legitimately use compiler switch tag `1`.

## Bounded effects

The first admitted selector-effect family is:

- direct `EntityLiving.addPotionEffect(new PotionEffect(Potion.<field>.getId(), duration, amplifier))`;
- potion field restricted to the exact 1.7.10 poison / confusion / regeneration identities;
- constant non-negative duration and amplifier.

The sidecar records these as `impactEffect=POTION` plus `potion`, `duration`, and `amplifier`.

A selector branch that directly calls a projectile-owned `(EntityLiving) -> boolean` helper is source-mapped but remains `CUSTOM_HELPER_UNCOMPILED`. This is the current Bamboo Ender branch.

Selectors proven to take the no-op default are emitted as `impactEffect=NONE`.

Unknown explicit branches remain `UNCOMPILED` and keep selector-specific completion closed.

## Bamboo target

Historical Bamboo source shows the intended selector edges:

- `ender` -> custom random teleport helper;
- `poison` -> poison, duration 30, amplifier 3;
- `confusion` -> confusion, duration 200, amplifier 1;
- `heal` -> regeneration, duration 50, amplifier 1;
- `stone`, `ice`, `iron`, `gold`, `diamond`, `compress` -> no additional target effect beyond common thrown damage.

The checksum-pinned exact-corpus regression requires those exact edges from the admitted Bamboo JAR. Production analysis contains no Bamboo class-name or registry-name special case.

## Sidecar schema 5

`legacyforgebridge/variant-snowball-proof.json` now records:

- `selectorEffectDispatchProven`;
- `selectorPotionEffectBranchCount`;
- `selectorEffectBlockers` when dispatch/effect proof is incomplete;
- per-variant `impactEffect` with bounded potion/helper details;
- `selectorSpecificImpactSemanticsComplete` independently from the already-proven common impact shell;
- `impactSemanticsComplete = common shell && selector-specific semantics complete`.

The root also reports `selectorEffectDispatchFamilies` and `selectorSpecificCompleteFamilies`.

`impactCompilerWired=false` and `runtimeImplementationWired=false` remain unchanged. Proof is not runtime generation.

## Regression

Normal CI covers a javac-style unrelated-namespace fixture where:

- selector id `7` is assigned compiler case tag `1`;
- the tag resolves back to the `poison` enum constant;
- the branch applies poison for 30 ticks at amplifier 3;
- the other selector reaches the proven no-op default;
- deleting the synthetic case binding closes the entire dispatch proof rather than guessing from branch order.

The exact Bamboo regression additionally requires all three potion branches, the Ender helper edge, and the six no-op selectors.

## Next boundary

The only known DirtySnowball selector-specific gameplay semantic still uncompiled after this slice is the Ender teleport helper. Its source behavior includes random destination generation, downward ground search, collision/liquid rejection, rollback on failure, 128 portal particles and portal sounds.

That helper must be proved as its own bounded behavior before DirtySnowball impact semantics can become complete and before a modern projectile runtime is generated.
