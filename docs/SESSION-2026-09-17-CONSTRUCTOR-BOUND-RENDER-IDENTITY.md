# 2026-09-17 — Constructor-bound registered Block render identity proof

## Scope

This slice follows converter revision `2026-09-17.94`. GridPot positive insertion already understands direct constant `getRenderType()` values, direct symbolic static render fields, explicit vanilla metadata demultiplexing, and exact symbolic matches such as `coordinateCrossUID` when the source block returns that field directly.

Converter revision: `2026-09-17.95`.

## Remaining positive-identity gap

Some registered legacy blocks do not return a constant/static field directly. Instead they store a constructor argument in an instance field and later return that field from `getRenderType()`.

Bamboo `BlockBamboo` is an acceptance example:

```text
constructor -> this.renderType = renderType
getRenderType() -> this.renderType
```

`bamboosingle` and `bamboo2` pass `CustomRenderHandler.coordinateCrossUID` as that constructor argument.

## Bounded proof

`LegacyRegisteredBlockRenderTypeAnalyzer` now admits this shape only when all of the following are true:

- `getRenderType()/func_149645_b()` is exactly `ALOAD 0 -> GETFIELD <int> -> IRETURN`;
- the concrete registration constructor is int-only;
- that exact constructor writes the returned field exactly once as `this.field = constructorParameter`;
- if the registry analyzer already proves that constructor parameter as a number, it becomes a constant render identity;
- otherwise source allocation sites are analyzed with ASM source frames;
- only `NEW registeredClass -> ... -> invokespecial registeredClass.<init>` receivers are considered;
- every non-render constructor argument must match the registration's already-proven integer arguments;
- the target render argument must resolve to one exact integer constant or one exact `GETSTATIC int` field;
- multiple differing candidate identities fail closed.

No arbitrary constructor arithmetic, helper-returned render IDs, field mutation, or non-int constructor family is accepted.

## Bamboo acceptance

The checksum-pinned exact-corpus test requires:

```text
bamboosingle -> ruby/bamboo/CustomRenderHandler#coordinateCrossUID
bamboo2      -> ruby/bamboo/CustomRenderHandler#coordinateCrossUID
```

This is test-only corpus validation. Production admission remains structural and registration-derived.

## GridPot impact

No new GridPot runtime mutation code is required. `.92` already matches symbolic render identities against the source insertion predicate. Once a constructor-bound block now resolves to the same static render field, its converted modern block id automatically enters the existing positive insertion eligibility set.

This closes more positive render identity surface before any future attempt to execute the legacy negative insertion branch. Whole Bamboo conversion remains `PARTIAL`.
