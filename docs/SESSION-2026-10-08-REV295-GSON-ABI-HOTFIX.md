# 2026-10-08 — rev295: fix universal Gson method linkage failure blocking all four legacy sources

Branch: `feature/generic-conversion-iyamato-corpus3`.

## Reported real-game failure

User rev294 diagnostic summary ended with `state=ERROR`, `conversionFinished=true`, `total=4`, `errorCount=4` (Bamboo, RPGTool, iYAMATO, and Twilight Forest), all `conversion=FAILED`. Each failed inside `LegacyItemSemanticsRecoveryPass` with `java.lang.NoSuchMethodError` calling one of:

- `com.google.gson.JsonObject.addProperty(String,boolean)`
- `com.google.gson.JsonObject.addProperty(String,float)`
- `com.google.gson.JsonObject.addProperty(String,int)`

The erroneous local rev293 compile-only `JsonObject` test stub declared primitive overloads that **do not exist in real Gson**. The official API expects boxed `Boolean`, `Number`, `String`, or `Character`; compiling against that inaccurate stub produced a class that loads but fails to link as soon as a conversion reaches those instructions.

**This is a common LegacyForgeBridge bug, not four unrelated mod-corruption incidents.**

## Actual source and binary fix

The existing generic `LegacyItemSemanticsRecoveryPass.java` was corrected at **19** boolean/int/float `addProperty` source call sites to pass explicit boxed wrappers (`(Number)Integer.valueOf(...)`, `(Number)Float.valueOf(...)`, `Boolean.TRUE/FALSE`). The class was **recompiled under JDK 21 against faithful Gson API signature-only compile stubs**, with all original rev294 production class dependencies present.

The newly compiled `.class` now calls only:

- `JsonObject.addProperty(String,String)`
- `JsonObject.addProperty(String,Number)`
- `JsonObject.addProperty(String,Boolean)`

The legacy local Gson stub **is not in the delivered JAR**.

A new `tools/release/verify_gson_abi.py` scans constant-pool method references across **all** packaged classes and rejects unsupported primitive `JsonObject.addProperty` or `JsonArray.add` descriptors. Nine source-independent `tools/release/test_verify_gson_abi.py` regressions were executed and passed. The actual rev294 failed this scan with exactly three invalid signatures in one class; the new rev295 passed with zero invalid signatures.

## Complete rev295 main JAR assembly

Produced locally:

`legacyforgebridge-0.2.0-alpha.27-rev295-gson-linkage-hotfix-preview.jar`

- Exact baseline: `legacyforgebridge-0.2.0-alpha.27-rev294-generic-load-diagnostics-preview.jar`
- Baseline SHA-256: `c12ab8b56d60c73b90e2eb817546aef839f62c31d4d7f30e571f24966eab8601`
- New complete JAR SHA-256: `1fa93b059f83e747757a99b14ebe87778f227db381705df2f7987b7e83eea467`
- Size: **4,998,129 bytes**; **1,843** compiled Java class entries.
- Exact ZIP delta: **4 explicitly reviewed replacements + 1 new build provenance file, 0 deletions**. The four replacements are the bad pass class, `BuildInfo.class`, `fabric.mod.json`, `META-INF/MANIFEST.MF`. The one addition is `legacyforgebridge/rev295-gson-linkage-hotfix-build.json`.
- All **1,927** other existing archive entries, including embedded desktop helper and nested dependencies, were preserved byte-for-byte. `LegacyItemSemanticsRecoveryPass$SourceField.class` is **identical** between source recompilation and baseline, so it is intentionally left untouched.
- Version: `0.2.0-alpha.27-corpus4-local.48-rev295-gson-linkage-hotfix.1`
- Converter revision: `2026-10-08.295-gson-linkage-hotfix`, deliberately bumped so stale per-mod cached results are re-evaluated.
- `LFB-Local-Full-Loom-Build=false` retained; release method states javac21 with boxed Gson ABI stub, audited constant-string BuildInfo edit and deterministic complete-archive overlay. No old JAR renaming or hidden Gradle/Loom claim.

The deterministic complete-main overlay guard returned **`PASS_STRUCTURAL_ONLY`**. All archive entries passed ZIP CRC.

## Executed regression evidence

1. **Exact root cause reproduced.** Run the old packaged `LegacyItemSemanticsRecoveryPass` with *faithful*, boxed-only Gson API test double and original Twilight Forest 1.7.10 JAR: throws `NoSuchMethodError: void JsonObject.addProperty(String,int)` at original source line 97.
2. **Fixed compiled source smoke.** Recompile corrected source with boxed-only Gson API, then run on original uploaded `twilightforest-1.7.10-2.3.8-tw.jar`: the pass completes and emits expected material/name data.
3. **Fixed *packaged* main JAR smoke.** Load the pass **from the generated rev295 JAR** in a Java 21 harness and run the same real Twilight Forest original-JAR conversion stage. Output confirmed **110 item name keys**, **35 block name keys**, **28 source armor slots**, **28 source armor protection values**, and **7 sword base attack attributes**; no duplicate `item.item.` keys remain in staged content. The test class CodeSource explicitly showed the real rev295 JAR.
4. **ABI release audit.** rev294: **1,843 classes / 601 target Gson method refs / 3 unsupported signatures -> FAIL**. rev295: **1,843 classes / 600 refs / 0 unsupported -> PASS**.
5. **Independent test suite:** 9/9 Gson signature guard regressions passed.
6. **Complete-main structural guard:** `PASS_STRUCTURAL_ONLY`, all old classes, original DesktopHelper, source and server files unchanged.

For the offline Java launch the original source material analyzer was substituted with the previous Java21 **JDK-internal ASM-compatible test build**; Gson was **a test double with the actual documented overload method descriptors**, not the real Fabric loader Gson binary. The final shipped JAR includes neither test library. **No full Gradle/Loom, real Minecraft/Fabric/Prism client launch, actual Forge multiplayer acceptance, or direct source testing of the user's Bamboo/RPGTool/iYAMATO JARs is claimed.** Their shared error path is fixed, but downstream problems could appear once they advance past this blocking pass.

## User test steps and safety

1. Backup the PrismLauncher instance.
2. In `mods`, remove other LegacyForgeBridge rev260–294 main JARs. Install **only** rev295 complete main.
3. Keep original Forge 1.7.10 mod binaries in `old-mods`; do not manually duplicate old managed `*-lfb.jar` into `mods`.
4. Launch (up to several minutes due to forced cache fingerprint refresh); if Prism's automatic restart is still unreliable, manually reopen Prism instance.
5. Compare new per-mod status: the `LegacyItemSemanticsRecoveryPass NoSuchMethodError` must be gone, but `PARTIAL` or other blockers may remain. **Never force-install `loaderSafe=false`**.
6. If any mod still fails, provide the new rev295 diagnostic summary, `legacy-cache/conversion-state.json`, `legacy-cache/reports/conversion-diagnostics-rev213.zip` and `logs/latest.log`, retaining exception stacktraces.

Development policy: only this feature branch; every commit `[skip ci] [skip actions]`; no GitHub Actions, PR, tags, release, force push, main/Bamboo or original mod/server mutation.
