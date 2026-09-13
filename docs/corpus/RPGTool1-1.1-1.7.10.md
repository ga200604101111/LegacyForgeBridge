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

The old client uses `IItemRenderer`, `AdvancedModelLoader` / `IModelCustom` and direct `GL11` transforms. Converted OBJ parsing/rendering is owned by the generic LegacyForgeBridge renderer; RPGTool only supplies corpus-known model identities until automatic renderer-bytecode extraction is complete.

### Legacy gameplay behavior observed

The real classes also implement behavior which is intentionally tracked separately from registry/model conversion:

- weapon attack/defense/lifesteal gem NBT;
- skill gems such as night vision, underwater breathing, range attack and charged attacks;
- wing jump boost and fall-damage cancellation;
- legacy recipes and event-bus hooks.

Those behaviors are not claimed complete merely because the content candidate loads.

## Semantic conversion slice

For the exact SHA, `RpgTool1Profile` contributes corpus data to the common conversion engine:

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
   -> preserve only source-provided locale zh_CN as modern zh_cn
   -> record item/translation identities in manifest
-> modern resource path normalization
-> corpus presentation metadata
   -> emit the original mod-owned creative group through generic creativeTabs schema
   -> emit wing/circle OBJ declarations through generic equipmentRender schema
-> legacy language cleanup
-> final staged-bytecode audit
-> Fabric candidate writer
```

The converter **does not synthesize `zh_tw`** when the source mod does not provide it. Locale migration preserves source locale availability rather than translating or copying another locale.

The runtime presentation features are not RPGTool-specific. `ConvertedContentRuntime` creates any manifest-declared custom creative group, and `ConvertedEquipmentRenderRuntime` registers any manifest-declared wearable OBJ model. RPGTool is the first corpus supplying those definitions.

The candidate embeds:

```text
legacyforgebridge/converted-content.json
```

LFB reads this at Fabric initialization and creates the modern registry items. The same manifest exposes original legacy mod IDs/versions so the FML handshake can advertise:

```text
rpgtool1=1.0
```

rather than being rejected as a missing client mod by a Forge 1.7.10 server.

## Managed activation

Fabric Loader discovers mods before `LegacyForgeBridge.onInitialize()` runs. Generated candidates are therefore staged across launches under LFB management. Cache identity remains:

```text
converter version + source SHA-256
```

The alpha.17 converter version bump forces RPGTool to be regenerated so older candidates containing synthesized `zh_tw`, vanilla creative-tab placement, or missing wearable presentation metadata cannot remain silently cached.

## Current acceptance boundary

Expected testable surface after the activation restart:

```text
Fabric Loader loads the managed converted candidate
legacy Forge classes do not enter the modern class path
71 RPGTool registry identities exist on the client
source zh_cn item names/icons load without an invented zh_tw locale
the mod receives its own converted creative group instead of dumping all items into vanilla groups
weapon OBJ assets use the generic bounds-aware special renderer for GUI/ground/fixed contexts
wing/circle items register the generic manifest-driven worn OBJ renderer
FML Client ModList can advertise rpgtool1=1.0
unchanged source/converter skips repeated conversion
updated source SHA or converter version forces deterministic rebuild and managed replacement
```

Still expected to require follow-up semantic work:

```text
gem socketing/effects
skill combat behavior
recipes
wing movement/fall behavior
exact legacy IItemRenderer GL11 per-context transforms where bytecode extraction has not yet recovered them
```

The real server test remains authoritative for runtime positioning and texture-candidate validation.
