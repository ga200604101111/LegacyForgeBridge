# 2026-10-08 — Twilight Forest / generic projectile Part 2F-3c (rev280)

Branch: `feature/generic-conversion-iyamato-corpus3`

## Delivered source changes

Introduced `LegacyFixedModelProjectilePreflight`, which inventories **only non-executable evidence** for the static `ModelRenderer` cuboid projectile family. It consumes source-proven legacy entity registration and entity-renderer registration rows via the existing DataWatcher and renderer analyzers, follows the exact `EntityThrowable` vanilla superclass, and calls the prior `LegacyFixedModelProjectileAnalyzer` to extract fixed mesh parameters. No Twilight Forest class, item, entity name or numeric ID appears in production dispatch.

The preflight fails closed on non-Throwable entities, missing/duplicate entity registration, **global** registry-name collisions, inherited source `IEntityAdditionalSpawnData`, and missing/multiple renderer bindings. Unsupported renderer shapes, missing textures, dynamic draw paths and any other incomplete geometry proof are retained as skipped evidence rather than converted into render rules.

Updated `LegacyProjectilePresentationPass` to write a **separate** file after its existing client-content staging prerequisite:

`legacyforgebridge/projectile-fixed-model-preflight.json`

This JSON contains source SHA-256, original mod ID, source entity identity, renderer/model classes, source texture, atlas dimensions, fixed axis-angle orientation and full cuboid geometry (UV/box/pivots), as well as explicit skipped reasons. Its safety fields are intentionally:

- `runtimeWired=false`
- `launcherDataflowProven=false`
- `fmlSpawnRuntimeProven=false`
- `clientFullbrightProven=false`
- per candidate: `runtimeReady=false`

The file does **not** contain an executable `rules` array and is never read by `LegacyProjectilePresentationRegistry`. The existing `legacyforgebridge/projectile-presentation-rules.json` schema and its active `THROWN_ITEM` / `ORIENTED_ITEM` families are unchanged.

Preflight IO/parser failure is reported as `LFB-CONVERT-PROJECTILE-0003` and does not suppress the original conversion path. Positive evidence is summarized separately as `LFB-CONVERT-PROJECTILE-0004`; it is not counted as runtime support.

## Tests

Added `LegacyFixedModelProjectilePreflightTest` with eight renamed, synthetic Java 1.7.10 bytecode / identity cases:

1. exactly one registered source Throwable and one renderer -> fixed geometry evidence;
2. missing or duplicated renderer registration -> reject;
3. duplicated registration for one class -> reject;
4. same registry name reused by another class -> reject;
5. inherited/source extra spawn interface -> reject;
6. exact non-Throwable base -> no candidate;
7. absent PNG resource -> reject;
8. renderer binding without a source entity registration -> no candidate.

Also added `LegacyProjectileFixedModelPreflightManifestTest` with **two JUnit source tests** asserting the standalone diagnostic schema retains cuboid data and has no runtime rules/readiness. These two tests are committed but were **not** executed by Gradle/JUnit.

Local Java 21 standalone smoke using temporary JDK-internal ASM substitutions and test-double registration analyzers: **8/8 passed** for the new preflight's `inspect` seam. The previous rev279 standalone 7/7 fixed renderer proof smoke was also re-run successfully in this session. These runs are not a valid replacement for the repository's own Gradle/Loom/JUnit pipeline or Minecraft runtime tests.

## Remaining hard boundaries

- The exact `twilightforest-1.7.10-2.3.8-tw.jar` binary was not available to test in this session; do not claim the preflight finds MoonwormShot in that specific shipped artifact, despite the upstream Java source reference in Part 2F-3b.
- The source-shape launcher detection in earlier converters is **not** a complete operand/dataflow proof. Keep launcher/semantic readiness false until a unique registered release-use spawning callback can be proven bytecode-causally.
- The projectile renderer must preserve actual texture, axis-angle and mesh UV rendering, respect source full-bright semantics where source-proven, and never execute legacy impact/placement/damage/server-authoritative gameplay on the client.
- Complete original main JAR work requires recovery of the rev256–rev260 local source overlays documented in `docs/SESSION-2026-10-07-REV260-REV262-HANDOFF-GAP.md`. This checkpoint is source-only. No Gradle/Loom build, Minecraft/Fabric/ViaFabricPlus startup, original server testing, or installable JAR is claimed.
- No Actions, PR, release, main/Bamboo mutation or force push was performed. Every continuation commit includes `[skip ci] [skip actions]`.

## Next slice

Add a reusable, **source-dataflow-bound** launcher proof (registered item -> supported use/release callback -> unique projectile allocation -> World.spawnEntityInWorld operand) and explicit fixed-render lighting semantics. Only then can the preflight be considered for a dedicated modern client renderer, followed by exact-corpus and live game validation.
