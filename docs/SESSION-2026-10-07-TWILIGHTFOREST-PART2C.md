# 2026-10-07 — Twilight Forest 2.3.8 compatibility, Part 2C checkpoint

Branch: `feature/generic-conversion-iyamato-corpus3`

Corpus:
`twilightforest-1.7.10-2.3.8-tw.jar`

SHA-256:
`1aa2c191170ff707499c42cd58bdcc69f2772b59014cb684ba99a6d1e6540589`

Scope: DataWatcher schema definition only.

This checkpoint does not claim watcher read/write behavior, entity AI, rendering, spawn behavior,
TileEntities, dimensions, structures or world generation.

## Source census

Among the 77 source-proven `registerModEntity` registrations:

- 25 registered entity classes directly override `entityInit / func_70088_a`;
- 21 of those directly add one or more custom DataWatcher entries;
- 4 override the method but add no custom watcher entry;
- source-owned superclass definitions must also be considered for registered subclasses.

Across the complete registered hierarchy, **23 registered entities have a non-empty source-owned
custom watcher schema** and **54 have no source-owned custom watcher entries**.

No registered Twilight Forest entity uses `DataWatcher.addObjectByDataType / func_82709_a` in its
source schema.

## rev269 generic fix

Two source defaults use legacy constructor boxing instead of `valueOf`:

- Hydra index 18: `new Integer(MAX_HEALTH)`, with source `MAX_HEALTH = 360`;
- Lich index 19: `new Integer(100)`.

The generic analyzer now admits constructor-boxed Byte, Short, Integer and Float defaults only when
the exact wrapper allocation, one-primitive constructor, receiver provenance and scalar argument are
all source-proven.

No arbitrary object construction is admitted.

Relevant commit:

- `ffb6329f55068ebb84dfebc60d1816ec3797108e` — constructor-boxed legacy watcher defaults.

## Whole-corpus schema validation

The current source proof was independently exercised over the uploaded exact corpus, including
source-owned superclass traversal.

Result:

- registered entities: **77**
- schema proofs accepted: **77**
- schema proofs rejected: **0**
- non-empty source-owned schemas: **23**
- empty source-owned schemas: **54**

### Non-empty proven schemas

- EntityTFAdherent: 17 byte=0
- EntityTFBlockGoblin: 17 byte=0, 18 byte=0
- EntityTFCharmEffect: 16 int=0, 17 string=""
- EntityTFFireBeetle: 17 byte=0
- EntityTFGoblinKnightLower: 17 byte=0
- EntityTFGoblinKnightUpper: 17 byte=0
- EntityTFKobold: 17 byte=0
- EntityTFMinotaur: 17 byte=0
- EntityTFTroll: 16 byte=0
- EntityTFWinterWolf: 21 byte=0
- EntityTFYeti: 16 byte=0
- EntityTFHydra: 17 byte=0, 18 int=360
- EntityTFHydraHead: inherited 17 string="" from EntityTFHydraPart, plus 18 byte=0, 19 byte=0
- EntityTFKnightPhantom: 17 byte=0
- EntityTFLich: 21 byte=0, 17 byte=0, 18 byte=0, 19 int=100, 20 byte=0
- EntityTFMinoshroom: inherited 17 byte=0 from EntityTFMinotaur
- EntityTFSnowQueen: 21 byte=0, 22 byte=0
- EntityTFUrGhast: 18 byte=0
- EntityTFYetiAlpha: 16 byte=0, 17 byte=0
- EntityTFBunny: 16 byte=0
- EntityTFQuestRam: 16 int=0, 17 byte=0
- EntityTFRaven: inherited 16 byte=0, 17 byte=0 from EntityTFTinyBird
- EntityTFTinyBird: 16 byte=0, 17 byte=0

The inheritance cases are important: the analyzer does not require the registered concrete class
itself to declare every watcher definition.

## Validation boundary

Performed:

- exact uploaded JAR hierarchy scan;
- verifier-source frame analysis of source `entityInit` methods;
- source-super traversal;
- exact DataWatcher receiver proof;
- exact index proof;
- legacy wrapper/default decoding;
- duplicate-index/range checks.

No GitHub Actions run was triggered, per repository policy.
No full Gradle/Loom build or Minecraft runtime test is claimed.

## Next: Part 2D

The next slice should cover **DataWatcher access behavior**, not schema definition:

1. read methods such as byte/int/string watcher getters;
2. update methods using `updateObject / func_75692_b`;
3. exact watcher index/type agreement with the Part 2C schema;
4. inherited/source helper access paths;
5. fail closed on dynamic index or mismatched type.

Do not mix renderer or AI conversion into the first Part 2D checkpoint.
