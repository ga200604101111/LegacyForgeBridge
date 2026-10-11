# LegacyForgeBridge rev315 — bytecode linkage scanner hotfix

## Observed Bamboo failure

The supplied pre-launch report for `Bamboo-2.6.8.5.jar` ends at `LFB-CONVERT-BYTECODE-0004`: `GeneratedContentBehavior.class: Invalid/unsupported JVM instruction 235 at 1936`, after 43 wrapper classes were generated. Other `MANUAL_REQUIRED` Forge API warnings are independent.

## Root cause, independently reproduced

The standalone rev314 `LFB-Converter.jar` contains an independent classfile parser (`LegacyGeneratedBytecodeLinkageAnalyzer.validateInstructions`). It incorrectly treats opcodes 192 (`checkcast`) and 193 (`instanceof`) as zero-operand because its first branch uses the inclusive range 190..195. Both actually consume a two-byte constant-pool index. The byte `0xEB` (`235`) in such an index is then misread as an invalid new instruction. Synthetic compiled Java 21 classes with `checkcast #235` and `instanceof #235` reproduce that exact invalid-instruction diagnostic.

## Narrow fix

Move 192 and 193 to the already-existing two-operand case by changing the zero-operand upper bound from 195 to 191. Explicitly preserve 194/195 (`monitorenter`/`monitorexit`) as zero-operand. This is a one-class patch inside **LFB-Converter.jar only**. No audit bypass, forbidden-prefix relaxation, generator/gameplay change, Forge mod modification, protocol change, runtime or Bootstrap JAR change.

Java 21 build-only method-patching source: `tools/PatchOpcodeLengths.java`. Compile with JDK `--add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED` and `--add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED` for both `javac` and `java`. The host-JDK ASM tool is **not packaged** with LFB.

## Checks and provenance

- Baseline rev314 install ZIP SHA-256: `3803e4126dc372da46f83dc04affc4ba48c75b7897d3267af4925cf90d556fda`.
- Base converter SHA-256: `ce362e2b924fa87c54ebb360ad90bd23e1e8bad1ad8ccac82e0f546a3ffceb08`.
- Patched converter SHA-256: `ba882b8cfd281990fe97f34273263e03ed5944ee9340b42f37dac3bcc961e6e1`.
- Complete install ZIP `LFB_rev315_LinkageScanner_Fix_Install.zip`: 9,949,090 bytes, SHA-256 `76b3b9abfdadf66e4558093eaf4a630749b8b11b536c07715bf69ce039889e7a` (delivered in ChatGPT, **not stored in Git**).
- Two independent builds of complete ZIP byte-identical; ZIP integrity and checksums valid.
- `checkcast #235` and `instanceof #235`: rev314 rejects legal fixture, rev315 accepts. `monitorenter`/`monitorexit`, dense/sparse switches: behavior unchanged. Forbidden Forge reference still detected. Genuinely corrupted opcode 235 still rejected.

## Boundaries

No user Bamboo JAR or produced `GeneratedContentBehavior.class` was available for real full-mod retest; Minecraft 1.21.11 game startup and original Forge 1.7.10 server not run. This proves the scanner defect and a narrow repair, **not** live gameplay compatibility. The original rev314 Bootstrap/Runtime classes still self-identify as rev314; `lfb/bundle.properties` and converter SHA identify the hotfix package `rev315-linkage-scanner-opcodes.1`.

Only `feature/generic-conversion-iyamato-corpus3` is modified. No PR, tag, CI/Actions dispatch, force push, main or Bamboo changes.
