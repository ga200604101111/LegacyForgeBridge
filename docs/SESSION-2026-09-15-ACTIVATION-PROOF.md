# P1 activation — finite-input proof and next source-backed slice

Date: 2026-09-15 (Asia/Taipei)

Branch: `feature/generic-conversion-bamboo-corpus2`

Starting commit: `b69f43778f9ee7230e15f2374153e04409c66127`.

This checkpoint hardens the read-only activation compiler. It does **not** claim that held-item mutation, GUI, inventory, TileEntity or the complete Bamboo corpus has been ported. Version numbers, the RPGTool baseline, block-drop work and the runtime interaction hook are unchanged.

## Implemented

- `Program` construction validates the instruction count, local-variable access, forward branch targets and switch table shape. The same validation applies when the existing runtime loader reconstructs a program from a sidecar.
- Every program is evaluated over the entire admitted input domain: 6 clicked sides x 16 metadata values x 2 client/server values x 2 sneaking values = **384 tuples**. This is exhaustive for this read-only input model, not for arbitrary Minecraft behavior. No source class is defined or initialized.
- Divide-by-zero, uninitialized locals, stack underflow and a path without a return reject the program before installation. The diagnostic includes the failing input tuple. A rejected callback does not remove independently valid sibling callbacks.
- Source preflight requires the exact public concrete instance callback descriptor, rejects synchronized/static/native/abstract methods and exception handlers, and bounds source frames to 256 locals / 96 stack slots. Source instruction and switch-entry limits remain bounded at 96.
- ASM `BasicVerifier` checks source stack/local dataflow without loading the legacy hierarchy. Jump targets inside a sequence collapsed into a typed input are rejected; merely preserving instruction indices is not sufficient.
- `tableswitch` keys are generated using a bounded label count and a `long` range check. A one-entry table whose key is `Integer.MAX_VALUE` no longer overflows a `key++` termination condition.
- Integer `LDC` constants now compile to the existing `CONST_INT` instruction without truncation. Other LDC types remain unsupported. The instruction schema and opcode names are unchanged.
- Boolean `IRETURN` uses `(value & 1) != 0`, matching JVMS narrowing, instead of treating every nonzero integer as true.

Specification reference: [JVMS 21, 6.5.ireturn](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-6.html#jvms-6.5.ireturn). Switch layout reference: [6.5.tableswitch](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-6.html#jvms-6.5.tableswitch).

## Regression coverage

`LegacyBlockActivationSafetyTest` adds **22 JUnit test methods**. They cover positive composed inputs, guarded division, large integer constants, extreme and empty switches, invalid source frames and control flow, invalid sidecar programs, whole-callback rejection when effects are unsupported, and a two-registration JAR with one valid and one invalid callback.

A dedicated case fails only at side=5, metadata=15, clientSide=true, sneaking=true: the final tuple of the 384-input enumeration. Another case reproduces the upstream held-item-gated metadata-rotation shape under `foreign/proof`, and asserts that the current read-only compiler rejects it rather than discarding its effects.

Local verification boundary: the development container cannot resolve external hosts or download the project's Gradle dependencies. A standalone Java 21 core harness ran 21 non-registry test methods using the JDK's internal ASM and an assertion shim. This is **not** a full Gradle/JUnit validation. The real registration/JAR integration case and the complete existing test suite must be validated by the branch's `corpus2-p0-verification` workflow for the introducing commit. Do not cite the previous #210 run as proof for this change.

Only the source file, regression tests and this document are included in one Git tree/commit/ref update. Temporary harness code and dependency stubs are not committed or shipped.

## Public Bamboo source cross-check

Pinned upstream repository: `rubnsn/mcmod`, commit `843a7c7dc0532fed00a59e04fcab50736a2414ec`.

### Candidate next slice: held-item-gated local metadata rotation

[BlockDecoPlaster.java](https://github.com/rubnsn/mcmod/blob/843a7c7dc0532fed00a59e04fcab50736a2414ec/java/ruby/bamboo/block/BlockDecoPlaster.java), `onBlockActivated`, performs this complete operation:

1. Obtain the activating player's currently equipped stack.
2. Require a non-null stack and equality with a source-registered Item identity.
3. Read current-position metadata, retaining its low two bits.
4. Derive the axis bits from `(side / 2) << 2` and cycle the low two bits from 0 through 3.
5. Write the combined metadata at the exact callback position with notification flag **2**; ignore the setter's boolean return.
6. Return true after that operation, otherwise false.

This is not a pure consume/pass callback. Recognizing only its boolean return or only its client/server prefix would silently lose gameplay semantics. The next implementation must derive the held-item identity from generic registry/static-field evidence, preserve the metadata expression and notification semantics, and explicitly resolve client prediction versus server authority before enabling writes. It must not hardcode the Bamboo mod ID, item name or block class in production code.

### Not suitable for a read-only shortcut

[BlockMultiPot.java](https://github.com/rubnsn/mcmod/blob/843a7c7dc0532fed00a59e04fcab50736a2414ec/java/ruby/bamboo/block/BlockMultiPot.java), `onBlockActivated`, depends on TileEntity slot state, hit coordinates, held items, creative-mode checks, stack consumption, drops and block updates. That requires the inventory/BlockEntity surface; returning true alone does not implement it.

## Exact-corpus boundary and remaining acceptance work

The expected original JAR hash remains:

`bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402`

This session did not obtain a matching JAR. The pinned public source and unrelated-namespace bytecode fixtures are semantic cross-checks, **not exact-JAR regression**. They do not establish an increased Bamboo callback coverage count, loader-safe installation, in-world rotation, or a playable converted candidate.

The continuation order is: obtain and verify the exact JAR; measure current supported/rejected callbacks with provenance; implement the complete held-item plus metadata-write slice with authority-aware runtime tests; then proceed to inventory/BlockEntity and GUI work. Keep the existing `PARTIAL`/manual-required boundaries until the project's full Definition of Done is satisfied.
