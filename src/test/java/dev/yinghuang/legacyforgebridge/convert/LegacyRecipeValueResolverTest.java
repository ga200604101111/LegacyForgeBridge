package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LegacyRecipeValueResolverTest {
    private final LegacyRecipeValueResolver resolver = new LegacyRecipeValueResolver();

    @Test void platformTableKeepsExact1710SrgAndLegacyRegistryIdentities() {
        assertEquals(171, LegacyVanillaRegistry1710.knownItemFields());
        assertEquals(170, LegacyVanillaRegistry1710.knownBlockFields());

        assertEntry(LegacyVanillaRegistry1710.ITEMS_OWNER, "field_151042_j",
                LegacyRegistryAnalyzer.Kind.ITEM, "iron_ingot");
        assertEntry(LegacyVanillaRegistry1710.BLOCKS_OWNER, "field_150347_e",
                LegacyRegistryAnalyzer.Kind.BLOCK, "cobblestone");

        // These identities changed or disappeared in later Minecraft versions.
        assertEntry(LegacyVanillaRegistry1710.ITEMS_OWNER, "field_151101_aQ",
                LegacyRegistryAnalyzer.Kind.ITEM, "cooked_fished");
        assertEntry(LegacyVanillaRegistry1710.ITEMS_OWNER, "field_151135_aq",
                LegacyRegistryAnalyzer.Kind.ITEM, "wooden_door");
        assertEntry(LegacyVanillaRegistry1710.ITEMS_OWNER, "field_151068_bn",
                LegacyRegistryAnalyzer.Kind.ITEM, "potion");
        assertEntry(LegacyVanillaRegistry1710.BLOCKS_OWNER, "field_150326_M",
                LegacyRegistryAnalyzer.Kind.BLOCK, "piston_extension");
        assertEntry(LegacyVanillaRegistry1710.BLOCKS_OWNER, "field_150396_be",
                LegacyRegistryAnalyzer.Kind.BLOCK, "fence_gate");
        assertEntry(LegacyVanillaRegistry1710.BLOCKS_OWNER, "field_150422_aJ",
                LegacyRegistryAnalyzer.Kind.BLOCK, "fence");
    }

    @Test void recipeFieldsResolveRecursivelyWithoutInventingUnknownValues() {
        var itemField = new LegacyRecipeAnalyzer.FieldValue(
                LegacyVanillaRegistry1710.ITEMS_OWNER, "field_151042_j", "Lnet/minecraft/item/Item;");
        var blockField = new LegacyRecipeAnalyzer.FieldValue(
                LegacyVanillaRegistry1710.BLOCKS_OWNER, "field_150347_e", "Lnet/minecraft/block/Block;");
        var unknownVanilla = new LegacyRecipeAnalyzer.FieldValue(
                LegacyVanillaRegistry1710.ITEMS_OWNER, "field_999999_x", "Lnet/minecraft/item/Item;");
        var unrelated = new LegacyRecipeAnalyzer.FieldValue(
                "other/mod/Content", "field_151042_j", "Lnet/minecraft/item/Item;");

        var input = new LegacyRecipeAnalyzer.ArrayValue(List.of(
                itemField,
                new LegacyRecipeAnalyzer.ObjectValue("net/minecraft/item/ItemStack",
                        "(Lnet/minecraft/block/Block;II)V", List.of(blockField,
                        new LegacyRecipeAnalyzer.NumberValue(2), new LegacyRecipeAnalyzer.NumberValue(3))),
                unknownVanilla,
                unrelated));

        var output = assertInstanceOf(LegacyRecipeAnalyzer.ArrayValue.class, resolver.resolve(input));
        var item = assertInstanceOf(LegacyRecipeAnalyzer.RegistryValue.class, output.elements().get(0));
        assertEquals(LegacyRegistryAnalyzer.Kind.ITEM, item.kind());
        assertEquals("iron_ingot", item.registryName());
        assertEquals("minecraft", item.legacyNamespace());
        assertEquals(itemField.owner(), item.sourceOwner());
        assertEquals(itemField.name(), item.sourceField());

        var stack = assertInstanceOf(LegacyRecipeAnalyzer.ObjectValue.class, output.elements().get(1));
        var block = assertInstanceOf(LegacyRecipeAnalyzer.RegistryValue.class, stack.constructorArguments().get(0));
        assertEquals(LegacyRegistryAnalyzer.Kind.BLOCK, block.kind());
        assertEquals("cobblestone", block.registryName());
        assertEquals("minecraft", block.legacyNamespace());

        assertSame(unknownVanilla, output.elements().get(2));
        assertSame(unrelated, output.elements().get(3));
    }

    @Test void analysisResolutionPreservesRegistrationProvenanceAndDiagnostics() {
        var field = new LegacyRecipeAnalyzer.FieldValue(
                LegacyVanillaRegistry1710.ITEMS_OWNER, "field_151055_y", "Lnet/minecraft/item/Item;");
        var registration = new LegacyRecipeAnalyzer.Registration(
                LegacyRecipeAnalyzer.Kind.SHAPELESS, List.of(field),
                "foreign/recipes/Init", "load", "(Ljava/lang/Object;)V");
        var input = new LegacyRecipeAnalyzer.Analysis(List.of(registration), List.of("source diagnostic"));

        var output = resolver.resolve(input);
        assertEquals(input.diagnostics(), output.diagnostics());
        var resolved = output.registrations().getFirst();
        assertEquals(registration.kind(), resolved.kind());
        assertEquals(registration.sourceOwner(), resolved.sourceOwner());
        assertEquals(registration.sourceMethod(), resolved.sourceMethod());
        assertEquals(registration.sourceDescriptor(), resolved.sourceDescriptor());
        var item = assertInstanceOf(LegacyRecipeAnalyzer.RegistryValue.class, resolved.arguments().getFirst());
        assertEquals("stick", item.registryName());
    }

    private static void assertEntry(String owner, String field, LegacyRegistryAnalyzer.Kind kind, String name) {
        var entry = LegacyVanillaRegistry1710.resolve(owner, field).orElseThrow();
        assertEquals(kind, entry.kind());
        assertEquals(name, entry.registryName());
    }
}
