# 2026-10-07 — Twilight Forest 2.3.8 compatibility, Part 1 checkpoint

Branch: `feature/generic-conversion-iyamato-corpus3`

Corpus: `twilightforest-1.7.10-2.3.8-tw.jar`

Scope of Part 1: registry identity, CreativeTab membership, ordinary recipes, enchanted recipe outputs,
OreDictionary registrations, smelting, and custom map-cloning recipes. Entity, TileEntity, GUI,
dimension, worldgen, structure and renderer work are explicitly not part of this checkpoint.

## Part 1A — Item / Block registration identity

The source uses helper wrappers around GameRegistry:

- `TFItems.registerTFItem(...)`
- `TFBlocks.registerMyBlock(...)`

The generic interprocedural registry analyzer can propagate through these helpers without a Twilight
Forest selector.

Exact source callsite census:

- registered items: 110
- registered blocks: 61

Two declared static fields are intentionally not promoted because the source does not register them
through the proven lifecycle path:

- `wandPacification`
- `castleUnlock`

Aurora single/double slabs remain two independent block registrations.

No Twilight Forest registry-name or class allowlist is required for Part 1A.

## Part 1B — CreativeTab

Twilight Forest declares its creative tab as a concrete subclass of `CreativeTabs`; Block
membership is commonly assigned inside constructors/super-constructors rather than directly at the
registration site.

The rev262 checkpoint adds allocation-specific Block CreativeTab proof. It follows the actual
registered allocation and constructor arguments rather than assigning membership per implementation
class.

Whole-corpus block result:

- registered blocks: 61
- source-proven TF CreativeTab members: 57
- source-proven hidden: 4
- unknown: 0

The four hidden source registrations are portal, trophy, huge gloom block, and lit cinder furnace.

The conditional same-class case is preserved:

- `BlockTFCinderFurnace(Boolean.FALSE)` -> source tab present
- `BlockTFCinderFurnace(Boolean.TRUE)` -> hidden

The exact rev262 source/patch/test handoff is stored under
`diagnostics/rev262-tf-creative/`. It is not yet promoted into the canonical current production
tree because the user-supplied rev260 runtime contains newer local-overlay production sources that
are still missing from this branch. See
`docs/SESSION-2026-10-07-REV260-REV262-HANDOFF-GAP.md`.

## Part 1C — Recipe corpus census

Direct bytecode census from `TFRecipes`:

- `OreDictionary.registerOre`: 12
- `GameRegistry.addSmelting`: 6
- direct `GameRegistry.addShapelessRecipe`: 26
- `GameRegistry.addRecipe(ItemStack,Object[])` instructions in the class: 46
- calls to the two `addEnchantedRecipe` helpers: 25
- `GameRegistry.addRecipe(IRecipe)`: 3

Two of the 46 shaped-registration instructions are inside the two helper bodies themselves. After
interprocedural expansion, the actual recipe count is:

- shaped: 44 direct + 25 helper-instantiated = 69
- shapeless: 26
- smelting: 6
- custom cloning: 3
- total recipes: 104
- OreDictionary registrations: 12

### Enchanted outputs

The old helper creates an ItemStack, stores it in a local, applies one or two
`ItemStack.addEnchantment / func_77966_a` mutations, then passes that same stack to GameRegistry.

The production recipe analyzer now retains these source-proven mutations in recipe IR and propagates
helper parameters to lifecycle callsites. Materialization maps only fixed vanilla 1.7.10 Enchantment
static fields to current vanilla enchantment ids. Unknown/custom enchantments and ambiguous levels
fail closed instead of silently removing the enchantment.

Twilight Forest uses the following 1.7.x SRG fields in these recipes and all are covered by the
pinned 1.7.x table:

`field_77327_f`, `field_77328_g`, `field_77329_d`, `field_77330_e`,
`field_77332_c`, `field_77334_n`, `field_77335_o`, `field_77337_m`,
`field_77341_i`, `field_77346_s`, `field_77347_r`, `field_77349_p`.

Production/source commits:

- `a64234959422c89e3066975d6776765f97b6b305` — enchanted recipe result provenance/materialization.

### Custom map-cloning recipes

The three source `IRecipe` registrations all use the same source behavior family:

- one filled source item;
- one or more blank source items;
- no unrelated non-empty input;
- output count = blank occupied slots + 1;
- output item = filled source item;
- output legacy metadata = source filled stack metadata;
- source custom display name copied when present;
- recipe size 9 and null preview result.

Admission is structural and does not use `TFMapCloningRecipe`, Twilight Forest package names,
registry names or map names.

A changed `blankCount + 2` synthetic implementation is rejected.

Production/source commits:

- `133ceb880d938a90e34a3f5660eecd276192444c` — generic cloning family proof.
- `e81093d80a13020ac799a4b2e6731f47684081ec` — explicitly require blankCount + 1.
- `2ec8a5fea2058a8197752d3049b4d3b4c5fb57db` — shared
  `legacyforgebridge:legacy_clone` serializer, candidate JSON materialization and bootstrap wiring.

The current 1.21.11 runtime implementation stores only the two resolved item ids and the proven
custom-name-copy flag. It copies the LFB legacy metadata component and, when proven, CUSTOM_NAME.
Arbitrary modern components are not duplicated.

## Pass-order check

`GenericContentPass` executes before `LegacyRecipeMaterializationPass` and records both item and
block modern identities in the ConversionContext. Therefore cloning recipe materialization receives
the modern item identities after they have been established.

## Validation boundary

Performed:

- source JAR callsite census with `javap`;
- direct bytecode inspection of `TFMapCloningRecipe`;
- renamed synthetic clone-family positive/negative regression committed;
- source-proven enchantment mutation regression committed;
- 1.21.11 mapped API surface checked for CustomRecipe, RecipeSerializer MapCodec/StreamCodec,
  CraftingInput, Identifier codec, ItemStack item/component access and DataComponents.CUSTOM_NAME;
- branch ref verified after all continuation commits.

Not claimed:

- no GitHub Actions run (repository policy explicitly forbids triggering it);
- no full current Gradle/Loom build;
- no Minecraft client launch of this Part 1 source tree;
- no claim that recipes whose ingredients depend on later unsupported content semantics are playable
  until the surrounding converted items/blocks themselves are admitted.

## Next stage

Part 2 should begin with entity registration identity only:

1. `EntityRegistry.registerModEntity`
2. `EntityRegistry.registerGlobalEntityID`
3. source entity class, tracking range, update frequency and velocity-update flag
4. dynamic/global numeric-ID provenance
5. spawn-egg/color data only when source-proven

Do not mix TileEntity, dimension/worldgen or entity rendering into the first Part 2 checkpoint.
