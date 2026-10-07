# rev263 Twilight Forest generic registry proof

Base runtime: `legacyforgebridge-0.2.0-alpha.27-rev262-tf-creative-final.jar`.

Result runtime:
`0.2.0-alpha.27-corpus4-local.46-rev263-tf-registry.1`

Converter revision:
`2026-10-07.263-registry-vanilla-boundary-field-bindings`

## Generic fixes

1. Legacy Block registration dispatch now treats a missing `net/minecraft/block/Block*`
   superclass as the known vanilla Block hierarchy boundary when the invoked getter/setter owner is
   `net/minecraft/block/Block`, or when that owner has already been reached. Source overrides are
   still rejected before the boundary.
2. Static-field binding compares normalized legacy identities so
   `foo`, `item.foo`, and `tile.foo` can refer to the same source-proven registration while
   kind and implementation-class uniqueness checks remain in force.

No Twilight Forest registry-name or class allowlist is used.

## Exact Twilight Forest 2.3.8 result

Running the rev263 patched `LegacyRegistryAnalyzer` against
`twilightforest-1.7.10-2.3.8-tw.jar`:

```text
REGS=171 ITEMS=110 BLOCKS=61 BINDS=171 DIAGS=0
TFItems bindings=110
TFBlocks bindings=61
```

This corrects the rev260 baseline, which produced:

```text
REGS=145 ITEMS=110 BLOCKS=35 BINDS=0 DIAGS=0
```

The 26 missing blocks were primarily source blocks inheriting vanilla subclasses such as BlockLog,
BlockLeaves, BlockBush, BlockSapling, BlockContainer and related Block families that are not bundled
inside the mod JAR.

## Delivery identity

Local test JAR SHA-256:
`8fca946690da6e3db31c2704e4293afafff12d4580699d465144d859d5c722ef`

`PatchRegistry263Final.java` is the exact audited bytecode patcher used on top of rev262.
This remains a diagnostics checkpoint rather than a `src/main` promotion because the rev260
LegacyRegistryAnalyzer production source on this branch is older than the shipped rev260 bytecode.
