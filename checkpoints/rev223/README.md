# rev223 — SOURCE FIX：Bamboo flat BlockItem、iYAMATO projectile completeness、block texture/icon recovery

Target branch: `feature/generic-conversion-iyamato-corpus3`.

**This checkpoint is source/evidence only. It is not an installable main JAR.** Do not package root `src/` alone and label it rev223/corpus4. The user's live bridge is `0.2.0-alpha.27-corpus4-local.6-rev220-local-test.1` with converter revision `2026-09-29.222-corpus4-presentation-projectile-blockstate`; that exact cumulative main JAR is not stored in the repository or current conversation/library files. A complete replacement JAR must preserve that binary history (or be rebuilt from an equivalent complete cumulative source/base), then apply this checkpoint.

## Exact user corpus

- iYAMATO: `iYAMATOs-Mod-1.7.10.jar`, 1,029,340 bytes, SHA-256 `35a79ca5a034fd6cda4fe4ee1f658c4ce058f9b7ec026516e76363ebdf89635e`.
- Bamboo: `Bamboo-2.6.8.5.jar`, 1,319,593 bytes, SHA-256 `bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402`.
- User live log: bridge version `0.2.0-alpha.27-corpus4-local.6-rev220-local-test.1`; converter revision `2026-09-29.222-corpus4-presentation-projectile-blockstate`.

## 1. Bamboo 2D bamboo shoot

Exact resources contain both:
- `assets/bamboo/textures/blocks/bambooshoot.png`
- `assets/bamboo/textures/items/bambooshoot.png`

The source simple renderer proves a flat inventory presentation. The earlier fix selected the unique item sprite, but Minecraft 1.21.11 item rendering can still be held by metadata ITEM_MODEL definitions written earlier by `LegacyBlockGeometryPass` under `lfb_geometry/<path>/<meta>`.

rev223 therefore:
1. materializes the proven legacy item sprite into the modern `textures/item/lfb_flat/...png` path;
2. rewrites the normal item model to `minecraft:item/generated`;
3. performs a final ownership handoff after geometry: only metadata entries for this exact block that still point to `<namespace>:lfb_geometry/<path>/...` are repointed to the flat item definition;
4. preserves any independently source-owned/non-geometry metadata model.

This closes both the atlas path and final metadata selection layers.

## 2. iYAMATO projectile completeness

The exact iYAMATO JAR has 24 registered weapon/helper entity classes in `EntityRegister.register()`. With default configured entity base 4800, registration is post-increment, therefore:
- 4800 IronDagger
- 4801 GoldenDagger
- 4802 DiamondDagger
- 4803 DamascusSteelDagger
- **4804 ExtendedReach**
- ...
- **4815 JavelinBolt**
- ...

User live evidence showed 4815 arriving with watcher 16 byte and being rejected as `family=THROWABLE` / unmapped watcher. Exact source proves `EntityIYJavelinBolt extends EntityArrow` and watcher 16 is the legacy arrow flags byte. Multiple other source-owned direct-Entity/IThrowableEntity projectile classes also copy watcher 16. Runtime admission therefore accepts the canonical projectile watcher 16 byte independently of the renderer/base-family label instead of rejecting the spawn.

The apparent remaining 23/24 presentation gap is ID 4804 `EntityIYExtendedReach`:
- direct superclass: `net.minecraft.entity.Entity`;
- implements `net.minecraft.entity.IProjectile`;
- source entityInit defines DataWatcher index 16 as byte zero;
- ClientProxy uniquely binds the entity to `RenderSnowball(ItemRegister.invisible_entity_projectile)`;
- `invisible_entity_projectile` is a real registered source item.

This is an intentional invisible reach/attack carrier used by spear/halberd-style weapons. It must still receive a remote identity/tracker mapping, but its source-visible result remains invisible. The generic analyzer now admits copied-arrow carriers only when `IProjectile + watcher16 byte zero + exact RenderSnowball registered carrier` are source-proven; it does not hardcode ExtendedReach or numeric ID 4804.

## 3. iYAMATO blocks

Exact source/resources prove:
- `iytamahagane_block` -> explicit `iymts_mod:tamahagane_block`;
- `iyvanadium_block` -> explicit `iymts_mod:vanadium_block`;
- `iyvanadium_ore` has no source texture setter; its unique source texture is `textures/blocks/vanadium_ore.png`, while the registry identity carries a short `iy` prefix;
- `iydamascus_steel_block` uses `damascus_steel_block_top` as the base/top texture and separately registers `damascus_steel_block_side`; its side selector compares the side index with `ForgeDirection.UP/DOWN.ordinal()`.

rev223 therefore:
- admits only a unique short leading registry prefix difference (<=3 normalized chars) when matching block texture fallback;
- interprets the fixed Forge 1.7.10 `ForgeDirection` ordinals for bounded icon-table evaluation, allowing top/side recovery instead of flattening Damascus Steel to one texture.

## Cache invalidation

`BuildInfo.CONVERTER_REVISION` is bumped to:

`2026-09-30.223-flat-item-projectile-blocktexture`

This is required. The user's live log showed source-hash/revision cache skips; without a new converter fingerprint, repaired Bamboo/iYAMATO candidate resources could remain stale even after replacing the main bridge.

## Source commits in this repair slice

- `c79d6b9c27b60cd52cdd918fce04c35bd8b26fb9` — accept canonical copied-arrow watcher 16 byte at remote spawn.
- `5b515e2deee52ace074da9d19638402e577d229c` — materialize proven flat BlockItem sprite in modern item texture path.
- `a568ca9bee9b7199a0f50a5006bce93f9497a711` — unique short-prefix block texture recovery.
- `70ff58399d4c033a5be062b730c0405ce39405f5` — ForgeDirection icon-selector ordinals.
- `7df0f0bdb657697e87ad8a184f9809ed64abb8ab` — flat BlockItem final ownership over earlier geometry ITEM_MODEL entries.
- `b0617563c01a65a14440769933f6a9a112a203b3` — converter fingerprint bump.
- `277ed20c684a45a4d124ceafb569731ca0e87d4a` — generic copied-arrow RenderSnowball analyzer admission.
- Regression/source-proof commits include `2c5c26490a62b45e2900dd8a69116dc8d5eaf8b5`, `b802b57f4f7759e79f4a6d8e44e13f63977402f6`, `06eb05d24558ef4496d0b952b4b85c339fce1499`, `8e93a9cd57fdb74eca347514b6badf5a8fe32d83`, `7c5bef21751bfaf935d6c9947f85950ca09333e8`, `1de3772aeb03dfd156afc13c904acfa3bd7c59ba`.

## Validation boundary

Performed now:
- exact source JAR SHA checks;
- direct class hierarchy/interface inspection for all 24 iY weapon/helper entities;
- direct bytecode inspection of EntityRegister, ClientProxy and ItemRegister for ExtendedReach/RenderSnowball/invisible carrier;
- direct bytecode/resource inspection of all four iY block texture/icon paths;
- direct resource inspection of Bamboo world and inventory bamboo-shoot PNGs;
- source-level regression fixtures added for flat final ownership, short-prefix texture match, ForgeDirection ordinals and copied-arrow watcher/admission.

Not performed:
- no complete corpus4 main-JAR assembly, because the exact cumulative `corpus4-local.6-rev220-local-test.1` main binary/base is absent from GitHub, current conversation files and Library;
- no Gradle/Loom clean build: this container has JDK 21.0.11 but no Gradle/Loom dependency cache/network;
- no Windows/Prism/Minecraft/Via live launch for rev223.

Do not claim an installable rev223 main JAR until the exact current main binary (or equivalent complete cumulative build base) is supplied/recovered and the complete binary is assembled and audited.

No Actions dispatch, PR, tag, release, workflow modification or main/Bamboo branch modification.
