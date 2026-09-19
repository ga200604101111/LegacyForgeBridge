# 2026-09-19 — Legacy texture atlas registration only

Base: `feature/generic-conversion-bamboo-corpus2` at `e7b892b395b59ce4218427af79052358818fe720`.

## Scope

Fix missing sprites for converted legacy textures. Do not modify Forge/FML, registry aliases,
ViaVersion item IDs, stone fallback, gameplay, model generation, or converter acceptance gates.

## Evidence from the supplied converted artifacts

The Bamboo candidate has 91 PNGs under `assets/bamboo/textures/blocks/` and 101 under
`assets/bamboo/textures/items/`. Its model JSON contains 61 concrete mod texture references
(46 unique IDs). All their PNG files exist and all IDs are valid, but neither the candidate
nor the bridge supplies an atlas definition collecting those old plural directories.

The RPGTool candidate instead references the modern singular `rpgtool1:item/` directory:
91 reference occurrences, 71 unique IDs, all files present. This specific atlas omission
was not found in those RPGTool model references. Do not claim to have reproduced or repaired
an unrelated RPGTool renderer defect.

Bamboo also has 58 explicitly unresolved baseline models. Their placeholder presentation is
separate from an existing PNG failing to stitch. Those placeholders remain unchanged.

## Fix

Add two additive built-in resource definitions to LegacyForgeBridge:

- `assets/minecraft/atlases/blocks.json`: collect `textures/blocks/`, retain `blocks/` sprite prefix.
- `assets/minecraft/atlases/items.json`: collect `textures/items/`, retain `items/` sprite prefix.

Directory sources scan all namespaces, so this is not a Bamboo-specific rule. Do not place
both directories in the block atlas: Minecraft 1.21.11 separates item textures into the items
atlas. ItemBlock models may still use the block atlas. Do not scan all textures indiscriminately;
GUI, entity, armor, and standalone custom-renderer textures must not be pulled into these atlases.
Native singular `block/` and `item/` paths are left to the default sources. No filters, replacement,
model rewrites, sprite renaming, or PNG copies are introduced.

The resources live in the bridge rather than generated candidates. Existing converted JARs
can therefore use the fix without reconversion. Converter revision remains `2026-09-19.137`
because the conversion output algorithm is unchanged; identify this bridge build by its Git commit.

## Validation

- `python3 tools/test_legacy_texture_atlases.py`: 8/8 resource regressions passed locally.
- Static audit of both supplied candidates: no missing PNGs or invalid IDs among their concrete
  mod model texture references; every such reference is covered by its native or added atlas source.
- All 192 newly collected Bamboo PNGs passed image decoding validation.
- Bamboo concrete reference occurrences outside default atlas coverage: 61 before, 0 after.
- Existing candidate JAR bytes, models, item definitions, metadata and PNGs were not changed.
- The build workflow runs the resource tests before the existing Gradle build.

These are resource/configuration checks, not an executed Minecraft model bake or a live client
rendering test. A full Gradle build and live client validation were not performed locally.
Do not mark all Bamboo presentation or the multiplayer item-identity path complete based on this fix.

## Immediate compatibility hotfix

The same two JSON resources can be packaged as a client-only, resource-only Fabric companion
mod with its own ID. It supplements the existing bridge; it does not replace the bridge or
converted mods. No cache deletion or server changes are required. Remove that companion after
installing a bridge build containing this commit.

## Upstream references

- https://www.minecraft.net/en-us/article/minecraft-snapshot-22w46a (atlas directory sources and prefixes)
- https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-11 (separate items atlas)
