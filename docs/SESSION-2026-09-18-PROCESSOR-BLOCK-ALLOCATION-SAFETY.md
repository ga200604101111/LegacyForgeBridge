# 2026-09-18 — Processor Block allocation retirement safety

Converter revision: `2026-09-18.131`.

## Resume and integration

The interrupted allocation-retirement slice at `005d4c7cb9b1830b9f2d09ca0b19fa6e2827a8d7`
already contained source analysis, staged residue removal, property materialization and
retirement-readiness wiring. Its normal CI (#617, run `35305412145`) compiled the project
but reported 565 tests, one failed and 11 skipped.

The failed local-alias fixture was correctly rejected before expression tracing with
`exact-block-registration-proof-missing`. This was independently reproduced locally.
During this work the branch advanced to `4f8f08f93c468dfda81b1077b4180596601103b6`.
That commit already fixes the diagnostic assertion and retains a separate GUI-retirement
hardening slice. Both are preserved unchanged; this commit is based on that newer tip.

## Source proof hardening

An `INVOKEVIRTUAL` symbolic owner is not the receiver's dynamic class. Allocation-site
setter proof now walks the actual allocated source Block hierarchy, rejecting any source
implementation of the setter even when the call is typed as vanilla Block. Inherited
vanilla setters remain supported, but unrelated symbolic owners cannot borrow this proof.

Floating-point property arguments must be JVM float constants. An integer or double LDC
is not converted into an apparent supported value.

`LegacyInlineBlockAllocationSafety` is shared by source analysis and staged mutation.
It proves one uninterrupted live operand-stack interval from allocation to registration
(or, in staged bytes, the exact terminal discard). The allocated value cannot be copied,
stored in locals, cast, passed to another call or consumed by another operation. SourceValue
provenance is checked together with instruction operand reads: DUP can preserve the original
producer while creating a second consumer, so provenance and stack balance alone are not enough.
Other registration-argument evaluation is retained, including its independent side effects.

Branches, switches, targeted interior labels, exception-protected intervals and interior
stack-map frames fail closed. This is a narrow inline-allocation proof, not general alias
analysis or static-holder retirement.

## Staged bytecode integrity

The staged stripper retains existing StackMapTable frames outside the removed straight-line
interval, including frames in completely unrelated methods. It verifies the target method
with ASM BasicVerifier before and after mutation, in addition to source-dataflow analysis.
All rejected or ambiguous cases return the original bytes and zero stripped sites.
The existing post-strip source-class reference check still runs before returning rewritten bytes.

A regression demonstrates why operand-read checking is required: an extra ambient stack
value can mask the underflow after an escaped DUP allocation is deleted. The old output
can remain stack-balanced while passing the wrong object to an observer. The new gate rejects
that transformation rather than trusting successful post-rewrite stack analysis.

## Regression coverage and local verification

`LegacyBlockAllocationRetirementSafetyTest` adds 16 normal JUnit test methods in an unrelated
fixture namespace. They cover inherited and overridden setters, exact float constants,
DUP consumers, casts, local aliases, branch/exception intervals, ambiguous sites, residual
references, two/four-argument neutralization, fluent effects, preserved argument side effects
and retained stack-map frames. Successful staged fixtures are defined and invoked by an
isolated JVM classloader. Rejected rewrites require byte-for-byte equality with the input.

An isolated local probe ran these 16 methods against the interrupted implementation:
7 passed and 9 failed. After these changes, all 16 pass, together with the 2 existing source
allocation tests (18 passed, 0 failed), under `java -Xverify:all`.

This probe uses locally available repackaged ASM and JUnit assertion adapters plus the last
successful CI artifact as a dependency. It is not the repository's Gradle/JUnit Platform
build and does not replace formal CI. The push workflow for this commit remains the
source of truth for full-project compilation, tests and artifact generation.

## Boundaries and next work

No source class deletion, static-field allocation support or broader runtime admission is
added. Modern runtime completeness, constructor replacement, allocation/registration removal,
GUI retirement and final staged reference closure remain separate gates. Source-class
retirement authorization must remain false until all required evidence is independently closed.

Normal Gradle tests exclude `exact-corpus`. Exact Bamboo validation and live-game acceptance
were not run by this local probe. They still require the external source JAR with SHA-256
`bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402`.
No fully converted/playable Bamboo JAR is claimed here.

The converter revision advances beyond the previous documented .130 construction checkpoint
to invalidate cached conversions made before these safety checks. Code, tests, revision and
this handoff are submitted together with one branch update; no CI-triggering intermediate
file-by-file commits are used.
