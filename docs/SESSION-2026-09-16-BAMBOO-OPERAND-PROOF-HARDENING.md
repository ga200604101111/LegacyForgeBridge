# Session 2026-09-16 - Bamboo operand proof hardening

Branch: `feature/generic-conversion-bamboo-corpus2`

Base commit: `67030a5cea6f3ae09b40b0c39574933c8aacfaa8` (converter revision `.50`).
This continues the existing branch; no alternate branch or mod-specific production profile is used.

## Retained fixes

The `.49` and `.50` commits already replaced cross-call TESR lookback windows, admitted raw
TileEntity handoff to a narrowing GUI constructor, and scoped Item subtype mutations so unrelated
custom ItemBlocks do not poison every block's silk proof. Those fixes are retained.

Reviewing their negative cases exposed additional false-positive admission paths. This slice hardens
operand provenance rather than weakening the existing proof gates.

## GUI operand and field binding

New package-private `LegacyGuiTileHandoff` uses ASM source frames to prove both ends of the handoff:

1. The actual last operand of `(InventoryPlayer, TileEntity)` construction originates from
   `World.getTileEntity` / `func_147438_o`, using the handler's World and x/y/z parameters.
2. The GUI constructor narrows its second argument to the expected source TileEntity and stores
   that value into a tile-typed instance field on `this`.

Direct values, the correct CHECKCAST and local-variable aliases are admitted. A lookup or cast
merely appearing elsewhere in the method is not evidence that its result reaches the constructor
or field. Null substitution, discarded values, incorrect coordinates, a different narrowing type,
ambiguous branch merges and instructions belonging to a different method fail closed.

Constructor field binding is deliberately limited to straight-line constructors without exception
handlers. Conditional/interprocedural constructor assignment requires a separate proof.

`LegacySingleInputProcessorPresentationAnalyzer` now consumes these operand-level checks instead of
its earlier call-presence and cast-presence tests.

## TESR argument control-flow and uniqueness

`LegacyDirectCallArguments` retains the exact local class/string/new/dup/constructor sequence, so
minimal direct ASM fixtures need not synthesize production maxStack/frame metadata.

The canonical path now additionally checks:

- invocation membership in the supplied instruction list;
- the exact three-argument TESR descriptor;
- absence of branch, switch or exception-handler targets inside the argument expression.

Line-number, frame and ordinary metadata labels remain harmless. A branch that supplies a different
class literal before joining halfway through the expression cannot borrow the fallthrough literal.
The source-frame fallback still rejects unresolved or merged provenance.

Both processor and oscillating-model registration selection now reject unresolved applicable
ClientRegistry registrations rather than silently ignoring potential renderer replacements. This
is intentionally conservative when an unrelated dynamic registration cannot be scoped.

## Regression evidence

Two new JUnit classes contain 17 test methods:

- `LegacyGuiTileHandoffTest`: 11 methods covering raw/cast/alias handoffs, incorrect operands,
  constructor-field provenance and integration with GUI selection.
- `LegacyDirectCallArgumentsControlFlowTest`: 6 methods covering metadata-only canonical sequences,
  branch entry, descriptor/membership rejection, consecutive calls and unresolved registrations.

Before these changes, five new negative checks failed against the `.50` implementations: discarded
GUI lookup, branch-entry contamination, wrong call descriptor, foreign invocation membership and
ignored unresolved TESR registration. After the changes, all 17 local test bodies passed.

Local execution qualifications: the available environment has JDK 21 but not Gradle or the complete
Minecraft dependency graph. Pure production analyzer sources were compiled unchanged and executed
using a locally relocated copy of the JDK's bundled ASM9 implementation. New test bodies were run
with only JUnit annotations/imports removed. This is not a substitute for the project's JUnit/Gradle
CI with pinned ASM 9.10.1. No relocated ASM, test dependency substitute, Minecraft binary or corpus
binary is committed or bundled in the mod.

## Targeted exact-JAR recheck

Input: original `Bamboo-2.6.8.5.jar`, 1,319,593 bytes.

```text
SHA-256 = bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402
Registry items = 42
Registry blocks = 63
Registry diagnostics = []
MillStone processor rules = 1
MillStone GUI/world-model presentation = present; diagnostics = []
MillStone source tick/energy proof = complete
MillStone energy-ingress proof = complete
Maneki oscillating-model rules = 1
Maneki world presentation = present; diagnostics = []
Silk proofs = 63
Silk eligibility complete = 5
Silk stacked-item complete = 7
Both silk proofs complete = 1 (bambooMoss, legacy damage 0)
```

The original bytecode and analyzer output confirm MillStone's GUI id is `1`, process threshold is
`400`, and min/max energy use is `100` / `500`. Earlier conversational values `50`, `79`, `20` / `40`
were incorrect and must not be used as corpus expectations.

A separate targeted drop probe produced 30 normal drop plans and 33 incomplete entries. For
`bambooMoss` it proved a one-item self drop with damage zero for all sixteen metadata values, safe
harvest/material inputs and an override-free explosion path. This does not assert that a final
runtime sidecar was materialized or that every Bamboo block drop is now supported.

## Validation boundary

This session's local evidence is targeted analyzer execution on the exact binary and the 17 new
test bodies. It is not a full `exactCorpusTest`, complete candidate conversion, Fabric launch,
world entry, rendering screenshot or legacy-server gameplay validation. The normal CI task excludes
`exact-corpus`; its result must be reported separately from exact-JAR checks.

The whole-mod PARTIAL/non-installable boundary is unchanged. Source-class dependency closure,
remaining runtime families and loader/gameplay validation are not bypassed.

```text
VERSION = 0.2.0-alpha.27
CONVERTER_REVISION = 2026-09-16.51
```
