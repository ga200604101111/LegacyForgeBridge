# 2026-09-20 — Terminal block-state restoration and native subtype state

Converter revision: `2026-09-20.140`. Base commit: `578bc8fab722a783209af1cb7389d1c3afcb3c34`.

## Report and evidence boundaries

The user still sees wrong names/models and blocks changing identity when placed. The available
old launch log is revision .137, not a fresh .139 reproduction. Its 63 Forge block identities
and 1,008 block-state tokens are useful test inputs, not evidence of which current client build
was running. The original Bamboo 2.6.8.5 input SHA-256 remains
`bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402`.
No original mod binary is committed.

## Missing terminal block handler

The exact ViaFabricPlus 4.4.15 artifact's ViaVersion mapping from 1.21.9 to 1.21.11 has an
identity block-state map of size 29,671. BlockRewriter uses that identity to skip registering
single/section updates and to skip chunk palette rewriting. The previous LFB mapping hook
therefore never ran at the native edge for these paths: a transport token was exposed as a
native registry raw ID, potentially selecting a different native block.

ViaTerminalBlockRewriteMixin disables only that final protocol's block-state identity shortcut.
It covers all seven relevant call sites, including block updates, multi-updates, level events
and modern chunk palettes. It does not change global Mappings behavior or bypass item/block-ID
identity checks. Registration cannot depend on a selected legacy server because Via registers
protocol handlers before a legacy session exists. Existing per-packet LFB mapping remains gated
to 1.7.10 sessions; ordinary vanilla state IDs keep their normal identity mapping.

## One native metadata value

The rev139 network bridge retained metadata in CUSTOM_DATA, while native placement code reads
LegacyStackComponents.legacy_meta, whose item prototype default is zero. Locally created
creative stacks also had no bridge marker, so the old outbound code selected DAMAGE/default zero.

LegacyViaStackComponents now writes the actual native metadata component ID at the final
clientbound edge. ViaNativeMetadataCodecMixin recognizes precisely that component in precisely
the V1_21_11 StructuredDataType instance, never in an earlier protocol. No raw component number
is assumed. Outbound conversion consumes the component before downgrading and copies its unsigned
0..65535 value into the validated session carrier. Real durability is handled separately. The
existing terminal legacy restore still removes bridge-only NBT and preserves original source NBT.

ConvertedStackPresentation applies the same proven default name and item model when native
stacks are created. It does not replace CUSTOM_NAME. Locally projected ITEM_NAME is removed only
when it exactly matches the source catalogue before the first downgrade; names are not forged
into legacy display.Name.

## Source creative enumeration and provisional models

LegacyCreativeVariantsPass interprets actual getSubItems/getSubBlocks callbacks. It preserves
source order and an explicitly empty output, and rejects unknown tab-dependent branches, source
state mutation, foreign item identity, non-unit counts and unrepresented NBT. It does not expose
all 256 inputs probed by an icon getter as if they were source creative variants. Unproved
callbacks retain their previous single default entry. This is subtype enumeration, not a claim
of full source creative-tab membership reconstruction.

The previous icon presentation pass replaced only barrier/terracotta placeholders. Models that
already had a filename-matched texture could still be wrong for all but one subtype. Generic and
baseline model writers now record their provisional outputs by SHA-256. The source-proven icon
pass may replace an unchanged provisional output; a subsequent specialized model rewrite revokes
that ownership. Specialized item renderer definitions stay authoritative. No mod-specific
registry-name-to-texture table or external repair mod is added.

## Original-source resource-stage results

Fresh copy/language/registry/baseline/icon/name/creative/atlas stages completed locally:

- 50 source default models replaced: 20 true placeholders plus 30 texture-backed provisional
  defaults that rev139's placeholder-only gate skipped.
- 88 source creative enumerations proved, containing 237 distinct per-identity metadata stacks;
  17 callbacks remain unproved. These are not 237 new item identities.
- Name analysis still proves the same 96 defaults and 231 distinct translation keys as rev139.
  The 30 corrected base keys are not 30 newly corrected keys in this revision. The new name fix
  connects local creative/placement metadata to those existing name/model catalogues.
- 263 model files rewritten by candidate-owned atlas conversion; 142 sprite aliases materialized.
- All 223 original PNG/animation entries remained byte-identical.

The prior 38 specialized/unresolved default backing models are not claimed complete. The new
30 improvements were texture-backed provisional defaults, not another 30 of those 38. Complex
connected/custom geometry and the previously excluded dynamic/missing-language names remain
separate work. A full semantic conversion may retain additional specialized presentations instead
of replacing their backing models, as intended by the ownership check.

## Verification

The local standalone source harness passed 12 positive/negative conversion checks. The local
terminal-policy harness checked 196,608 unsigned metadata selections plus boundaries and all
seven call sites in the exact upstream runtime bytecode. Normal JUnit wrappers are included.
Additional JUnit tests execute the upstream single/section block handlers with a synthetic
registry and packet transport, demonstrating removal/restoration of the identity-optimized
handlers and 1,008 carrier mappings. These are not a real Minecraft connection or a Mixin startup.
Compiled-boundary and creative-catalogue parser regressions are also included.

The local environment lacks a complete Gradle/Minecraft runtime. Local source tests use a local
ASM compatibility jar and Guava; neither is shipped or committed. CI must perform the actual
full build/test/remap. A successful build remains distinct from live graphics, creative inventory,
chest operations, placement, chunk reload and reconnect validation. Replace the LFB main jar,
keep original old-mods inputs, remove obsolete texturefix mods, and restart after managed
candidates are regenerated before testing the revision.
