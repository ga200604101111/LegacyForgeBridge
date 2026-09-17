# Session 2026-09-17 — Plain Entity renderer registration stripping

Checkpoint target: converter revision `2026-09-17.83`.

## Goal

After `.82`, exact common-side `EntityRegistry.registerModEntity` callsites can be removed once the modern plain Entity runtime is complete. The client bootstrap can still retain legacy Entity and renderer references through `RenderingRegistry.registerEntityRenderingHandler(...)`.

This slice removes that client registration edge only when both presentation and construction are proven inert.

## Constructor side-effect proof

`LegacyEntityRendererConstructionAnalyzer` proves the source renderer's no-arg constructor chain. Every source constructor in the chain must contain exactly:

- `ALOAD 0`
- `INVOKESPECIAL directSuper.<init>()V`
- `RETURN`

The chain must terminate at the known vanilla 1.7.10 base:

`net/minecraft/client/renderer/entity/Render`

Any field initialization, helper call, alternate constructor arguments, missing/ambiguous no-arg constructor, or unsupported external base fails closed.

## Exact registration expression

`LegacyEntityRendererRegistrationStripper` accepts only the canonical five-opcode expression:

1. Entity class literal
2. `NEW` proven renderer
3. `DUP`
4. renderer `INVOKESPECIAL <init>()V`
5. `RenderingRegistry.registerEntityRenderingHandler(Class, Render)`

No label/frame boundary may split the expression. The Entity and renderer identities must match the source presentation proof exactly.

## Pass integration

`LegacyPlainEntityRendererRegistrationStripPass` requires:

- executable `runtimeComplete=true` plain Entity rule;
- exactly one source renderer registration;
- `sourceNoOpRendererProven=true`;
- renderer class present and render callback proven no-op;
- exact source owner/method/descriptor;
- side-effect-free no-arg constructor chain;
- exact canonical registration expression in current staging bytecode.

It rewrites only the staging candidate. Output:

`legacyforgebridge/plain-entity-renderer-registration-strip.json`

The sidecar retains constructor proof reason, source constructor chain, terminal base, exact registration source identity, stripped site count, and fail-closed blockers.

## Effect on retirement readiness

When stripping succeeds, the later current-candidate reference scan no longer sees the client registrar referencing either the legacy Entity class literal or the legacy renderer construction. The renderer's own no-op render method descriptor may still reference the Entity; `.81` intentionally permits that single Entity/renderer retirement-cohort edge.

Actual source Entity/renderer class deletion remains unauthorized. Direct local Entity construction/spawn sites are also outside this slice.
