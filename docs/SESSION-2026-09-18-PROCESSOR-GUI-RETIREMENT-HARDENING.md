# 2026-09-18 — Processor GUI retirement hardening and CI recovery

Converter revision: `2026-09-18.129`.

## Resume point

The resumed branch was already at `005d4c7cb9b1830b9f2d09ca0b19fa6e2827a8d7`,
not the earlier GUI-proof-only checkpoint. Its later Block constructor/property and inline
allocation work is retained. This slice changes only the GUI branch stripper, its regression
coverage, one incorrect diagnostic expectation, and the converter cache revision.

## GUI mutation boundary

`LegacySingleInputProcessorGuiHandlerBranchStripper` retains its public Target/Result API.
It now requires exact handler method descriptors, unchanged handler parameter locals,
unique switch dispatch and isolated executable case entry points. Aliased labels, default
entries, other switches, incoming jumps and fallthrough into the selected case fail closed.
Methods with exception handlers and branches with interior stack frames remain unsupported.

The admitted branch must construct and return exactly the proven GUI or Container using
the handler player's inventory and the handler World(x, y, z) TileEntity. Both raw TileEntity
handoff and a cast to the exact source tile are supported. Extra calls, extra effects,
discarded construction, changed coordinates and malformed operand stacks cannot borrow the
older source proof.

Both methods are checked with ASM BasicVerifier before and after the rewrite. The rewrite
removes executable instructions, not metadata labels or debug scopes. Only an independently
verified null-return pair is returned to the staging pass. Any rejection returns the original
byte array and zero stripped branches; there is no one-sided server/client mutation.

No source mod class is loaded by this analysis. No processor source class deletion is added.

## Regression coverage

Eleven new JUnit test methods use an unrelated namespace. They cover TABLESWITCH and
LOOKUPSWITCH success, raw tile handoff, selected-entry jumps, shared/default targets,
fallthrough, try/catch regions, extra effects, discarded construction, changed operands,
overwritten parameters, invalid targets, malformed bytes, invalid stacks and debug scopes.

Successful rewritten fixtures are actually defined and invoked by an isolated JVM classloader:
GUI id 7 returns null; unrelated id 8 and the default case preserve their original results.
The JVM fixtures are synthetic, not execution of the external Bamboo source JAR.

## Verified results before this diagnostic correction

Baseline run #617 (`35305412145`), at `005d4c7cb9b1830b9f2d09ca0b19fa6e2827a8d7`,
compiled the project and tests and remapped the JAR, but reported 565 tests with one failure
and 11 skipped. The failing local-alias allocation fixture was already correctly rejected;
its assertion expected a later expression-tracer diagnostic.

Run #618 (`35306283248`), at `5428d27d66dde5f3c3561f85b2523f32e7ee6985`,
compiled the hardened implementation and ran the real project ASM/JUnit suite. All 11 new
GUI safety methods and both existing GUI stripper methods passed, including JVM execution.
The complete run still failed: 576 tests, one failed, 11 skipped. Its sole remaining failure
was the allocation diagnostic assertion, now printed explicitly as:

`[exact-block-registration-proof-missing]`

An initial assumption that the later constructor-provenance gate was responsible was wrong.
The registry analyzer cannot bind the local alias to the concrete source Block at all, so the
allocation analyzer rejects it before examining constructors or expressions. This follow-up
asserts that missing registry binding independently, requires both allocation admission flags
to remain false, and requires exactly `exact-block-registration-proof-missing`.
No production allocation rule has been relaxed. The push CI for the commit containing this
correction is the authoritative full-build result; this note does not predeclare its outcome.

Before formal CI, a local JDK-internal-ASM diagnostic also passed all 11 new methods. It is
not used as a substitute for the project ASM 9.10.1 / JUnit / Gradle build recorded above.

## Remaining boundary

The processor retirement readiness pass still sets `retirementAuthorizationWired=false`
and `sourceClassDeletionWired=false`. Runtime, constructor, allocation, GUI and final staged
reference closure remain separate evidence gates. Narrow GUI rejection must not be bypassed
by broadening the retirement cohort or by assuming runtime completeness implies deletion safety.

Normal `gradle build` excludes `exact-corpus`. A normal green build does not establish exact
Bamboo conversion or live-game playability. Exact corpus verification still requires the
external source JAR with SHA-256
`bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402`.
No new Bamboo playable/test JAR is claimed by this slice.
