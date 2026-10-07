# 2026-10-07 — Twilight Forest 2.3.8 compatibility, Part 2D checkpoint

Branch: `feature/generic-conversion-iyamato-corpus3`

Corpus:
`twilightforest-1.7.10-2.3.8-tw.jar`

SHA-256:
`1aa2c191170ff707499c42cd58bdcc69f2772b59014cb684ba99a6d1e6540589`

Scope: DataWatcher runtime read/write access proof and source-wide call closure.

This checkpoint does not claim entity AI, rendering, vanilla-base FML spawn metadata completeness,
TileEntities, dimensions, structures or world generation.

## Starting point

Part 2C proved the DataWatcher definition schema for all 77 registered entities:

- 77/77 schema proofs accepted;
- 23 entities have non-empty source-owned custom watcher schemas;
- 54 have no source-owned custom watcher entries.

The first full runtime-access audit found 94 direct DataWatcher calls outside entityInit:

- 49 updateObject calls;
- 40 byte getters;
- 3 int getters;
- 2 string getters;
- no ItemStack watcher getter;
- no force-dirty call;
- no unsupported addObjectByDataType runtime surface.

## rev270 — reference-valued write provenance

The initial access analyzer admitted 72/77 entity surfaces.

Two failures were generic analysis gaps:

- EntityTFHydraHead inherits a source method that writes watcher 17 from a String parameter;
- EntityTFCharmEffect writes watcher 17 from a String parameter.

`LegacyEntityDataWatcherAccessAnalyzer` now follows exact reference-value provenance through:

- JVM method parameter descriptors;
- verifier source-frame local aliases;
- CHECKCAST;
- already-supported wrapper/string return descriptors.

Unknown merges and unsupported reference types remain fail closed.

Commit:

`6547c7f94aab618bbc2d5a3342ef084ca92c87e6`

Exact-corpus result after rev270:

- entity access surfaces: 74/77;
- only MiniGhast / TowerGhast / UrGhast remained rejected.

## rev271 — pinned vanilla Ghast watcher

The remaining three entity-level failures all use vanilla Minecraft 1.7.10 EntityGhast watcher 16.

The platform table `LegacyVanillaEntityDataWatcher1710` was introduced with the pinned definition:

- external base: `net/minecraft/entity/monster/EntityGhast`
- legacy index: 16
- kind: byte
- default: 0

The definition analyzer imports the entry only when the source-mod inheritance chain actually reaches
that exact external vanilla base. It is then processed by the ordinary duplicate/type/runtime-plan
pipeline; it is not treated as Twilight Forest-owned state.

Commit:

`e891611cea0e670dcd4d23175bc89e689301f2f2`

Exact-corpus result after rev271:

- entity access surfaces: **77/77**;
- entity-level failures: **0**;
- source-wide runtime calls accounted: 92/94.

The only unresolved method was:

`twilightforest/client/TFClientEvents.renderLivingPost(RenderLivingEvent.Post)`

with two direct vanilla EntityLivingBase reads:

- index 7 as int;
- index 8 as byte.

## rev272 — platform-owned getter closure

The pinned vanilla table was extended with Minecraft 1.7.10 EntityLivingBase watcher definitions:

- index 6 -> float, default 1.0;
- index 7 -> int, default 0;
- index 8 -> byte, default 0;
- index 9 -> byte, default 0.

The global closure analyzer may account a platform-owned getter outside a registered source entity
lineage only when all of these are proven:

1. the DataWatcher call is an already-supported typed getter;
2. the index is one direct integer constant;
3. the DataWatcher receiver comes from a direct non-static
   `getDataWatcher / func_70096_w` call;
4. that accessor invocation owner is an exact vanilla `net/minecraft/entity/...` class;
5. the pinned platform table contains the exact owner/index/value-kind tuple.

Dynamic indices, mismatched getter types, arbitrary watcher GETFIELD receivers and mod-owned
receiver classes do not pass this shortcut.

Commit:

`175995f658a3e3b4d4a388049d3adc466cfd5dae`

A follow-up safety hardening prevents aggregate method-level counts from adding entity-lineage and
platform counts together. Because the current closure IR does not identify individual callsites,
mixed methods stay fail closed instead of risking double-counting one call and masking another.

Commit:

`f9289adbaa177fcbc970e5c8b1b92d237947f582`

## Final exact-corpus validation

A local Java 21 verifier/source-frame audit was rerun against the uploaded exact Twilight Forest JAR
after applying the rev272 platform-closure rules.

Result:

```text
ACCESS_SUMMARY  ok=77  fail=0
PROVEN_ACCESS_INSTANCES=107
GLOBAL  total=94  proven=94  unresolved_methods=0
```

Therefore Part 2D closes with:

- **77/77 registered entity DataWatcher access surfaces proven**;
- **94/94 source runtime DataWatcher calls accounted**;
- **0 unresolved source methods**;
- source-wide DataWatcher call closure complete.

Local audit output:
`/mnt/data/tf_access_audit_rev272.txt`

## Validation boundary

Performed:

- exact uploaded JAR bytecode scan;
- source verifier-frame read/write provenance;
- source inheritance/helper traversal;
- pinned vanilla 1.7.10 Ghast/LivingBase watcher checks;
- final whole-JAR runtime-call closure recount.

Not claimed:

- no GitHub Actions run, per repository policy;
- no full current Gradle/Loom build;
- no Minecraft client launch;
- no claim that FML spawn metadata for every vanilla living-entity base is complete yet.

## Next: Part 2E — vanilla base watcher inheritance at FML spawn

This is a distinct compatibility boundary discovered during Part 2D.

The current plain-entity FML spawn path accepts legacy Entity base watchers 0/1 specially and
requires every other incoming watcher to be consumed by the generated watcher bridge.

Twilight Forest entities inherit many vanilla 1.7.10 entity classes. Their initial FML spawn
metadata may therefore contain platform-owned watcher definitions beyond 0/1 even when the source
mod never reads or writes those indices.

Part 2E must:

1. classify the first/external vanilla ancestry of all 77 registered entities;
2. derive the required transitive vanilla 1.7.10 watcher schemas for those bases;
3. merge only source-proven/pinned platform entries needed by each exact ancestry;
4. verify that the generated watcher bridge can consume the complete initial FML watcher envelope;
5. keep projectile/ItemStack watcher families separately gated where necessary.

Do not interpret Part 2D's source-wide closure as full entity spawn/runtime compatibility.
