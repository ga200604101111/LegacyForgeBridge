# 2026-09-18 — Variant snowball item-registration neutralization

## Scope

This slice follows converter revision 2026-09-18.118, where the complete source item/projectile/selector cohort has a modern playable runtime, the legacy projectile registerModEntity path can be removed, and source-class deletion remains blocked by explicit retirement evidence.

Converter revision: 2026-09-18.119.

This revision adds a conservative GameRegistry.registerItem retirement gate without removing source item construction or any source class.

## Side-effect-preserving call neutralization

LegacyItemRegistrationCallStripper replaces exactly one supported static GameRegistry.registerItem call with stack POP instructions.

It does not remove the argument producers.

As a result:

- source item constructors still run exactly where the old bytecode evaluated them;
- helper/field lookup side effects are preserved;
- only the actual Forge registration call is neutralized;
- stack behavior after the call remains equivalent because the same arguments are consumed.

The accepted descriptor is bounded to the normal Forge 1.7 Item plus one or two String arguments and void return.

## Direct-source safety

LegacyVariantSnowballItemRegistrationStripPass first joins the complete runtime rule to the exact LegacyRegistryAnalyzer item registration by:

- legacy registry name;
- source item class;
- direct source owner/method/descriptor.

The direct source is accepted only when:

1. it is a static initializer; or
2. it is an FML lifecycle-root method; or
3. it is a private helper with exactly one source-JAR callsite.

Additionally, only one concrete item registration may map to that direct source method.

This explicitly rejects shared registration helpers, public/protected helpers and ambiguous methods.

## Retirement readiness

LegacyVariantSnowballRetirementReadiness now joins both registration-retirement sidecars:

- exact projectile registerModEntity strip;
- exact item registerItem neutralization.

Even when the item registration call is safely neutralized, constructor/argument evaluation remains. Therefore an incoming source-class reference such as Bootstrap -> ItemDirtySnowball is still a deletion blocker until a separate construction-retirement proof exists.

No source class is deleted by this revision.

## Regression

The unrelated-namespace runtime fixture verifies that:

- the unique root registerItem call is removed;
- item construction remains in staged bytecode;
- item registration retirement is marked complete;
- retirement remains blocked by the preserved incoming construction reference.

The exact Bamboo test probes the same boundary. If Bamboo uses a shared helper shape, the item strip is allowed to fail closed with explicit blockers while source-class deletion remains unauthorized.

## Next boundary

The next slice should classify the remaining source item allocation/reference path after registration neutralization:

- prove whether construction is pure/runtime-replaced or contains required source side effects;
- identify any static field identity used by other legacy code;
- remove or rewrite only source references whose semantics are already represented by the modern runtime;
- then rerun fresh retirement reference closure before any class deletion.
