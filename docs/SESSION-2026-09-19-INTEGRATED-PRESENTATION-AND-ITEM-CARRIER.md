# 2026-09-19 — Integrated presentation conversion and terminal item carrier

Converter revision: `2026-09-19.138`. Base: `3b70e3788340525d831c14db78f7ab8b52772010`.

## User requirement

Fix LegacyForgeBridge itself, not a separate texture-fix mod. Also investigate the 58 Bamboo
baseline placeholder models and the Via inventory stone/incorrect-item regression.

## Candidate-owned texture conversion

`LegacyTextureAtlasPass` runs in the conversion engine after presentation passes. It resolves
model-referenced source PNGs, materializes deterministic legal lowercase aliases in the candidate,
copies animation sidecars unchanged, and rewrites model texture references. Candidate-owned atlas
entries are emitted with disjoint block/item sprite IDs. Each item model uses a single atlas, including
inherited mixed-texture models. Source images, geometry and original files are retained.

The two global directory atlas resources and their obsolete Python-only tests are removed. This is
not an additional resource mod. The revision change invalidates old conversion fingerprints; a
newly generated managed candidate needs the normal restart before it is loaded.

## Source-proven icon/metadata presentation

`LegacyIconTableAnalyzer` interprets a bounded subset of legacy bytecode without defining any source
class. It connects verified GameRegistry identities to allocations, constructor arguments, fluent
configuration, icon-registration strings, metadata getters and simple block bounds. Unsupported
calls, ambiguous allocations and custom render types remain exclusions, not guessed cube models.

`LegacyIconPresentationPass` replaces only converter-owned unresolved default models, preserves
specialized outputs, generates supported metadata blockstates and native item-model definitions,
and records evidence in `legacyforgebridge/icon-presentation.json`. Upper-half block state and
inventory geometry are evaluated separately. A runtime catalog selects metadata model definitions
for network stacks. No Bamboo registry-name table is embedded in the implementation.

This does **not** claim all 58 Bamboo placeholders are resolved. Custom renderers, connected/world-
dependent geometry, multi-pass items and source shapes outside the supported interpreter remain
separate work. Some baseline placeholder files can also be unused when a specialized renderer owns
the actual item/world presentation. A count of files is not full renderer equivalence.

## Verified stone fallback mechanism

Inspected the ViaFabricPlus 4.4.15 tag build (commit
`2f00a92851bed1bdab05e2465cadd9b21e4e6b7f`) and its embedded ViaLegacy bytecode.
The previous serverbound restore hook ran at 1.13 -> 1.12.2, before the last 1.8 -> 1.7.10
non-existent-vanilla-item fallback. Forge content in slots 165..169 and 179..192 was consequently
changed to stone. The hook now targets the exact declared methods of
`net.raphimc.vialegacy.protocol.release.r1_7_6_10tor1_8.rewriter.ItemRewriter`: clientbound HEAD,
serverbound RETURN. A paper carrier traverses all intermediate rewrites.

Legacy metadata is retained in bridge-owned CUSTOM_DATA until the final server edge, independently
of modern durability. Item ID and marker are checked against the active session registry mapping.
Any colliding original marker/Damage NBT is preserved and restored with its original tag type.
Only bridge-owned item-model overrides are removed on the return trip. Unknown native identities
have bounded diagnostics rather than silently claiming success.

## Validation performed before branch update

- 23 new standalone Java assertion checks passed using the actual production logic. This local
  harness was **not** the JUnit platform; JUnit regression sources are included for normal Gradle CI.
- The real ViaLegacy fallback implementation was exercised for 69 IDs * 16 metadata values = 1,104
  cases. The old placement reproduced 304 stone fallbacks; terminal restoration preserved all
  1,104 identities/metadata values and checked custom NBT.
- Two additional JUnit tests check compiled Mixin wiring and the target runtime method descriptors.
- New integrated atlas code applied to the supplied **already-converted** Bamboo candidate rewrote
  65 models and materialized 46 sprites with zero missing/ambiguous owned references. Original PNG
  and animation bytes were preserved; generated block/item sprite IDs were disjoint.
- The supplied RPGTool candidate needed no atlas changes.
- Synthetic source conversion replaced three unresolved defaults, generated 288 metadata item
  definitions, and distinguished placed upper-half and held lower-half geometry.

The user's uploaded candidates no longer contain the original Bamboo classes. No checksum-pinned
original `Bamboo-2.6.8.5.jar` was available in this working environment; the public source was used
for inspection only, not substituted as an exact binary corpus. Therefore neither fresh exact-
Bamboo coverage nor an all-58 completion count is claimed. The existing exact-corpus gate is kept.
Full CI and a real Minecraft/ViaFabricPlus connection smoke test are distinct from these local
checks. No live client/server was run here.
