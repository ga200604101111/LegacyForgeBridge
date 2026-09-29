# 2026-09-29 — generic conversion follow-up: Bamboo, iYAMATO and RPGTool

Branch: `feature/generic-conversion-iyamato-corpus3`

This session continues the generic Forge 1.7.10 conversion work. The fixes below are deliberately
source-structure based. No Bamboo, iYAMATO or RPGTool registry-name allowlist was added to production
conversion logic.

## External corpus identities

- BambooMod 2.6.8.5 exact corpus already used by the repository:
  SHA-256 `bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402`.
- RPGTool1-1.1-1.7.10 exact corpus:
  SHA-256 `b82cd54d2d2db576e82ba02ea4e55b4db92174c5814b45aa3ebbe1b44d98961d`.
- iYAMATO's Mod 1.7.10-1.6.8 public file identity:
  SHA-256 `35a79ca5a034fd6cda4fe4ee1f658c4ce058f9b7ec026516e76363ebdf89635e`,
  size 1,029,340 bytes, mod id `iymts_mod`.

The exact iYAMATO 1.6.8 JAR was not available in the current workspace, so this session does not
claim a fresh exact-corpus conversion run for that SHA.

## 1. Mimic material chains

The runtime resolver already walks directional mimic chains, but unknown renderer views were limited
to immediate-neighbour reads. A source -> mimic -> terminal material therefore failed outside the
vanilla RenderSectionRegion snapshot case.

`LegacyMimicReadWindow.localTwoHopChain` now admits exactly two local hops for unknown views.
Cycles, invalid directions, the 256 traversal budget and the guarded read window remain fail-closed.
The new regression explicitly covers `mimic -> mimic -> terminal` and rejects a third hop.

This is a bounded rendering-read change, not arbitrary long-distance copying and not a Bamboo class
special case.

## 2. Flat BlockItem presentation

A source Forge `ISimpleBlockRenderingHandler` can prove that the world block is custom-rendered
while `shouldRender3DInInventory` is false. For that case the final simple-block pass now prefers
one uniquely matched source `textures/item(s)/*.png` sprite. If the source item sprite is absent or
ambiguous, it falls back to the already proven world material instead of guessing.

This fixes the Bamboo shoot class of problem where a real 2D BlockItem sprite exists but the
converted inventory model previously flattened the placed-block texture.

## 3. Durability and legacy tool attributes

A generic durability analyzer/pass now recovers:

- allocation-specific `setMaxDamage` / `func_77656_e`;
- exact always-visible durability-bar overrides;
- the exact inverse formula
  `1 - stack.getItemDamage() / stack.getMaxDamage()`.

The runtime uses the same proof for both bar width and bar colour. An inverse source bar therefore
starts visually empty/red and fills toward green as damage increases, matching the source formula.

For vanilla 1.7 tool families, the combat analyzer can additionally prove an exact constructor
ToolMaterial and project the corresponding legacy melee attribute. It currently admits pickaxe,
axe and shovel families only when the superclass constructor and material are source-proven.
The Bamboo pickaxe exact regression expects durability 10000 and legacy attack damage 5.0
(ItemPickaxe base 2 + EMERALD material bonus 3).

Modern attack speed is recorded as an adaptation because Forge 1.7.10 did not expose the modern
attack-speed attribute. Mining tags/speed, repairability and other modern tool behaviour are not
invented by this proof.

## 4. Vanilla block material delegation

The icon interpreter now maps the vanilla 1.7 `soul_sand` block identity and accepts the one-argument
vanilla block icon route in geometry mode. This allows source blocks whose fallback/ordinary icon
delegates to `Blocks.soul_sand` to retain that material instead of becoming unresolved.

The conversion does not infer soul sand from a class name such as VillagerBlock; the source icon
delegation itself is the evidence.

## 5. Generic Block GameRegistry recovery

Registry-name dataflow now recognises both Item and Block unlocalized-name APIs, including
`Block#getUnlocalizedName` / `func_149739_a` and `setBlockName` / `func_149663_c`.

The bounded derived-name collection rule was generalized from Item-only to both:

`static List<T> -> constructor self-enrolment -> iterator -> getUnlocalizedName().substring(5)
-> GameRegistry.registerItem/registerBlock`.

Static field bindings are likewise recovered for Item and Block identities. Regression coverage
includes both a direct static Block derived-name registration and a self-enrolled `List<Block>`
registration loop.

## 6. Source creative tabs

Creative-tab membership now prefers the exact GameRegistry static-field binding over constructor
string heuristics. This prevents multi-string constructors such as
`new Gun("musket", "musket_texture", ...)` from using the last unrelated string as the identity.

The stale-GETSTATIC regression also prevents an earlier static item read from stealing the
`setCreativeTab` call of a fresh allocation.

The analyzer and presentation pass now cover both Item and Block content. Block
`setCreativeTab` / SRG `func_149647_a` is source-proven and the converted BlockItem can therefore
appear in the same generated creative tab. No unassigned content is automatically exposed.

## 7. Projectile presentation coverage

Projectile analysis now separates two different source identities:

- the registered item whose use/release callback creates and spawns the projectile;
- the registered item selected by the bound renderer as its presentation carrier.

Supported source launch callbacks include ordinary right-click and release-use
(`onPlayerStoppedUsing` / `func_77615_a`), including inherited source callbacks. A unique launcher
is still required.

Vanilla `RenderSnowball` is admitted for both EntityThrowable and EntityArrow source families when
the exact renderer binding proves one registered presentation item. The carrier may intentionally
differ from the launcher. Source-typed static fields that inherit Item are accepted; the field does
not have to be declared exactly as `Item`.

Both `RenderSnowball(Item)` and `RenderSnowball(Item, constantMetadata)` are supported. Fixed
metadata uses the existing default-item-metadata field and does not require a dynamic DataWatcher.
Dynamic selector state still requires the existing watcher proof.

The projectile presentation pass now resolves the modern carrier by
`sourceClass + legacyRegistryName`, with source-class-only fallback only when that class is unique.
This avoids the same implementation class registered multiple times overwriting another carrier.

A source renderer that deliberately selects a transparent carrier remains transparent. The
converter does not substitute a visible bullet texture merely because a file named bullet exists.

## 8. RPGTool and allocation-specific weapon damage

Combat-item analysis now follows the source numeric path from a registered constructor argument
through an instance field into the attack-damage AttributeModifier. Rules are keyed by registration
identity rather than implementation class alone.

This covers shared weapon classes whose individual registered instances carry different damage and
durability constants. The generated runtime publishes the proven attack-damage attribute and hides
only the modern attack-speed compatibility row.

## Validation performed in this session

- Added/updated generic unit regressions for mimic two-hop reads, flat BlockItem sprites, inverse
  durability, per-registration weapon constants, vanilla ToolMaterial melee projection,
  RenderSnowball carrier separation/fixed metadata/inherited release callbacks, source-typed
  carrier fields, creative-tab stale fields/multi-string constructors/Block membership, and
  direct/iterable derived Block registration.
- Re-scanned all production Java files changed since the rev221 baseline for unbalanced braces,
  missing imports and missing helper definitions; no remaining issue was found in that static scan.
- Re-scanned the affected/new test Java files for the same structural problems.
- Checked the newly used Minecraft 1.21.11 API surface against current mappings:
  `Item#getBarColor(ItemStack)`, `Mth.hsvToRgb`,
  `Item.Properties#attributes`, and `ToolMaterial#attackDamageBonus()`.

### CI limitation

A temporary draft pull request (#18) was opened only to trigger the repository's
`pull_request` workflow. Multiple runs failed before execution with `runner_id=0` and an empty
`steps` array. Checkout, Java setup and Gradle never ran, so these failures provide no compile/test
result and must not be reported as a failed or successful build.

## Required live / exact-corpus acceptance

For iYAMATO 1.7.10-1.6.8, rerun the converter with the exact SHA above and inspect:

1. all source GameRegistry block identities are emitted;
2. source creative tabs contain Musket and any source-assigned converted blocks;
3. Contract Documents remote projectile uses the source-bound visible document carrier;
4. Musket projectile uses the exact source RenderSnowball carrier and remains visually transparent
   only when the source renderer deliberately selected a transparent carrier;
5. no unrelated Item/Block with ambiguous registry or renderer provenance is admitted.

For Bamboo, verify in-game that dirt -> mimic(dirt) -> mimic resolves through the intermediate mimic,
the shoot uses its source 2D inventory sprite, and the pickaxe shows max damage 10000 with the
source inverse progress presentation.

For RPGTool, inspect several weapons sharing the same implementation class and confirm their
individual source damage attributes remain distinct.
