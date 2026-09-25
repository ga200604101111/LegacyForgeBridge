# Session 2026-09-16 - Raw GUI fixture validation

Branch: `feature/generic-conversion-bamboo-corpus2`

## CI finding

Run 305 on `55f8195ca0f2c44bf6bd164479cdb0086365314b` compiled production and test sources
successfully. All 17 newly added operand/control-flow regressions passed with the project's pinned
ASM and JUnit dependencies. The full task reported 344 tests, 1 failure and 11 skips.

The failure was the older `LegacySingleInputProcessorRawTileGuiTest`. Its handwritten MethodNodes
had no maxStack/maxLocals metadata, and its supposed narrowing constructor only performed
`ALOAD 2; CHECKCAST; POP`, never binding a tile field. Its drawing fixture also omitted the GUI
receiver and accessed tile fields on the GUI itself. These were sufficient for the old
instruction-presence heuristic, but not the new operand proof.

## Test-only correction

The fixture now constructs its Menu, invokes the GuiContainer superclass constructor and stores
the narrowed TileEntity parameter in a real GUI-owned tile field. The drawing instructions supply
their receiver and read through that field. A ClassWriter COMPUTE_MAXS / ClassReader round trip
supplies the maxima that real class files contain.

The original positive expectation remains unchanged: raw World TileEntity handoff without a
redundant handler-side CHECKCAST must select the correct GUI. A new assertion checks actual
constructor-to-field narrowing, and an additional JUnit test checks both fixture classes' methods
with ASM BasicVerifier.

Local fixture-probe execution verified the handler, constructor and drawing operand stacks, plus
GUI selection and field binding. No production proof gate was relaxed or changed in this follow-up.
The converter revision therefore remains `2026-09-16.51`.

There are now 18 newly added regression methods across the operand-hardening and fixture follow-up,
plus the repaired pre-existing raw-GUI test. The 11 CI skips belong to the optional external
RpgToolBehaviorCorpusTest inputs; they are not reported as passes. Exact Bamboo tests use their
separate exact-corpus task and remain outside the normal build's execution scope.
