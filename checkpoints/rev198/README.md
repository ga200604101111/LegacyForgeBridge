# rev198 experimental — iYAMATO corpus trial

**This is not a completed iYAMATO port or a gameplay-ready converted mod.** The normal baseline remains the merged rev197 on `main`. Experimental work is confined to `feature/generic-conversion-iyamato-corpus3`.

Main consolidation: PR #17, merge commit `912cb905ce61e97ccdd6e54e2a5a26b3b6ae8c5f`. The only main-only change was the `dev.longyu` to `dev.yinghuang` namespace rename. It was reviewed and reconciled with the newer feature implementation while preserving both histories. No forced ref update or original-branch deletion was used.

## Actual supplied corpus

`iYAMATOs-Mod-1.7.10.jar`, 1,029,340 bytes; SHA-256 `35a79ca5a034fd6cda4fe4ee1f658c4ce058f9b7ec026516e76363ebdf89635e`.

Recovered FML ID `iymts_mod`, version `1.7.10-1.6.8`, display metadata `iYAMATO's mod`; 243 classes, 157 PNG resources, 99 independently registered items and 4 blocks. The archive has 30 mob and 24 weapon/projectile/helper entity classes. Entity registrations include configuration-dependent branches; these counts are not proof that every class is enabled on every server. Metadata declares no mod dependencies and the audit detects no CoreMod references; this is not a universal dependency guarantee.

## Shared changes, not mod-name selectors

1. `LegacyRegistryAnalyzer`: recover literal Block names through proven vanilla MCP/SRG setters/getters and constant substring calls, preserving allocation identity across admitted fluent setters, static fields and helper-argument paths. Distinguish Block `tile.*` names from Item `item.*` names. Reject overridden methods, missing ancestry, unknown fluent effects, unknown substring bounds and mixed unknown name paths. This recovers the 4 actual block registrations and their field bindings, without hardcoding their names. Reference locals and arbitrary dynamic code remain unsupported.
2. `LegacyBehaviorCompiler`: validate constructor bootstrap arguments against the actual JVM descriptor before admitting a binding. The old release generated a String where a Material object was required; the negative control reproduces its VerifyError. Reject only the unsupported binding with diagnostics instead of generating invalid bytecode, inventing a material or substituting an arbitrary null. This is not implementation of material-dependent armor/tools.
3. Update converter fingerprint to `2026-09-26.198-generic-block-names-and-bootstrap-arguments`.

## Actual trial boundary

The complete released engine could not finish in this container because `com.mojang.serialization.Codec` was unavailable in the host classpath. A diagnostic runner reads the release plan and executes 104 of 105 passes, explicitly omitting only `LegacyRecipeMaterializationPass`. It forces `installable=false` and never packages a candidate mod. Its result is **PARTIAL**, not CONVERTED.

Current unresolved results: 103 model identities remain unresolved; source-proven default icons recovered: 0; all 54 entity registration candidates are skipped, with 0 admitted runtime entity rules. There are 19 generated behavior/identity item bindings, not 19 fully supported items. Completing a diagnostic pass can mean producing exclusions or finding no matching source family.

The lifecycle item allocation/texture provenance still needs expansion. Projectile registrations use a static counter; monster helpers use `findGlobalUniqueEntityId()` and pass the result to mod-entity registration. The latter depends on source configuration and the shared allocation context. Do not guess entity IDs from declaration order or a configuration field, impersonate another entity, or advertise success from the FML mod list alone. Client models, watcher state and weapon interactions still need real adaptation; server AI/damage/world generation remain on the original server.

## Local validation

- 74 block-name assertions across 40 positive/negative cases; 27 constructor-argument assertions. Total 101 new assertions.
- Actual released rev197 reproduces the constructor String-to-Material VerifyError; the revised compiler rejects that binding and preserves an unrelated valid binding.
- 36 normalized baseline class files match the release; 29 changed/added classes pass pure-class JVM verification with `-Xverify:all`, plus 255 ASM BasicVerifier methods. 128 exposed members remain; 1,446 prior JAR entries are byte-identical.
- Original Bamboo registry and behavior analyses are unchanged: 100 registrations, 99 field bindings, 87 behavior item bindings, 3 event bindings and 40 byte-identical generated behavior classes. This is not a complete Bamboo game test. No new RPGTool1 source test was run.
- Independent targeted builds and a build from the checksum-verified compressed source payload produce identical JAR bytes. The three-source-file delta and eight validation sources were checked in an isolated directory. The entire rev189–198 restoration chain was not rerun this turn.

No Minecraft compile-time API substitutes were needed for the three pure-Java modifications. This does not validate actual Minecraft/Fabric/Mixin runtime linkage, rendering, networking or gameplay. No clean Gradle/Loom build or live-server test was performed. No Actions build was requested.

## Artifact and sources

Experimental main bridge: `legacyforgebridge-0.2.0-alpha.27-rev198-experimental.jar`, 3,735,329 bytes.
SHA-256: `0208f6d22e19875bbbff52d8d65fef2ebfb455866fff548ba72b64f4cf31cdb9`.

The artifact includes the integrated rev197 baseline plus the two generic fixes; it is **not** an `iymts_mod` converted JAR. Keep normal play on the tested rev197 until the new corpus is ready. Do not place the original Forge JAR directly into modern Fabric `mods`.

Root `src/` still requires cumulative checkpoint restoration. To obtain the new source in a fresh directory outside the checkout:

```sh
python checkpoints/rev198/restore.py --output ../LegacyForgeBridge-rev198
```

This verifies payload and source hashes, restores rev197 and applies the three-file delta, then installs the local builder/tests under `tools/local-validation/rev198`. It does not build or run Actions. The downloaded source/build ZIP also includes the complete three modified source files, readable source.patch and diagnostic logs. It does not include the supplied third-party mod, decompiled third-party source, dependency JARs or Minecraft classes.

Targeted builder requires JDK21, the exact rev197 input JAR and a real ASM/Gson classpath:

```sh
python tools/local-validation/rev198/build198.py --base /path/to/rev197.jar --tools-cp '/path/to/asm-and-gson-classpath' --source-root src/main/java --work /path/to/new-build-directory --output /path/to/rev198-experimental.jar
```

See `Trial-Report.zh-TW.md` and `verification.json` for scope and observed failures.
