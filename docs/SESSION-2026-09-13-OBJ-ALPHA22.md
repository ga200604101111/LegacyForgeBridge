# alpha.22: source-driven item rendering and OBJ geometry fidelity

## Scope and architectural correction

Class count is not evidence of a complete conversion. A Fabric mod may legitimately use JSON,
resources, and a shared library; replacing JSON interpretation with generated constants does not
by itself port gameplay. The actual requirement is preserved executable behavior and native
Minecraft/Fabric resource/registration lifecycle. This change does not claim arbitrary Forge mods
or RPGTool gameplay are fully ported.

This patch fixes common OBJ geometry and adds a reusable bounded symbolic IItemRenderer analyzer.
It contains no item-name lists, namespace checks, source hashes, or per-weapon scale tables.
The existing content profiles are still needed to emit basic item definitions; this patch does not
replace those with a universal item/gameplay compiler.

## Direct evidence

The uploaded alpha.21 candidate includes 39 OBJ resources and three generated classes. The original
source JAR is SHA-256 b82cd54d2d2db576e82ba02ea4e55b4db92174c5814b45aa3ebbe1b44d98961d.
All 39 OBJ resources in the uploaded candidate are byte-identical to the original source resources.
Its ClientProxy registers 20 IItemRenderers. Each refuses INVENTORY; native item sprites must be
used in inventory instead of forcing an auto-fitted 3D model. Resource files hold vertex/UV data;
renderer classes hold model/texture bindings, context predicates, helper choices and GL operations.

The previous shared OBJ renderer emitted only A,B,C per triangle into entity RenderTypes that
consume QUADS. It now emits A,B,C,C. Previously independent triangles were grouped across face
boundaries by quad indexing, which could connect unrelated vertices.

The previous per-vertex fract UV normalization was not equivalent to a repeating sampler for a
triangle crossing a tile boundary. Preparation now clips triangles on integer UV tile boundaries,
then uniformly rebases each piece. Interpolation is preserved; UVs at opposite edges remain 0 and 1.
This emulates repeated geometry addressing, not a guarantee of identical GPU filter footprints
for every renderer or resource pack. Face normals and Forge V flip / 0.0005 face UV inset remain.

## Generic extraction and modern resources

LegacyItemRenderAnalyzer never loads or executes original source classes. It symbolically resolves
ordinary registerItemRenderer calls, constructor constants, static OBJ loads, ResourceLocation /
StringBuilder texture paths, handleRenderType/shouldUseRenderHelper branches and ordered GL
translate/rotate/scale/matrix stacks. Item/NBT/time-dependent branches, unknown state operations,
exception handlers, group rendering and runtime field mutations produce diagnostics, not guessed
constants. Calls and instruction counts are bounded.

LegacyItemRenderPass is in the common conversion plan. It joins proven renderer bindings to
existing model definitions by exact canonical model+texture identity (ambiguous joins are rejected),
then writes normal minecraft:select / minecraft:display_context item models. INVENTORY rejection
uses minecraft:model and the existing sprite base. Each supported hand/entity context has its own
ordered transform sequence. Shared helper prefixes come from Forge 1.7.10 ForgeHooksClient;
they are not corpus-specific tuning. Render and reported extents use the same local matrix.
The source trace is retained in legacyforgebridge/item-render-analysis.json.

Resource reload uses Fabric ResourceLoader v1 and invalidates parsed mesh/missing-resource state
and generated equipment texture resolution. Geometry preparation is cached, not repeated per frame.

## Validation and explicit boundaries

Local pure-Java corpus checks: all 39 uploaded OBJ files prepare successfully, producing 28,997
triangles after degenerate-face removal and UV clipping, with complete four-vertex output and bounded
UVs. The original JAR yields 20 renderer bindings with no analyzer diagnostics. Independent synthetic
alchemy/astronomy namespaces also work; a dynamic item-dependent branch is rejected. JVM integer
division is tested before float conversion.

Regression tests cover primitive grouping, seam interpolation and area preservation, negative UVs
and indices, concave polygons, invalid references/non-finite data, bounded UV expansion, independent
mod namespaces, ordered context operations, native GUI sprite fallback and unsupported/missing
resource diagnostics. CI builds against the actual 1.21.11 target; it is not a GPU visual test.

Still unverified/incomplete: exact outer first/third-person coordinate equivalence, left-hand
mirroring versus the legacy right-hand-only client, item-frame legacy helper differences, glint,
arbitrary custom inventory renderers, unusual render helper/state behavior, dynamic ModelBiped wing
and circle animation, lighting/blend state, material/group draw selection and all gameplay behavior.
Common topology/UV repairs also reach wearable OBJ rendering, but its animated behavior is not
recovered by this static IItemRenderer pass. Do not describe this build as a complete faithful port.

Retained rules: source-basename-lfb.jar output, no synthesized source locales, cached missing managed
JAR restoration, generated mod lifecycle, and FML/Via item identity. PR remains unmerged pending
client validation.
