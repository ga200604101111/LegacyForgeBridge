package dev.yinghuang.legacyforgebridge.convert;

import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacySingleInputProcessorRecipeMaterializerTest {
    @TempDir Path tempDir;

    @Test
    void concreteVanillaProcessorRecipeUsesCurrentStackIdentity() {
        var recipe = new LegacySingleInputProcessorRecipeAnalyzer.Recipe(
                new LegacySingleInputProcessorRecipeAnalyzer.StackInput(stack(vanillaBlock("gravel"), 1, 0)),
                stack(vanillaItem("flint"), 2, 0),
                LegacyRecipeAnalyzer.NullValue.INSTANCE,
                0.25F,
                "fixture/Recipes", "register", "()V");
        var json = LegacySingleInputProcessorRecipeMaterializer.materialize(recipe, context()).orElseThrow();
        assertEquals(1, json.getAsJsonArray("inputAlternatives").size());
        assertEquals("minecraft:gravel", json.getAsJsonArray("inputAlternatives").get(0).getAsJsonObject().get("id").getAsString());
        assertEquals("minecraft:flint", json.getAsJsonObject("output").get("id").getAsString());
        assertEquals(2, json.getAsJsonObject("output").get("count").getAsInt());
        assertEquals(0.25F, json.get("bonusChance").getAsFloat());
    }

    @Test
    void legacyBlockWildcardExpandsThroughDfuInsteadOfChoosingMetaZero() {
        var recipe = new LegacySingleInputProcessorRecipeAnalyzer.Recipe(
                new LegacySingleInputProcessorRecipeAnalyzer.StackInput(
                        stack(vanillaBlock("leaves"), 4, LegacyRecipeStackResolver.WILDCARD_META)),
                stack(vanillaItem("dye"), 1, 2),
                LegacyRecipeAnalyzer.NullValue.INSTANCE,
                0F,
                "fixture/Recipes", "register", "()V");
        var json = LegacySingleInputProcessorRecipeMaterializer.materialize(recipe, context()).orElseThrow();
        int alternatives = json.getAsJsonArray("inputAlternatives").size();
        assertTrue(alternatives > 1, "1.7 leaves wildcard should cover more than one flattened modern block identity");
        assertTrue(alternatives <= 16);
        for (var value : json.getAsJsonArray("inputAlternatives")) {
            assertEquals(4, value.getAsJsonObject().get("count").getAsInt());
        }
    }

    @Test
    void oreKeyInputRemainsFailClosedUntilTagExpansionExists() {
        var recipe = new LegacySingleInputProcessorRecipeAnalyzer.Recipe(
                new LegacySingleInputProcessorRecipeAnalyzer.OreInput("oreCopper", 1),
                stack(vanillaItem("flint"), 1, 0),
                LegacyRecipeAnalyzer.NullValue.INSTANCE,
                0F,
                "fixture/Recipes", "register", "()V");
        assertFalse(LegacySingleInputProcessorRecipeMaterializer.materialize(recipe, context()).isPresent());
    }

    private static LegacyRecipeAnalyzer.RegistryValue vanillaItem(String name) {
        return new LegacyRecipeAnalyzer.RegistryValue(
                LegacyRegistryAnalyzer.Kind.ITEM, name, "minecraft", "net/minecraft/init/Items", "field_vanilla");
    }

    private static LegacyRecipeAnalyzer.RegistryValue vanillaBlock(String name) {
        return new LegacyRecipeAnalyzer.RegistryValue(
                LegacyRegistryAnalyzer.Kind.BLOCK, name, "minecraft", "net/minecraft/init/Blocks", "field_vanilla");
    }

    private static LegacyRecipeAnalyzer.ObjectValue stack(
            LegacyRecipeAnalyzer.RegistryValue registry, int count, int meta) {
        String parameter = registry.kind() == LegacyRegistryAnalyzer.Kind.BLOCK
                ? "Lnet/minecraft/block/Block;" : "Lnet/minecraft/item/Item;";
        return new LegacyRecipeAnalyzer.ObjectValue(
                "net/minecraft/item/ItemStack",
                "(" + parameter + "II)V",
                List.of(registry, new LegacyRecipeAnalyzer.NumberValue(count), new LegacyRecipeAnalyzer.NumberValue(meta)));
    }

    private ConversionContext context() {
        LegacyModMetadata metadata = new LegacyModMetadata(
                "fixture.jar", "test",
                List.of(new LegacyModMetadata.ModEntry("fixture", "Fixture", "1.0", "1.7.10", List.of())));
        LegacyJarAnalyzer.Analysis analysis = new LegacyJarAnalyzer.Analysis(
                "fixture.jar", 0, 0, false, false,
                0, 0, 0, 0,
                Set.of(), Set.of(), Set.of());
        return new ConversionContext(
                tempDir.resolve("fixture.jar"), tempDir.resolve("staging"), tempDir.resolve("candidate.jar"),
                "sha", 0L, metadata, analysis, new DiagnosticCollector(), "generic-test");
    }
}
