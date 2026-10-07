# 2026-10-07 — Twilight Forest 2.3.8 compatibility, Part 2A checkpoint

Branch: `feature/generic-conversion-iyamato-corpus3`

Scope: source-proven `EntityRegistry.registerModEntity` registration identity only.
This checkpoint does not claim entity AI, DataWatcher/runtime completeness, rendering, spawning rules,
TileEntity support, dimensions, structures or world generation.

## Exact corpus registration census

`TwilightForestMod.registerCreatures()` contains:

- 54 calls to `TFCreatures.registerTFCreature(Class,String,int,int,int)`;
- 4 calls to `TFCreatures.registerTFCreature(Class,String,int)`;
- 19 direct calls to `EntityRegistry.registerModEntity`.

The two TFCreatures helper families always reach a source `registerModEntity`, so the total is:

**77 source mod-entity registrations.**

The five-argument creature helper delegates to the extended helper with:

- tracking range = 80
- update frequency = 3
- velocity updates = true

The three-argument helper uses the same 80 / 3 / true values directly.

Therefore all 58 helper-wrapped creature registrations have the exact tuple
`(tracking=80, update=3, velocity=true)`.

## Numeric ID provenance

An earlier working assumption that the creature ids were Configuration-driven was incorrect.
Direct bytecode inspection proves:

- 57 registered `idMob*` fields are assigned exact literal ints in lifecycle-reachable
  `loadConfiguration(Configuration)`;
- `idMobRovingCube` has no explicit source write anywhere in the JAR, so the JVM static-int
  default is exactly 0;
- 18 `idVehicleSpawn*` fields are assigned exact literals in `<clinit>`;
- HydraHead passes direct literal id 11.

This yields **77 resolved numeric ids, all unique, with zero collisions**.

Two assigned mob-id fields are not among the registered helper callsites in this corpus:
`idMobBoggard` and `idMobNagaSegment`.

## Direct registration transport tuples

The 19 direct registrations are all source-constant after static-field resolution.

Tuple distribution:

- 150 / 3 / true: 5
- 80 / 3 / true: 4
- 150 / 5 / true: 3
- 80 / 1 / true: 3
- 150 / 3 / false: 1 (HydraHead)
- 150 / 2 / true: 1
- 80 / 2 / true: 1
- 150 / 1 / true: 1

The direct IDs are:
HydraHead=11; vehicle/projectile/effect ids use
1,2,3,4,5,6,7,8,9,10,13,14,15,16,17,18,19,20.

## Generic analyzer changes

`LegacyLifecycleAnalyzer` already propagated lifecycle templates through source helper methods.
Part 2A adds bounded static-field value proof:

1. start from lifecycle roots and `<clinit>`;
2. build the source call-graph reachability set;
3. for a source `GETSTATIC`, inspect every reachable exact `PUTSTATIC owner/name/desc`;
4. require every reachable assignment to resolve and all resolved values to be identical;
5. otherwise retain `FieldValue` and fail closed downstream.

A second narrow rule admits JVM primitive static defaults only when the source-owned static field has
**no PUTSTATIC anywhere in the whole source JAR**. Reference/object defaults are not materialized.

The regression also proves that a true
`Configuration.get(...).getInt()` assignment remains unresolved rather than substituting its
default argument.

Relevant commits:

- `e275b4409c03f01503ec4cf94d99f773456b866c` — lifecycle-reachable static field proof.
- `8556735aeaf35238ee3b51aff567e67a4a4c3b80` — allow exact reachable cross-class static writes.
- `86f335254c8c762f0f0d7710cc180bd6d621dde2` — JVM-default primitive static proof.

## Validation boundary

Performed against the uploaded Twilight Forest 2.3.8 JAR:

- `javap` callsite census;
- literal/static assignment census;
- registration tuple reconstruction;
- uniqueness/collision check of all 77 numeric ids.

Result: **77/77 numeric ids resolved; 77/77 unique; zero unresolved transport tuples.**

No GitHub Actions was triggered, per repository policy.
No full current Gradle/Loom build or Minecraft launch is claimed.

## Next: Part 2B

Keep the next slice restricted to registration-side presentation identity:

- the optional `registerGlobalEntityID` path and its actual source condition;
- source spawn-egg color definitions;
- whether those identities need a modern compatibility representation.

Do not mix DataWatcher/entity behavior/rendering into Part 2B.
