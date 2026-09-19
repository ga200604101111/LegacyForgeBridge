# 2026-09-20 — Native block geometry, held models and neighbor material projection

Converter revision: `2026-09-20.141`. Parent: `6ecbde61278dc561863ad9e15e3da7a508a78652`.

The user's .140 launch log and feedback confirm that the A-to-B placement identity issue is no
longer observed. This revision does not alter Via registry mappings, numeric IDs, carrier edges,
name projection or native metadata codecs. It belongs to the converter and native runtime;
there is no separate texture-fix mod.

## Converter-owned geometry

`LegacyBlockGeometryAnalyzer` selects explicit presentation adapters from source allocations,
platform ancestry, bound render IDs, icon selectors, current-position metadata bounds and
inventory bounds. World coordinates remain tainted. Source classes are not defined or executed.
A bounded neighbor probe records directional per-face material selection; it does not claim to
interpret arbitrary world access. Stateful shape selectors, position-dependent decisions,
shifted collision forwarding and unresolved collision programs are not admitted.

`LegacyBlockGeometryPass` emits candidate-owned block geometry rules, block models, 3D item
models and native metadata item-model selections before atlas materialization. Only unchanged
provisional models, explicit placeholders, or defaults just owned by the icon pass can be
replaced. Specialized item model definitions are preserved. Original sprites and animation
sidecars are unchanged. The shared icon pass's cuboid models now inherit `minecraft:block/block`
so vanilla block GUI/first-person/third-person transforms are not accidentally omitted.

The fresh original Bamboo resource-stage check admitted 19 block identities: three half-block
families, three inherited stair families, the pane family, horizontal/vertical/stair mimic
families and nine other source cuboid families. The pane has seven source variants; two curtain
variants have no collision and no top/bottom/edge caps. The 19 identities are not 19 newly
resolved placeholders. Existing simple cuboids are included because collision and held models
now use the source shape too.

## Runtime

`ConvertedLegacyBlock` consults strict optional geometry rules for outline/collision shapes.
Its IDs and raw 0..15 metadata are retained. Shapes are dynamic only for admitted rules.
Stair orientation, upper half and corner geometry use bounded box unions; panes evaluate their
four neighbors. No extra connection block-state properties or state IDs are introduced.

A Fabric `ModelLoadingPlugin` wraps native block-state models. Meshes are emitted through the
1.21.11 Fabric Renderer API, using current adjacent blocks for connected panes and stair corners.
Non-cubic models are not treated as full-cube occluders. Source-generated face sprites are read
from the baked models after candidate-owned atlas conversion. A pane/lattice is not reclassified
as a wooden fence simply because its texture looks wooden.

The three admitted directional mimic families keep their own shape and project the neighboring
block's face sprite and tint. A visited-position set and a maximum 256-step traversal protect
against cycles and pathological chains. Air/water/unresolved sources fall back to the mimic's
own material. This guard is not advertised as the original mod's configurable recursion limit.
No shader-specific or external resource-pack patch is installed.

## Limits

These are native geometric adaptations, not a claim of bit-for-bit legacy renderer equivalence.
The pane mesh uses a two-pixel native thin-panel form rather than preserving the old renderer's
subpixel cap offsets. Unusual multi-layer/tinted/animated neighbor models may need dedicated
mimic adapters; copied material does not copy the neighbor's collision or block identity.
Neighbor interaction forwarding and mimic pressure plates are not completed here. Long chains
crossing several chunk sections may need additional dependency invalidation beyond ordinary
neighbor-section rebuilds. Unknown custom pillar/beam/ornament renderers remain excluded instead
of being guessed as fences or cubes. This does not claim all prior unresolved models are fixed.

## Validation

Thirteen standalone source/geometry/schema checks run the production converter logic locally;
the same checks have normal JUnit wrappers. They cover cube winding and exposed area, all raw
stair orientation bits, upper/lower slab dimensions, inner/outer corners and continuation guards,
all pane connection masks, uncapped curtains, invalid schemas/bounds, block-style item transforms,
source non-execution and preservation of specialized item renderers. An additional checksum-pinned
external corpus test is tagged `exact-corpus`, following the repository's existing policy.

Original test input SHA-256: `bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402`.
The binary is not committed. Local resource-stage checks are separate from full semantic code
generation and client startup. Local tests use a local ASM compatibility jar solely because the
working environment has no downloaded Gradle distribution; it is not distributed or committed.
CI supplies the declared production dependencies and performs the full build/test/remap.

No live Minecraft rendering/placement test has been performed in the working environment.
Use the CI-built main jar, retain original old-mods inputs, and restart after conversion stages
the new managed candidates. Do not install the obsolete standalone texturefix mod.
