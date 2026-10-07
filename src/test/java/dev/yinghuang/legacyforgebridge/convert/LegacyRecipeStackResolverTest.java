package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyRecipeStackResolverTest {
    private static final LegacyRecipeAnalyzer.RegistryValue MOD_ITEM = new LegacyRecipeAnalyzer.RegistryValue(
            LegacyRegistryAnalyzer.Kind.ITEM,
            "teaCup",
            "fixture",
            "fixture/Items",
            "field_tea"
    );
    private static final LegacyRecipeAnalyzer.RegistryValue VANILLA_BLOCK = new LegacyRecipeAnalyzer.RegistryValue(
            LegacyRegistryAnalyzer.Kind.BLOCK,
            "wool",
            "minecraft",
            "net/minecraft/init/Blocks",
            "field_150325_L"
    );

    @Test void exactCorpusConstructorShapesReduceToIdentityCountAndMetadata() {
        assertStack(MOD_ITEM, 1, 0, MOD_ITEM);
        assertStack(MOD_ITEM, 4, 0, stack("(Lnet/minecraft/item/Item;I)V", MOD_ITEM, 4));
        assertStack(MOD_ITEM, 2, 7, stack("(Lnet/minecraft/item/Item;II)V", MOD_ITEM, 2, 7));
        assertStack(VANILLA_BLOCK, 1, 0, stack("(Lnet/minecraft/block/Block;)V", VANILLA_BLOCK));
        assertStack(VANILLA_BLOCK, 5, 0, stack("(Lnet/minecraft/block/Block;I)V", VANILLA_BLOCK, 5));
        assertStack(VANILLA_BLOCK, 3, 11, stack("(Lnet/minecraft/block/Block;II)V", VANILLA_BLOCK, 3, 11));
    }

    @Test void wildcardMetadataIsPreservedForLaterIngredientSemantics() {
        var resolved = LegacyRecipeStackResolver.resolve(stack(
                "(Lnet/minecraft/item/Item;II)V",
                MOD_ITEM,
                1,
                LegacyRecipeStackResolver.WILDCARD_META
        )).orElseThrow();
        assertTrue(resolved.wildcardMeta());
    }

    @Test void unknownConstructorOrUnresolvedIdentityFailsClosed() {
        assertTrue(LegacyRecipeStackResolver.resolve(new LegacyRecipeAnalyzer.ObjectValue(
                "net/minecraft/item/ItemStack",
                "(Ljava/lang/Object;)V",
                List.of(MOD_ITEM)
        )).isEmpty());
        assertTrue(LegacyRecipeStackResolver.resolve(new LegacyRecipeAnalyzer.ObjectValue(
                "net/minecraft/item/ItemStack",
                "(Lnet/minecraft/item/Item;)V",
                List.of(new LegacyRecipeAnalyzer.FieldValue("unknown/Owner", "mystery", "Lnet/minecraft/item/Item;"))
        )).isEmpty());
    }

    private static LegacyRecipeAnalyzer.ObjectValue stack(String descriptor, LegacyRecipeAnalyzer.RegistryValue registry, int... values) {
        java.util.ArrayList<LegacyRecipeAnalyzer.Value> args = new java.util.ArrayList<>();
        args.add(registry);
        for (int value : values) args.add(new LegacyRecipeAnalyzer.NumberValue(value));
        return new LegacyRecipeAnalyzer.ObjectValue("net/minecraft/item/ItemStack", descriptor, args);
    }

    private static void assertStack(
            LegacyRecipeAnalyzer.RegistryValue expectedRegistry,
            int expectedCount,
            int expectedMeta,
            LegacyRecipeAnalyzer.Value value
    ) {
        var stack = LegacyRecipeStackResolver.resolve(value).orElseThrow();
        assertEquals(expectedRegistry, stack.registry());
        assertEquals(expectedCount, stack.count());
        assertEquals(expectedMeta, stack.meta());
    }
}
