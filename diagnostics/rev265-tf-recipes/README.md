# rev265 Twilight Forest recipe checkpoint — shared cloning recipe runtime

Date: 2026-10-07

## Implemented

Part 1C-2b connects the source-proven cloning recipe family to a shared Minecraft 1.21.11 runtime.

- Registers `legacyforgebridge:legacy_clone` as one bridge-owned `RecipeSerializer`.
- Emits candidate recipe JSON from `LegacyCloningRecipeAnalyzer.Rule` only when both source item
  identities resolve to exact modern item ids.
- Runtime accepts exactly one filled item plus one-or-more blank-item slots and rejects any other
  non-empty input.
- Output count is blank-slot-count + 1.
- Output carries the source filled stack's LFB legacy metadata component.
- If the source proof includes custom-name copying, only `minecraft:custom_name` is copied.
- Arbitrary modern components are not duplicated.

The serializer is generic. No Twilight Forest class, registry name, map name, or namespace is used by
the runtime or materialization rule.

## Corpus target

Together with the rev264 structural analyzer this covers the three Twilight Forest 2.3.8 custom
map-cloning registrations (magic, maze, ore) without converting them to an inexact shapeless recipe.

## Verification status

Source and synthetic materialization regression are committed. The public 1.21.11 mapped API surface
used here was checked before authoring. A full Gradle/Loom rebuild is still not claimed because the
user-supplied rev260 runtime contains local-overlay production source that has not yet been fully
recovered into this branch.
