# 2026-10-07 — Twilight Forest 2.3.8 compatibility, Part 2B checkpoint

Branch: `feature/generic-conversion-iyamato-corpus3`

Scope: registration-side global-ID condition and source-proven entity-egg metadata.
This checkpoint does not claim spawn-egg right-click/dispenser behavior, entity AI, DataWatcher,
renderers, TileEntities, dimensions, structures or world generation.

## Optional global entity registration

`TFCreatures.registerTFCreature` contains an optional
`EntityRegistry.registerGlobalEntityID(Class,String,id)` call guarded by
`TwilightForestMod.creatureCompatibility`.

In the supplied 2.3.8 source JAR:

- `creatureCompatibility` is declared static boolean;
- the JAR contains no `PUTSTATIC` for that field;
- normal JVM initialization therefore leaves the value false.

The optional global-ID branch is consequently not executed by the ordinary source lifecycle.

The generic lifecycle analyzer is deliberately not taught to report
`registerGlobalEntityID` unconditionally because its current helper-template propagation does not
prove branch predicates. Reporting the call without proving the guard would be a false positive.

## Entity egg definition census

Of the 58 helper-wrapped creature registrations:

- 54 call the overload carrying two colour ints;
- 4 use the no-colour overload.

Direct bytecode census of the 54 coloured callsites shows:

- 54 unique entity classes;
- 54 unique entity names;
- every primary/secondary value is within 0x000000..0xFFFFFF.

The source helper writes:

`entityEggs.put(Integer.valueOf(id), new EggInfo(id, primaryColor, secondaryColor))`

after the source mod-entity registration.

The egg-info constructor stores the three int constructor arguments into three instance int fields.

## Generic proof

`LegacyEntityEggAnalyzer` admits only the structural family where:

1. one source helper calls the standard Forge 1.7.10 `registerModEntity`;
2. the same source id is used as the exact static Map/HashMap key;
3. the map value is constructed by a source `(III)V` class;
4. that constructor stores argument 1/2/3 into source int instance fields;
5. constructor argument 1 is the same registration id;
6. primary/secondary color arguments propagate to an FML lifecycle root;
7. class/name join uniquely to a source-proven lifecycle entity registration and numeric id;
8. both colours fit the unsigned 24-bit RGB range.

No Twilight Forest package/class/helper/field/entity/registry name is used for admission.

A renamed synthetic regression is present, together with a negative case whose egg-info id flow is
changed and must be rejected.

## Candidate handoff

`LegacyEntityEggAnalysisPass` writes:

`legacyforgebridge/entity-eggs.json`

with source hash plus the proven registry name, source entity class, numeric entity id, two colours,
and source provenance. The pass is wired immediately after lifecycle analysis.

A separate materialization regression verifies that numeric id and both RGB values are persisted
unchanged.

Relevant commits:

- `4a7454b5b16cce544ab22928b0cc218e9bd92bc2` — structural entity-egg analyzer.
- `c51cfc8b4c31ed4765f528d71473630c3b6bd93b` — valid ASM fixture completion.
- `6b61d5ec7cad334de9be5f335442b3e8f9f49b8d` — mismatched egg-id negative regression.
- `1c27007a6c6e2cc2f9ea054c0932e14bb4e8be63` — candidate egg sidecar pass + engine wiring.
- `03ffd416cfc873fb13d2a207dce002e032d9898e` — sidecar identity/color regression.

## Validation boundary

Performed:

- exact uploaded JAR callsite census with `javap`;
- no-source-write check for `creatureCompatibility`;
- 54 coloured / 4 uncoloured helper split;
- RGB range and class/name uniqueness census;
- source/test changes committed on the required branch.

Not claimed:

- no GitHub Actions run, per repository policy;
- no full Gradle/Loom build;
- no Minecraft runtime test;
- spawn egg interaction/dispenser behavior is not yet converted.

## Next checkpoint

Part 2C should begin with DataWatcher schema compatibility only. It should first run the existing
`LegacyEntityDataWatcherAnalyzer` against all 77 source registrations and classify:

- fully proven schemas;
- source entities with no custom watcher entries;
- exact unsupported watcher default/access shapes.

Do not mix rendering or AI into the first DataWatcher checkpoint.
