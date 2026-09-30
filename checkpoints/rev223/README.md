# rev223 — Bamboo 2D BlockItem、iYAMATO projectile completeness、block texture/icon recovery

Target branch: `feature/generic-conversion-iyamato-corpus3`.

## Installable corpus4 overlay is now assembled

The exact cumulative user main JAR was supplied after the original source-only checkpoint:

- base file: `legacyforgebridge-0.2.0-alpha.27-rev220-corpus4-local.6.jar`
- base bytes: `4,395,764`
- base SHA-256: `38a880d97f4e24d0dcadfab5b29a481bf91776195fca966f9bb3e8fbc3d878b7`
- base internal version: `0.2.0-alpha.27-corpus4-local.6-rev220-local-test.1`

A complete replacement JAR was assembled from that exact binary base without rebuilding from stale root source:

- output file: `legacyforgebridge-0.2.0-alpha.27-rev223-corpus4-local.7.jar`
- output bytes: `4,409,843`
- output SHA-256: `1ade6ca24d0332cf8b10a72e3800063736ecfec3c56b5c62b1f269a9e8cdbf56`
- internal version: `0.2.0-alpha.27-corpus4-local.7-rev223-local-test.1`
- converter revision: `2026-09-30.223-flat-item-projectile-blocktexture`

This binary is an incremental compatibility overlay on the exact corpus4-local.6 main JAR. The branch still contains the generic rev223 source fixes; the overlay exists because the supplied cumulative binary contains some converter classes older than the branch source, and a clean Loom rebuild of the entire cumulative history is not available in this environment.

## Exact user corpus

- iYAMATO: `iYAMATOs-Mod-1.7.10.jar`, 1,029,340 bytes, SHA-256 `35a79ca5a034fd6cda4fe4ee1f658c4ce058f9b7ec026516e76363ebdf89635e`.
- Bamboo: `Bamboo-2.6.8.5.jar`, 1,319,593 bytes, SHA-256 `bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402`.

## Bamboo 2D bamboo shoot

Exact source evidence:
- block registry identity: `blockbambooshoot`;
- BlockItem class: `ruby/bamboo/item/ItemBambooshoot`;
- world texture: `assets/bamboo/textures/blocks/bambooshoot.png`;
- item texture: `assets/bamboo/textures/items/bambooshoot.png`.

rev223 materializes the proven inventory sprite into a modern `textures/item/lfb_flat/...` path, writes a generated item model, writes the 1.21 item definition, and retires only the same block's stale `lfb_geometry/<path>/<meta>` ITEM_MODEL ownership. Independently source-owned metadata presentation is preserved.

## iYAMATO projectiles

The exact iYAMATO JAR has 24 registered weapon/helper projectile entities. With the user's live base 4800:
- 4804 is `EntityExtendedReach`;
- 4815 is `EntityJavelinBolt`.

The live log showed watcher 16 byte packets being rejected when a presentation rule was labeled `THROWABLE`. Runtime admission now treats the canonical watcher-16 byte as a valid legacy projectile base watcher independently of that presentation-family label.

The remaining 23/24 presentation gap is `EntityIYExtendedReach`:
- direct `Entity` subclass;
- implements `IProjectile`;
- watcher 16 byte zero;
- exact client binding: `RenderSnowball(ItemRegister.invisible_entity_projectile)`;
- carrier registry identity: `iymts_mod:iyinvisible_entity_projectile`.

It is intentionally invisible. rev223 gives it the missing remote identity/tracker presentation rule while retaining the registered invisible item carrier.

## iYAMATO blocks

Exact source/resources prove:
- `iytamahagane_block` -> `iymts_mod:tamahagane_block`;
- `iyvanadium_block` -> `iymts_mod:vanadium_block`;
- `iyvanadium_ore` -> unique short-prefix recovery to `iymts_mod:vanadium_ore`;
- `iydamascus_steel_block` -> `damascus_steel_block_top` for UP/DOWN and `damascus_steel_block_side` for sides.

The binary overlay carries the short-prefix matcher and Forge 1.7.10 `ForgeDirection` ordinal handling needed by the cumulative corpus4 converter.

## Cache invalidation

`BuildInfo.CONVERTER_REVISION` is:

`2026-09-30.223-flat-item-projectile-blocktexture`

This forces source-hash/revision conversion candidates to rebuild instead of reusing the user's old `.222` output.

## Binary audit

- base entries: 1663
- output entries: 1664
- removed entries: 0
- duplicate entries: 0
- only added entry: `dev/yinghuang/legacyforgebridge/convert/Rev223Compat.class`
- seven expected existing entries changed: `BuildInfo.class`, `FmlRuntimeClient.class`, `LegacyIconTableAnalyzer.class`, `LegacyClientContentBaselinePass$TextureIndex.class`, `LegacySimpleBlockPresentationPass.class`, `LegacyProjectilePresentationPass.class`, and `fabric.mod.json`
- nested `META-INF/jars/energy-4.2.0.jar`: byte-for-byte unchanged
- ZIP integrity: pass
- ASM `BasicVerifier`: pass for every method in every changed/added class
- patched bytecode was disassembled and checked for the intended watcher, short-prefix, ForgeDirection, Bamboo pass hook, iY pass hook, version, and converter-revision calls.

## Validation boundary

No Windows/Prism/Minecraft/Via live launch was performed inside the build container because the user's live game instance is not available here. The JAR is structurally and bytecode-audited; install-and-launch behavior remains the final live validation.

No Actions dispatch, PR, tag, release, workflow modification, or main/Bamboo branch modification was performed.
