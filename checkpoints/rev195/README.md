# rev195 - Property verification and world-clock linkage repair

**rev194 is superseded because its GridPot renderer fails JVM verification.**

Current recovered replacement: `legacyforgebridge-0.2.0-alpha.27-rev195.jar`

SHA-256: `5187fbbefa1adb73a0ca9b94d96552636619dec98acdbcf2b240e1da849e627e`

Size: 3,702,399 bytes. This checksum supersedes the earlier unavailable local build recorded as `e5e7a60ae49ee1b934badd9e791c82ae13fd4d20c15877619a2caa39d9c2ec78`. The normal-symbol source patch remains unchanged; the current artifact and verification report are the delivery reference.

## Root causes and changes

The previous compile-only declarations incorrectly treated `net.minecraft.class_2746` as the generic Property base. In 1.21.11 it is BooleanProperty. IntegerProperty (`class_2758`) and BooleanProperty are siblings under Property (`class_2769`). The resulting `method_28498(BooleanProperty)` call in the GridPot renderer is invalid, matching the supplied 18:21:48 resource reload failure at bytecode offset 138. Correct declarations and an explicit `Property<?>` source variable produce the correct descriptor. Native world block models are retained; the renderer is not disabled or reverted to flat icons.

The suspended-model renderer also referenced obsolete `ClientLevel.method_8510()J`. The correct inherited WorldAccess/LevelAccessor clock is `class_1936.method_75260()J`. This second defect was found through inspection and reproduced with an API-fixture negative control, not observed in the user's pasted exception.

Only the two extraction method bodies and BuildInfo change against the exact delivered rev194 JAR. All other 1,451 archive entries are byte-identical. No entries are added or removed. Existing tray placement, plant models, wind-chime implementation, hydration and remote-menu implementation remain; this does not certify those features in live gameplay.

## Current local verification

- Exact rev194 renderer bytecode is rejected by JDK21 `-Xverify:all` under corrected Property fixtures at offset 138. The rebuilt renderer passes the corresponding JVM check.
- Exact rev194 wind extraction produces the expected obsolete-clock `NoSuchMethodError` under corrected inherited-default fixtures. The rebuilt method passes time advance and null-world reset checks.
- All 176 existing recording-fixture assertions pass, including tray, pot, wind, hydration and menu/screen/dispatch cases.
- The expanded audit scans all 1,439 top-level classes and checks 14 Property-presence calls and three current world-clock calls. No invalid references remain in these two targeted families. The audit does not validate every Minecraft member.
- ASM BasicVerifier checks the three changed classes and 17 methods.
- A fresh-directory rebuild produces exactly the same JAR bytes.
- The unchanged four-file source patch applies cleanly with zero fuzz to the locally restored rev194 source tree.
- No Minecraft, fixture or dependency classes are added to the mod.

These are corrected API-fixture tests, not a complete Minecraft/Fabric/Mixin launch, full Gradle/Loom build or live server test. The earlier tests used an incorrect API model and missed the defect. Optional GridPot insertion-predicate conflicts are not changed. No Actions build was requested.

## Install

Close the client, replace the old main LegacyForgeBridge JAR with rev195, and retain dependencies and original `old-mods` files. Remove the obsolete `lfb-visual-stack-hotfix` add-on if present. Complete conversion and restart when requested. Converter fingerprint: `2026-09-25.195-gridpot-property-abi`.

Do not delete settings, resource packs or server data. The failed resource reload may have deselected resource packs; re-enable desired packs after startup succeeds.

## Source and builder

Root `src/` remains the rev188 checkpoint base. Restore before a source build:

```sh
python checkpoints/rev195/restore.py --output ../LegacyForgeBridge-rev195
```

`source.patch` retains the normal-symbol fix. This directory contains the original targeted integration/regression sources and the expanded `AuditDelivery195.java` guard. The delivered source/build ZIP additionally contains the exact recovery builder, its source files, normal-symbol changed sources, the pinned rev194 source/build input archive and current logs. The recovery builder requires Python, JDK21, the exact rev194 main JAR, and a tools classpath containing ASM tree/analysis and Gson. It performs no network request or Actions operation.

API references:
- https://maven.fabricmc.net/docs/yarn-1.21.11+build.4/net/minecraft/state/State.html
- https://maven.fabricmc.net/docs/yarn-1.21.11+build.4/net/minecraft/state/property/BooleanProperty.html
- https://maven.fabricmc.net/docs/yarn-1.21.11+build.4/net/minecraft/world/WorldAccess.html
