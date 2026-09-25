# 2026-09-17 — Entity renderer provenance fix

## Scope

Converter revision `2026-09-17.72` introduced the source-wide legacy entity presentation inventory, but its first CI run exposed an overly narrow ASM provenance assumption: a Java expression shaped as `new Renderer()` is not guaranteed to arrive at the final callsite as a `SourceValue` containing only the original `NEW` instruction.

Converter revision: `2026-09-17.73`.

## Failure

The synthetic canonical registration:

`RenderingRegistry.registerEntityRenderingHandler(EntityClass.class, new Renderer());`

was rejected with:

`Unable to prove entity/renderer class identity for RenderingRegistry call ...`

Compilation was successful; only the two new presentation regressions failed.

## Fix

`LegacyEntityPresentationAnalyzer` now resolves source identities recursively through the bounded producer shapes created by ASM `SourceInterpreter`:

- direct class-literal `LDC Type`;
- direct renderer `NEW`;
- `DUP` copies;
- object `CHECKCAST`;
- `ALOAD` local aliases;
- `INVOKESPECIAL <init>` only when the constructor receiver itself recursively proves the same `NEW` owner.

The resolver is depth-bounded and cycle-guarded. Multiple producer paths are accepted only when every path collapses to the same exact internal class name.

Critically, constructor ownership is not used as independent identity evidence. A constructor call proves the renderer type only after its receiver has already been proven from the allocation provenance.

## Safety boundary

This change does not broaden presentation runtime admission. Field-loaded renderer singletons, factory-returned renderers, polymorphic renderer suppliers, or otherwise ambiguous object identities remain fail-closed.

The source no-op renderer definition remains unchanged: the effective source-owned `doRender` / `func_76986_a` callback must still contain exactly one executable `RETURN` instruction.
