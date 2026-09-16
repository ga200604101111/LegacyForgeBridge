# 2026-09-16 — Seat-bed special presentation runtime

## Scope

This slice continues the generic two-part legacy bed/seat family after source proof, core gameplay runtime, and proof-gated FML seat entity spawn bridging. It does not add a Bamboo-specific production branch and does not change the already-green seat-bed gameplay sidecar schema 3.

Exact corpus used for validation:

- `Bamboo-2.6.8.5.jar`
- SHA-256 `bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402`

Converter revision: `2026-09-16.59`.

## Separate presentation layer

The stable gameplay sidecar remains:

- `legacyforgebridge/seat-bed-rules.json`
- schema version 3

Special client presentation is intentionally isolated in a new sidecar:

- `legacyforgebridge/seat-bed-presentation-rules.json`
- schema version 1

This separation means a rendering change cannot silently widen or invalidate the already-tested gameplay/remote-spawn admission boundary.

## Source presentation proof

`LegacySeatBedPresentationAnalyzer` is a non-executing ASM analyzer. Admission starts from an already source-proven `LegacySeatBedAnalyzer.Rule` and then independently proves the legacy client presentation chain:

1. the source TileEntity class is bound to exactly one source `TileEntitySpecialRenderer` registration;
2. the renderer constructs exactly one source `ModelBase` implementation;
3. the renderer dispatches the same model through two boolean-selected texture/model branches;
4. both branches use the same fixed `0.0625F` model scale and metadata-derived four-way transform;
5. the metadata switch resolves to one unique 4-entry direction permutation and one unique 4x2 translation table;
6. the two texture resources exist in the source JAR and have one proven image size;
7. the model is exactly four fixed `ModelRenderer` cuboids, including UVs, dimensions, pivots and source rotations;
8. the two render groups are disjoint and together cover all four parts;
9. the source TileEntity explicitly expands its render bounding box around the two-part object.

Unknown registration shapes, merged renderer candidates, missing image resources, dynamic model geometry, incomplete part groups or ambiguous transform tables fail closed.

## Exact Bamboo result

The checksum-pinned Bamboo corpus proves one Huton presentation rule with zero presentation diagnostics:

- renderer: `ruby/bamboo/render/tileentity/RenderHuton`
- model: `ruby/bamboo/render/tileentity/ModelHuton`
- foot texture: `bamboo:textures/entitys/huton.png`
- head texture: `bamboo:textures/entitys/makura.png`
- source texture/model size: `64x32`
- source cuboids: 4
- foot group: `box1`, `box2`
- head group: `box0`, `box3`
- source-direction X translation: `[0.5, 0.0, 0.5, 1.0]`
- source-direction Z translation: `[1.0, 0.5, 0.0, 0.5]`
- source-direction Y yaw: `[90, 0, 270, 180]` degrees
- source expanded render bounds: proven

The same proof shape is covered by an unrelated-namespace synthetic regression, so the production analyzer is not keyed to Bamboo class names, registry IDs or mod IDs.

## Modern runtime

`LegacySeatBedPresentationPass` materializes only proof-complete presentation payloads into the independent schema-1 sidecar.

`LegacySeatBedPresentationRegistry` validates that sidecar again on the client before exposing it to rendering. It rejects malformed geometry, duplicate parts, overlapping/incomplete render groups, invalid transform arrays, missing texture identities, non-finite values, or non-proven expanded bounds.

`ConvertedLegacySeatBedRenderer` rebuilds the four proven cuboids as modern `ModelPart` geometry, preserves source pivots/rotations, applies the proven direction translation/yaw table, and submits the proven foot/head groups with their distinct textures.

The source renderer expanded its culling AABB to cover a 3x3 horizontal neighborhood. Minecraft 1.21.11's BlockEntityRenderer boundary used here does not expose an equivalent source-defined per-renderer AABB payload, so the admitted renderer conservatively opts into off-screen rendering only when that expanded-bounds proof is present. This preserves visibility instead of replacing the source extent with a smaller guessed box.

Generated converted clients call `ConvertedSeatBedPresentationRuntime.initializeMod(modId)` during client initialization. The bootstrap cross-checks that an already-admitted seat-bed gameplay rule exists for the same modern block ID before registering the renderer. Presentation rules without the corresponding proven gameplay family remain fail closed.

## Authority boundary

Huton `TimeAccel` remains source-proven but **not client-executed**. On a real Forge 1.7.10 server the world-time/weather mutation is server-authoritative. Replaying it locally would duplicate or desynchronize authoritative state.

Therefore:

- source time acceleration remains inventoried/proven by the gameplay analyzer;
- local client time-acceleration execution remains closed;
- presentation completion does not change the gameplay authority boundary;
- the full Bamboo candidate remains partial until its remaining families and loader/dependency closure are complete.

This slice must not be cited as proof that the entire Bamboo JAR is already installable or fully converted.

## Regression coverage

- checksum-pinned `BambooSeatBedExactTest` validates the exact Huton presentation proof;
- `LegacySeatBedPresentationAnalyzerTest` validates the same source shape under unrelated names;
- `LegacySeatBedPresentationRegistryTest` validates schema-1 fail-closed parsing and presentation invariants;
- `GeneratedModEntrypointPassTest` requires the generated client entrypoint to invoke the seat-bed presentation bootstrap;
- `ConvertedLegacySeatBedRendererBytecodeTest` requires the modern renderer to retain ModelPart creation, texture render-type creation, render-queue submission and the source-expanded culling intent.
