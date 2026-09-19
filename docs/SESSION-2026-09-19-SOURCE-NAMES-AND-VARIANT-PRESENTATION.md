# 2026-09-19 — Source names, enum icon tables and layered presentation

Converter revision: `2026-09-19.139`. Parent: `d56e246ae6b8a9a50107cd4274370cfcec57a2f0`.

## Scope

The fix belongs to LegacyForgeBridge's conversion pipeline, not an extra resource mod.
The user supplied the original Bamboo 2.6.8.5 binary this time. SHA-256:
`bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402`.
The original binary is external test input and is not committed.

## Names

`LegacyItemNamePass` interprets actual source registration helpers and ItemStack naming
methods. The base name must be proven by the helper's setter; it is not inferred from
registry IDs or texture filenames. MCP and SRG getter names are supported. Name prototypes
retain unknown source fields: an unexecuted constructor is never assumed to have initialized
those fields. Custom display-name overrides and unsupported/NBT-dependent branches are excluded.
Only keys already present in the converted source language files are emitted.

The pass corrects `converted-content.json` before generated semantic code and entrypoints.
It also writes metadata name mappings to `legacyforgebridge/item-names.json`.
`ConvertedItemNameCatalog` restores the source-localized default via `ITEM_NAME` at the native
Via boundary. `LegacyItemNameBridge` never replaces `CUSTOM_NAME`; it restores the previous
carrier default before the return trip and removes its bookkeeping at the existing terminal
legacy edge. Names do not become forged `display.Name` tags.

Original-corpus resource-pass validation recovered 96 default name keys, corrected 30 existing
incorrect defaults, and recorded 17,061 metadata-to-key mappings in the bounded 0..255 range.
These reuse 231 distinct source translation keys; they are NOT 17,061 different items.
Six identities lack a matching source-language key, and three custom display-name methods
remain outside this name proof. Existing valid defaults remain available.

## Appearance

The bounded icon interpreter now models Java zero-initialization for actually constructed
objects, enum constructors and fields, map-backed selectors, arraycopy, layered icons and
metadata-only tints. An enum subclass constructor is not mistaken for java.lang.Enum's base
constructor. Source classes are never defined or executed by the JVM.

World positions use distinct unknown coordinate tokens. A neighboring lookup, coordinate-
dependent branch, differing world/item icon or tint, or stateful setter rejects conversion.
Custom render types remain excluded instead of being represented as guessed cubes.
Existing special item definitions stay authoritative.

The presentation pass emits native layered models and tint sources. Proven block tints are
baked into candidate-owned copies, preserving alpha and animation sidecars. Source PNG and
animation bytes remain untouched. Equivalent item definitions are deduplicated. The rev138
candidate-owned atlas pass runs afterward; no global texture-repair resource is reintroduced.

With the supplied rev137 converted candidate as the existing semantic-presentation baseline,
new passes using the ORIGINAL source replaced 20 of its 58 default placeholder models.
This includes tatami, decoration families, leaves, food enums, sliding-door icons, firecracker
icons, shaved-ice layers, boiled egg and source-proven vanilla icon references. 38 default
placeholder files remain. Some are unused backing files for specialized renderers. This is
not an all-58 or full-renderer-equivalence claim. Custom/connected geometry remains separate.

## Verification boundaries

- Nine standalone synthetic-source converter checks passed locally.
- Three standalone name/carrier policy checks passed using the actual Via data container/NBT
  with a synthetic serializer-ID lookup; custom names and unsigned metadata are preserved.
- All twelve checks have JUnit wrappers in the normal Gradle test task.
- An additional checksum-pinned `exact-corpus` JUnit test is excluded from normal CI, following
  the existing external-corpus policy. It requires the original binary explicitly.
- Fresh original-source resource stages (copy, language, registry, baseline, icon, name, atlas)
  completed locally. This is NOT a full semantic code-generation/client startup test.
- New resource stages also completed against the existing converted Bamboo candidate; 225
  models were rewritten by atlas conversion, 133 sprites materialized, and all 223 original
  source PNG/animation entries were byte-identical afterward.
- Local converter checks used a local ASM compatibility jar and local Guava classes. Neither
  dependency is distributed. Normal Gradle CI supplies the declared production dependencies.
- The terminal ViaLegacy ID/metadata protection from rev138 is retained. These name tests do
  not replace a real inventory/chest/creative packet round-trip or Minecraft rendering test.

A CI-built full mod must be tested without the old texturefix mod. Updating the revision stages
new managed converted candidates; restart after conversion before testing them in a server.
