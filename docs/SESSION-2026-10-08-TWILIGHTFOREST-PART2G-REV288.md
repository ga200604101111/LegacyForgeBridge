# 2026-10-08 — Twilight Forest Part 2G / rev288: source-causal per-part TileEntity model pivots

Branch: `feature/generic-conversion-iyamato-corpus3`

## New generic conversion evidence

Introduced `LegacyTilePivotAnimationAnalyzer` to analyze the source-bytecode shape of a `ModelBase` invoked from a **registered ordinary-Block + TileEntity + TESR** candidate. The constructor must declare and instantiate each used `ModelRenderer` part; a TESR must construct its model field and call exactly one source-owned model animation method with the actual TileEntity argument and original partial-tick float. Mod/class, tile, registry and item names are input identities **not special-case production selectors**.

Within the method, the analyzer requires **one unconditional constant Y-pivot reset** and **one guarded dynamic Y-pivot update** per part, and verifies the exact source expression shape:

`modelPart.rotationPointY = provenSourceConstant; if (tileGuard == 0) modelPart.rotationPointY += Math.min(0f, MathHelper.sin(sourceExpression(tileFields, partialTick)));`

The `sourceExpression` graph must be constructed only from proven TileEntity instance field reads, partial ticks, finite constants and bounded integer/float arithmetic. The exact `GETFIELD`/`PUTFIELD` receiver must be the **same** constructed `ModelRenderer` instance field, including `DUP` stack aliases. `MathHelper.sin` (MCP/SRG) and `java.lang.Math.min(FF)F` must be the actual source function operands, and `min` must have the constant zero argument. Guard conditions must source an integer instance TileEntity field and gate dynamic writes only when its value is zero. If a source has changed the formula, used a different partial-time source, called unknown methods, mutated other gameplay/model state, assigned null instead of new part/model, or produced a genuinely different animation family, this analyzer does **not** admit it.

The proof records `modelAnimationMethod`, the source guard field, and per-part names, rest Y pivots and source TileEntity field dependencies. The exact source formula still is **not an executable modern animation program**; no bytecode is replayed in the 1.21.11 client.

## Conversion preflight wiring

`LegacyBlockTileModelPreflightPass` adds an independent optional pivot analyzer after the rev284 source-registered Block+Tile+TESR, rev285 visual inventory, rev286 16-state static facing and rev287 dynamic yaw audits. Failures remain `SupportLevel.AUTO` diagnostic `LFB-CONVERT-BLOCK-TILE-0008`/`0009`, so an incomplete optional family cannot downgrade the user's existing convertible content. The existing non-executable `legacyforgebridge/block-tile-model-preflight.json` sidecar gains:

- Root: `sourcePivotSineClampCandidateCount`.
- Candidate: `sourcePivotSineClampAnimationProven`, `sourcePivotTileSyncProven=false`, `sourcePivotAnimationRuntimeWired=false`.
- For valid identity-matched source proofs only: `sourceModelAnimatorMethod`, `sourceModelAnimationGuardTileField`, and `sourcePivotAnimatedParts` containing the source part/pivot identities, unscaled source constant reset value, source TileEntity input fields, source partial-time dependence, and `sourceAnimatedPartRuntimeWired=false`.
- Optional source observation: `sourceAnimationPacketHookObserved`, **not** serialization/protocol proof.

The conversion sidecar retains `sourceOnly=true`, `runtimeWired=false`, `blockEntityRuntimeWired=false`, `tileStateSyncProven=false`, `modernBlockGeometryProven=false`, `animationSemanticsProven=false` and every candidate `runtimeReady=false`. **No executable `rules` array is emitted or consumed.** All previous `manifest` overloads remain callable, defaulting missing new evidence to false.

## Test evidence and provenance

Executed with Java 21 against the actual user-uploaded **rev260 complete main JAR** ABI and prior rev284–287 fixture classes, using temporary `jdk.internal.org.objectweb.asm` substitutions plus test-double Gson/JUnit for isolated synthetic bytecode tests. Source uploaded to GitHub was verified to match each locally tested Git blob.

| Tested suite | Passed |
| --- | ---: |
| New rev288 renamed source model animator/counterfeit bytecode tests | 34/34 |
| New rev288 candidate diagnostic/legacy overload tests | 9/9 |
| rev287 dynamic source Y + manifest regressions | 28/28 |
| rev286 facing + manifest regressions | 30/30 |
| rev285 visual source + manifest regressions | 22/22 |
| rev284 ordinary Block/TESR + manifest regressions | 20/20 |
| **Total** | **143/143** |

The JDK-internal ASM adaptation is **not** the real external ASM library; test stubs are **not** official JUnit/Gson; no Gradle/Loom or Minecraft/Fabric/ViaFabricPlus/Forge 1.7.10 in-game test was performed. The original `legacyforgebridge-0.2.0-alpha.27-rev260-handoff-fix.jar` stayed unmodified; SHA-256 `03a7bff020197b275977227ff0ee4dbbf7c9e3df77ea6c1425470f7fde4b93d9`.

## Source vs gameplay

The source family is inspired by the Moonworm **placed Block + TileEntity** renderer in Benimatic's 2017 Twilight Forest Java sources. A separate MoonwormShot projectile uses the same geometry but does **not** share its TileEntity animation or client networking lifecycle.

**Not proven / not shipped:** original translated `twilightforest-1.7.10-2.3.8-tw.jar` corpus SHA and actual bytecode; explicit FML TileEntity field payload on original server; exact GLSL/render matrix/negative scale geometry and mesh animation on Fabric 1.21.11; six-face metadata-dependent bounds, collision and placement; full client startup or unchanged Forge 1.7.10 server interoperability. Rev256–rev260 complete production source overlays and desktop helper source must still be recovered before a truthful build/release. A source-only checkpoint does not mean any additional Moonworm placed block is playable.

## Next development slice

1. Source-prove state synchronization **per dependent TileEntity field** from real legacy packet/NBT payload, rather than upgrading an observed packet hook to actual sync.
2. Separate safe **client-computable** presentation states from server-authoritative state: animation must never replay the old server tick/placement logic.
3. Validate full source TESR transform stack including metadata-facing, dynamic Y rotation, model pivot moves, negative Y/Z scaling, texture and source lightmap. Plan a modern BlockEntity renderer only when source/transport prerequisites are satisfied.
4. Obtain exact translated Twilight Forest corpus for negative and positive proof tests. Recover cumulative rev260 source, compile against real Loom/Fabric/ASM/Gson, run native-client and multiplayer comparisons, then use pinned rev260 full JAR as a base only for a genuinely compiled, tested complete main.

Repo policy: only the existing feature branch. No workflow trigger/PR/release/tag, no force push, main/Bamboo edits, original mod/server writes, or new installable main JAR. All commits carry `[skip ci] [skip actions]`.
