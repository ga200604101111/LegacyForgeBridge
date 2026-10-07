# rev263 / Twilight Forest recipe checkpoint — enchanted result preservation

Date: 2026-10-07
Branch: `feature/generic-conversion-iyamato-corpus3`

## Corpus finding

Twilight Forest 2.3.8 `TFRecipes.registerRecipes()` contains:

- 12 `OreDictionary.registerOre` calls;
- 6 `GameRegistry.addSmelting` calls;
- 46 direct shaped `GameRegistry.addRecipe(ItemStack,Object[])` calls;
- 26 direct shapeless calls;
- 25 calls through two `addEnchantedRecipe` helper overloads;
- 3 custom `TFMapCloningRecipe` registrations through `GameRegistry.addRecipe(IRecipe)`.

The 25 helper recipes construct an ItemStack, store it in a local, apply one or two
`ItemStack.addEnchantment / func_77966_a` mutations, then register the stack.

## Generic fix

`LegacyRecipeAnalyzer` now retains source-proven ItemStack enchantment mutations as recipe IR and
propagates their helper parameters interprocedurally. No Twilight Forest method/class/registry name
is used by the production rule.

Only vanilla 1.7.10 Enchantment static fields with a fixed mapping to modern vanilla enchantment ids
are materialized. Unknown/custom enchantment identities, non-integral levels, levels outside 1..255,
or duplicate modern enchantment ids fail closed.

Minecraft 1.21.11 `ItemEnchantments.CODEC` is an unbounded enchantment-id -> level map, so recipe
result components are emitted as `minecraft:enchantments: { "minecraft:...": level }`.

## Remaining Part 1C blocker

The three `TFMapCloningRecipe` registrations are still not admitted by the generic analyzer.
They remain Part 1C-2 and must be handled by source-semantic custom-recipe recognition rather than a
Twilight Forest class-name allowlist.

No claim of a full rev260/rev263 Gradle/Loom rebuild is made here; the rev260 source handoff gaps
remain documented separately.
