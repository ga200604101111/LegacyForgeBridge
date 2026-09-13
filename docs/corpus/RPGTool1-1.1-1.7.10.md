# RPGTool1-1.1-1.7.10 corpus baseline

This is the first real LegacyForgeBridge conversion corpus. The third-party binary is used as an external test input and is **not committed to the repository**.

## Exact identity

```text
file: RPGTool1-1.1-1.7.10.jar
size: 14,556,748 bytes
SHA-256: b82cd54d2d2db576e82ba02ea4e55b4db92174c5814b45aa3ebbe1b44d98961d
main class: mhzd.net.rpgtool1.Main
modid: rpgtool1
embedded version: 1.0
mcversion: 1.7.10
```

The filename says `1.1`, while `mcmod.info`/FML report `1.0`. Conversion identity trusts parsed metadata and preserves the source filename separately.

## Analyzer baseline

The exact SHA must continue to produce:

```text
classes: 53
unreadableClasses: 0
hasMcmodInfo: true
hasManifest: true
forgeReferenceCount: 29
minecraftReferenceCount: 104
coremodReferenceCount: 0
openglReferenceCount: 1
OpenGL marker: org/lwjgl/opengl/GL11
```

A changed result for the same SHA is treated as a converter/analyzer regression until explicitly reviewed.

## Real binary inspection

The September 2026 corpus inspection confirmed that `mhzd.net.rpgtool1.init.ModItem` constructs **71 registered items** and `RegistryHandler.registerItem()` registers them through Forge `GameRegistry` using the unlocalized item name (without the `item.` prefix) as the registry name.

The 71 items are:

```text
20 weapons
8 wings
15 circle/aura armor items
28 material / attack / lifesteal / defense / skill gem items
```

The exact weapon durability/custom attack values are extracted from the real bytecode rather than inferred from filenames.

### Legacy rendering

The binary contains 20 weapon OBJ models under the legacy mixed-case path:

```text
assets/rpgtool1/textures/items3D/*.obj
```

It also contains paired wing OBJ models and three circle/aura OBJ meshes:

```text
assets/rpgtool1/textures/wings/left_wing01..08.obj
assets/rpgtool1/textures/wings/right_wing01..08.obj
assets/rpgtool1/textures/circle/buff1.obj
assets/rpgtool1/textures/circle/buff2.obj
assets/rpgtool1/textures/circle/buff3.obj
```

The old client uses `IItemRenderer`, `AdvancedModelLoader` / `IModelCustom` and direct `GL11` transforms. The alpha.14 test slice replaces the **weapon** renderer with LFB's modern 1.21.11 `SpecialModelRenderer` + `SubmitNodeCollector.submitCustomGeometry` OBJ path. The mixed-case `items3D` directory is normalized to `items3d` because modern resource identifiers reject uppercase path characters.

### Legacy gameplay behavior observed

The real classes also implement behavior which is intentionally tracked separately from registry/model conversion:

- weapon attack/defense/lifesteal gem NBT;
- skill gems such as night vision, underwater breathing, range attack and charged attacks;
- wing jump boost and fall-damage cancellation;
- equipped wing/circle rendering;
- legacy recipes and event-bus hooks.

Those behaviors are not claimed complete merely because the content candidate loads.

## alpha.14 semantic conversion slice

For the exact SHA, `RpgTool1Profile` now contributes:

```text
common resource copy
-> legacy .lang conversion
-> exact corpus guard
-> RPGTool1 semantic content pass
   -> remove all 53 obsolete Forge class files from candidate
   -> remove mcmod.info
   -> normalize items3D -> items3d
   -> emit 71 modern content definitions
   -> emit modern item model definitions
   -> map 20 weapon OBJ models to legacyforgebridge:obj
   -> promote item translations to item.rpgtool1.<id>
   -> mirror source zh_CN item names into zh_cn + zh_tw for testing
   -> record item/translation identities in manifest
-> final staged-bytecode audit
-> Fabric candidate writer
```

The candidate additionally embeds:

```text
legacyforgebridge/converted-content.json
```

LFB reads this at Fabric initialization and creates the modern registry items. The same manifest exposes original legacy mod IDs/versions so the FML handshake can advertise:

```text
rpgtool1=1.0
```

rather than being rejected as a missing client mod by a Forge 1.7.10 server.

## Current acceptance boundary

alpha.14 is a **live-test candidate**, not a claim that every RPGTool gameplay feature is already ported.

Expected testable surface:

```text
Fabric Loader can load the converted candidate
legacy Forge classes do not enter the modern class path
71 RPGTool registry identities exist on the client
item names/icons load
weapon durability and base attack values are reconstructed
20 weapon OBJ assets use the modern LFB OBJ renderer
FML Client ModList can advertise rpgtool1=1.0
```

Still expected to require follow-up semantic work:

```text
gem socketing/effects
skill combat behavior
recipes
wing movement/fall behavior
equipped wing OBJ rendering
equipped circle/aura OBJ rendering
modded numeric registry packet remapping if Via exposes a gap during live server testing
```

The real server test is authoritative for those runtime boundaries.
