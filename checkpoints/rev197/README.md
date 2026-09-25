# rev197 — repeated books and exact FML identities

Main replacement: `legacyforgebridge-0.2.0-alpha.27-rev197.jar` (3,736,135 bytes).
SHA-256: `e3c28cccbe3fab0b6ac2d003ad26a7e3565a6bf38f22bd4c1b1544b109a885c5`.
Converter: `2026-09-26.197-repeat-books-and-exact-fml-identities`.

## Implemented

- Reopen actual held written books on every explicit main-hand use for 1.7.10 connections. The old code handled only the first source-item-to-book transition. Preserve server pages and inventory objects, with no fake book, slot/title whitelist or extra packet. Initial source use still waits for the real server replacement. Modern targets, offhand and spectators are not intercepted.
- Replace the exact `book_return` instruction fingerprint with bounded symbolic recognition of a tagged written-book return. Support three ItemStack constructors, arbitrary source helper names, locals, static/instance helpers and inherited Item classes. Unknown control flow rejects; this does not execute or certify the NBT helper.
- Resolve exact-case source @Mod IDs separately from display metadata. Match FML 1.7.10 version precedence: annotation, version.properties, raw mcmod version, then 1.0. Keep literal placeholders. Missing metadata no longer changes an annotated mod's ID to a filename guess.
- Emit actual annotated identities even without GameRegistry content. Do not advertise metadata-only or filename-guessed FML identities.
- Reject duplicate wire keys, duplicate loaded mod IDs, normalized registry collisions, ambiguous aliases and non-bijective numeric maps instead of choosing a winner. Conflicts are rejected, not automatically solved.

**Not universally generic:** several furniture/renderer/menu/steam/head families still use constrained layouts, constants and/or exact instruction fingerprints. A dormant RPGTool1 profile remains in source but is not selected by the default engine. Read `Generality-Audit.zh-TW.md`. Passing FML handshake is not proof of arbitrary mod compatibility.

## Verification

The released rev196 reproduces first-open success followed by missing local reopen in the recording environment. Rev197 passes 71 repeated-book assertions including 20 close/reopen cycles; 38 identity checks; 42 source-generalization checks across 18 independent registered book implementations; 8 pure JVM release wire-codec checks; 176 prior runtime and 56 auxiliary recording checks. Total 391, plus 17 original-source mutation/contract checks. Property/clock fixture regressions remain passing. The packaged verification script runs these checks successfully.

17 selected Bamboo conversion passes finish PARTIAL. The full conversion engine was not run this revision. Original mod classes are analyzed as bytes, never executed.

ASM BasicVerifier: 28 changed/new classes, 226 methods. 128 existing public members remain; 604 internal member references resolve; one existing external-superclass boundary is explicitly unverified. All 1,458 top-level classes are scanned for the previous Property/clock ABI families, not universal external linkage. Independent fresh-directory targeted rebuilds are byte-identical. 1,445 prior archive entries, Fabric manifest and Mixin configuration are unchanged.

No complete Gradle/Loom build, Minecraft/Fabric/Mixin launch, real page rendering or live server test. API/host recording fixtures are source-only in the build attachment, never bundled into the main mod. Existing GridPot insertion conflict and previously incomplete gameplay remain unresolved. No Actions build was started.

## Install and source

Close Minecraft; replace the old main LegacyForgeBridge JAR with rev197. Remove the obsolete standalone hotfix if present. Keep dependencies, original old-mods, settings and server data. Let the new fingerprint regenerate converted output, then restart. Existing server-returned recipe books need not be discarded.

Root src remains rev188 plus cumulative checkpoints, not expanded rev197. Restore into a NEW directory outside the checkout:

```
python checkpoints/rev197/restore.py --output ../LegacyForgeBridge-rev197
```

The checksum-guarded payload contains the 15-file normal-symbol source delta and four new regression sources. The downloadable source/build ZIP also includes the full offline targeted builder, compile-only API declarations, projected source, regression scripts and logs. build197.py requires JDK21, the exact rev196 JAR and an ASM/Gson classpath. Neither restoration nor local building runs Actions.
