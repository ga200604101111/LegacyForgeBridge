# 2026-09-18 — Processor block registration neutralization

## Scope

This slice follows converter revision 2026-09-18.124. Runtime-complete single-input processors can already neutralize an exact legacy TileEntity registration after gameplay, presentation and energy-ingress completion.

Converter revision: 2026-09-18.125.

This revision adds the second source-registration gate: GameRegistry.registerBlock. It deliberately preserves source Block constructor and argument evaluation; constructor/allocation retirement remains a later independent proof.

## Side-effect-preserving block registration stripper

LegacyBlockRegistrationCallStripper operates on one proven source method and supports the bounded Forge 1.7 registration shapes:

- Block + String;
- Block + ItemBlock Class + String + Object[] constructor arguments.

Void-returning overloads may be neutralized directly.

If the call descriptor returns Block, the pass does not assume Forge returns its first argument. The call is eligible only when the bytecode itself proves the return value is immediately discarded with POP. A stored, returned or otherwise used value fails closed.

For an admitted call the stripper evaluates every original argument exactly as before, inserts category-correct POP instructions, removes only GameRegistry.registerBlock and any proven immediate discarded return, and reparses the result to verify that no supported registerBlock call remains in the target method.

## Processor admission gate

LegacySingleInputProcessorBlockRegistrationStripPass consumes only processor machines whose sidecar already has:

- baseRuntimeComplete=true;
- sourcePresentationComplete=true;
- runtimeComplete=true.

It rejoins the machine sourceBlockClass to LegacyRegistryAnalyzer. Exactly one block registration identity is required.

The direct registration source is also constrained:

- lifecycle root / static initializer is accepted;
- a helper must be private and have exactly one source call;
- the same direct source method must not own multiple recovered block registrations.

These rules prevent neutralizing a shared registration helper that may still register unrelated blocks.

The pass records:

- blockRegistrationStripWired=true;
- runtimeCompleteRequired=true;
- argumentEvaluationPreserved=true;
- constructorSideEffectsPreserved=true;
- sourceClassDeletionWired=false;
- per-machine legacy registry name, direct source identity, stripped site count and blockers.

## Pipeline ordering

LegacyConversionEngine runs:

1. LegacySingleInputProcessorPresentationPass;
2. LegacySingleInputProcessorTileRegistrationStripPass;
3. LegacySingleInputProcessorBlockRegistrationStripPass.

Both registration mutations therefore require the final source-complete processor runtime boundary first.

## Regression

Normal CI covers:

- void registerBlock neutralization while retaining NEW MachineBlock;
- Block-returning registerBlock only when the returned value is immediately discarded;
- used Block return values failing closed with byte-for-byte source preservation;
- the legacy ItemBlock varargs descriptor family;
- pass-level registration provenance and constructor-preserving staged rewrite.

The exact Bamboo processor regression reads the block-strip sidecar by the machine's sourceBlockClass. If the real MillStone registration is safely neutralizable it requires exactly one clean strip. If it is not, the test requires explicit blockers rather than accepting silent partial retirement.

## Next boundary

After this slice is green, source Block/TileEntity deletion is still not authorized.

The remaining processor retirement work is separated into independent gates:

1. prove replacement of source Block constructor/property side effects;
2. remove the now-dead source Block allocation only after the constructor proof;
3. analyze GUI handler registration and determine whether its processor branch can be retired independently from other GUI families;
4. compute a minimal Block/TileEntity/nested-companion retirement cohort;
5. perform fresh pre-delete/post-delete reference closure with full byte restoration on failure.
