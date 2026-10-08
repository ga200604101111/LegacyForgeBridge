# 2026-10-08 — Twilight Forest 2.3.8 continuation, Part 2F-4 (rev281)

Branch: `feature/generic-conversion-iyamato-corpus3`.

This checkpoint improves **source-only** evidence for a generic fixed-model projectile candidate. It does **not** implement client mesh rendering, gameplay, transport, or a new installable main JAR.

## Source changes

1. Added `LegacyProjectileLauncherAnalyzer`. From source-proven `LegacyRegistryAnalyzer` item registrations, it examines exact 1.7.10 `onPlayerStoppedUsing(ItemStack,World,EntityPlayer,int)` and `onItemRightClick(ItemStack,World,EntityPlayer)` callbacks (MCP and SRG names), including inherited source-owned callbacks. For an eligible direct `World.spawnEntityInWorld(Entity)` call, ASM `SourceInterpreter` frames must trace the **actual Entity argument** back to one specific source `NEW` allocation of the target projectile and its matching declared `<init>` invocation. The call receiver must trace to the original callback `World` argument. A uniquely registered source item/callback is required. Aliases through `ALOAD`, `ASTORE`, `DUP` and `CHECKCAST` are handled while ambiguous merged allocation sites fail closed.
2. `LegacyFixedModelProjectilePreflight` now consults that independent registered-item dataflow proof when the source registry exposes item declarations. Candidates retain their previous fixed cuboid geometry proof. A launcher proof is optional and cannot manufacture a geometry candidate or authorize rendering. The historical four-argument `Candidate` constructor still builds a geometry-only record with an empty optional launcher proof.
3. `LegacyProjectilePresentationPass` adds per-candidate `sourceLauncherDataflowProven` and, only when proven, the registered launcher item identity and exact callback owner/name/descriptor/family to `projectile-fixed-model-preflight.json`. Aggregate `sourceLauncherProofCandidateCount` and `launcherDataflowProven` are source-evidence counts, **not runtime support**. The existing `runtimeWired=false`, per-candidate `runtimeReady=false`, absent executable `rules` list, and unchanged active `projectile-presentation-rules.json` remain the critical gates.

No mod names, projectile names, texture names, registry names or numeric IDs appear in the production acceptance algorithm. Original legacy mod binaries, world state and Forge 1.7.10 server are untouched.

## Executed local tests and limitations

The two new synthetic ASM/JUnit-source suites have **16 executed test methods** through a standalone Java 21 reflection harness:

- 14 launcher analyzer scenarios: direct release-use, right-click with stored aliases, inherited source-owned callback, unrelated target allocation/spawning a different entity, opaque factory spawn, different World receiver, incorrect callback name, multiple direct spawns, multiple allocation sites merging into one local, two registered launchers, duplicate item registry keys, duplicated registration, nonexistent invoked source constructor and unregistered projectile.
- 2 joined preflight scenarios: source-proven fixed model plus direct launcher; fixed geometry preserved when launcher is an unproved factory.

Compilation used temporary substitutions to JDK-internal ASM and minimal double classes for the repository source registry, DataWatcher renderer inventory and JUnit annotations. This is not an official project ASM/JUnit/Loom execution, exact original corpus conversion, modern client launch or real server test.

A further JUnit source case was committed in `LegacyProjectileFixedModelPreflightManifestTest` to verify that even a **proven launcher** does not become an executable runtime rule. That specific manifest test has **not** been executed in the real repository test runner.

The exact translated original `twilightforest-1.7.10-2.3.8-tw.jar` was unavailable here. The archived original upstream source for MoonwormShot demonstrates a release-use launcher; successful exact-`-tw` matching must still be established by the production analyzers before claiming admission.

## Remaining compatibility work (not represented by this checkpoint)

- Full static model source proof against the exact translated JAR and special full-bright/light emission semantics.
- New modern client mesh renderer with correct cuboid UV, pose axis, texture and visibility, plus dedicated registration/carrier wiring.
- Actual entity FML spawn/velocity/watcher envelope validation and live connection. Behavior and damage must remain server-authoritative.
- ThrownIce block-backed, ChainBlock multipart, CubeOfAnnihilation dynamic translucent custom renderer families; other three launcher-unproven projectile families and seeker arrow.
- EntityMob/EntityLiving family construction, navigation, AI, animations, boss parts and interactions (current entity admission census was 0/77).
- TileEntity, GUI, dimension, portal, worldgen/structures, progression, particles and sound conversion. Registration counts are not a functional compatibility percentage.
- Source recovery: rev256–rev260 main build overlays including Corpus3 production and desktop files; see `docs/SESSION-2026-10-07-REV260-REV262-HANDOFF-GAP.md`. Root `src` is not the cumulative shipped rev260 source. Do not claim a reproducible release or invent a main JAR.

## Safety and development boundaries

All changes are on the pre-existing feature branch, and every new commit uses `[skip ci] [skip actions]`. Do not dispatch Actions, create a PR, release, force-push, change main/Bamboo, or modify original JAR/server state. Missing source proofs must fail closed. Source-only candidate metrics must never be presented as runnable gameplay.
