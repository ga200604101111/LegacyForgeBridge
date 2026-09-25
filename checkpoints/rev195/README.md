# rev195 - repair Property verification and world-clock linkage

**rev194 is superseded because its GridPot renderer fails JVM verification.**
Replacement: `legacyforgebridge-0.2.0-alpha.27-rev195.jar`
SHA-256: `e5e7a60ae49ee1b934badd9e791c82ae13fd4d20c15877619a2caa39d9c2ec78`

## Root causes and actual changes

The previous local compile-only declarations incorrectly treated `net.minecraft.class_2746` as the generic Property base. In 1.21.11 it is BooleanProperty; IntegerProperty (`class_2758`) and BooleanProperty are siblings under Property (`class_2769`). This emitted an invalid BlockState `method_28498(BooleanProperty)` call at bytecode offset 138, matching the supplied 18:21:48 resource reload error. Correct declarations and an explicit `Property<?>` source variable now produce the correct parameter descriptor. The renderer still submits native world block models; it is not disabled or reverted to flat icons.

The follow-up ABI audit also found an obsolete world-clock reference in the wind-chime renderer. 1.21.11 inherits `getGameTime` / Yarn `getTime` from LevelAccessor / WorldAccess, intermediary `class_1936.method_75260()J`. The local projection had emitted `ClientLevel.method_8510()J`. It is now recompiled with the current inherited default method. This second issue was found by source/binary inspection and a local negative control, not in the user's pasted stack trace.

Only BuildInfo, the GridPot renderer extraction body and the suspended-model extraction body change in the main JAR. The two render methods are recompiled locally and integrated against a pinned rev194 input. All other 1,451 archive entries are byte-identical; no classes are added or removed. Prior tray, plant model, hydration and remote-menu implementation remains. No separate hotfix mod is required.

## Verification performed

- JDK21 `-Xverify:all` with explicitly corrected API fixtures rejects the exact rev194 GridPot class at offset 138 and accepts rev195.
- Executing the exact old suspended-model extraction against a corrected inherited-default clock fixture throws the expected `NoSuchMethodError`. rev195 resolves the new method; changing the world time and clearing the world are also tested.
- All 176 existing recording-fixture regression assertions pass with the corrected Property and clock declarations.
- All 1,439 top-level classes are scanned for the two targeted ABI families: nine Property-presence and three world-clock references, zero invalid references remaining.
- A second targeted local build in a fresh directory produced identical bytes.
- The four-file Java source patch applies cleanly to the isolated rev194 source subset and matches the intended files exactly.

These are API-fixture tests, not an actual Minecraft/Fabric/Mixin launch, full Gradle/Loom build, live server test, or certification that every rev194 gameplay feature works. The earlier tests used an incorrect API model and therefore missed the defect; both old-release negative controls are now mandatory. Optional GridPot insertion-predicate conflicts remain unchanged.

## Install

Close the client, replace the old main LegacyForgeBridge JAR with rev195, and keep other dependencies and original `old-mods` files. Remove the obsolete `lfb-visual-stack-hotfix` add-on if still present. Complete conversion and restart when requested; converter fingerprint is `2026-09-25.195-gridpot-property-abi`. Do not delete settings, resource packs or server data. The failed reload may have deselected resource packs; re-enable desired packs after startup succeeds.

## Source and builder

Root `src/` remains the rev188 checkpoint base. Do not compile it directly and label it rev195.

```
python checkpoints/rev195/restore.py --output ../LegacyForgeBridge-rev195
python checkpoints/rev195/check_property_abi.py path/to/legacyforgebridge-0.2.0-alpha.27-rev195.jar
```

This checkpoint includes the normal-symbol source fix, corrected fixture definitions, two JVM regression sources, targeted ABI guard and method-body integration tool. The downloadable source/build ZIP additionally contains the complete no-network targeted builder, compile-only declarations, projected source, recording fixtures and generated test logs. Its `local-build/build195.py` requires JDK21, the exact rev194 JAR and an ASM/Gson tools classpath. No fixture or dependency binaries are packaged into the delivered mod. Source restoration itself does not run a build or Actions.

API references:
- https://maven.fabricmc.net/docs/yarn-1.21.11+build.4/net/minecraft/state/State.html
- https://maven.fabricmc.net/docs/yarn-1.21.11+build.4/net/minecraft/world/WorldAccess.html
