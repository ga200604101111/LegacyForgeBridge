# alpha.24: source-owned equipment motion and native handheld coordinates

## User feedback and diagnosis
The wings/feet-circle wearable effects and held-item positions are still wrong. The previous
wearable adapter combined bounds, centered all parts and fitted to a guessed size, then attached
the result to the body ModelPart. That loses the ModelBiped root origin, independently authored
left/right pivots and the source renderer's scale/translation/rotation and animation.

The original uploaded JAR (SHA-256 b82cd54d2d2db576e82ba02ea4e55b4db92174c5814b45aa3ebbe1b44d98961d)
contains 8 wing items and 15 circle variants. The new common analyzer recovers all 23 without a
mod-ID/hash/class-name profile. It follows ItemArmor allocation -> constructor name and armor slot
-> getArmorModel -> ModelBiped.render. Static textures, StringBuilder paths, float expressions,
MathHelper sin/cos, per-frame assigned renderer fields, matrix stacks and proven crouch branches
are interpreted symbolically during conversion, not by loading the old classes.

## Generated code and runtime
`LegacyEquipmentRenderPass` emits a generated equipment registration class plus JVM render
programs in the converted mod's generated package. Program methods contain the extracted float
math, age inputs and boolean posture branches. Their small stable Sink API feeds normal Fabric
ArmorRenderer/SubmitNodeCollector calls. The runtime does not execute original source bytecode,
read a render manifest or select per-mod equipment formulas. The JSON trace is for audit only.
A proven program removes the corresponding provisional equipmentRender definition to avoid
double renderer registration. Source mesh/texture paths are validated and PNG fractional alpha
is inspected for each exact selected texture, not a guessed family texture.

Armor programs render in the humanoid model root. They do NOT auto-center, auto-fit or apply a
second body-part rotation. Authored feet offsets and separate left/right local pivots survive.
MathHelper's legacy float sine lookup table is provided as a shared mathematical adapter.
Disabling GL_LIGHTING alone is NOT interpreted as disabling the world lightmap/fullbright.
Legacy diffuse lighting state is audited but exact fixed-function lighting fidelity is not yet
implemented. Modern layer handling still approximates some legacy translucent/culling behavior.

## Held-item coordinate normalization
For ordinary EQUIPPED_BLOCK=false source renderers, native handheld base display transforms
already provide the modern equivalent of the vanilla sprite placement. Appending Forge's old
vanilla helper compensation and an extra half-block translation applies a second coordinate
conversion. Use source-local ordered operations relative to the native item coordinate frame,
without that extra helper prefix or centered offset. Inventory native sprites remain unchanged.
Block-helper and ground/fixed behavior are deliberately not generalized by this fix.
This is a native-reference normalization, not a promise of pixel-identical old outer camera,
left-hand, attack/block or third-party animation matrices. GPU/client validation is required.

## Validation performed before CI
- Original source: 23 complete equipment bindings, zero analyzer diagnostics.
- Generated and JVM-loaded all 23 equipment render programs locally, comparing 230 age/posture
  executions with the parsed source operations and exact model/texture paths. Balanced push/pop,
  finite transforms and draw counts verified. Local compiler used JDK internal ASM as a test
  stand-in; production CI uses the existing Fabric Loader-provided core ASM dependency.
- Independent alchemy/astronomy source fixtures recover the same generic mechanism.
- Unknown entity-dependent calls are rejected with diagnostics, not frozen into guessed values.
- Regression tests execute generated program bytecode, not just count generated class files.

## Scope and upgrade
This is not arbitrary Forge behavior conversion, nor a complete ModelBiped emulator. Stateful
cached models, arbitrary render events, unknown GL effects and unresolved source item identities
remain diagnostic/fallback paths. Source-name-lfb.jar naming, source-only locale conversion,
alpha.22 mesh fixes and alpha.23 native tags/yinghuang binary namespace are retained.
Reconversion is required (BuildInfo alpha.24). Keep PR unmerged until real client tests.
