package dev.longyu.legacyforgebridge.convert;

import dev.longyu.legacyforgebridge.compat.LegacyStackComponents;
import dev.longyu.legacyforgebridge.convert.api.ConversionContext;
import dev.longyu.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.longyu.legacyforgebridge.convert.api.LegacyModMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyOreDictionaryConventions1710Test {
    @TempDir Path tempDir;

    @Test void forge1710BuiltinsMapToCurrentInteroperabilityCategories() {
        assertEquals("#c:dyes/blue", LegacyOreDictionaryConventions1710.ingredient("dyeBlue").orElseThrow().getAsString());
        assertEquals("#c:dusts/glowstone", LegacyOreDictionaryConventions1710.ingredient("dustGlowstone").orElseThrow().getAsString());
        assertEquals("#minecraft:planks", LegacyOreDictionaryConventions1710.ingredient("plankWood").orElseThrow().getAsString());
        assertEquals("#minecraft:logs", LegacyOreDictionaryConventions1710.ingredient("logWood").orElseThrow().getAsString());
        assertEquals("#c:ingots/iron", LegacyOreDictionaryConventions1710.ingredient("ingotIron").orElseThrow().getAsString());
        assertEquals("minecraft:quartz_block", LegacyOreDictionaryConventions1710.ingredient("blockQuartz").orElseThrow().getAsString());
        assertFalse(LegacyOreDictionaryConventions1710.ingredient("bambooSpecificName").isPresent());
    }

    @Test void localRegistrationIsUnionedWithForgePlatformCategoryInsteadOfNarrowingIt() {
        ConversionContext context = context();
        context.recordRegistryIdentity("items", "fixture:woodPanel", "fixture:wood_panel");
        var localPanel = new LegacyRecipeAnalyzer.RegistryValue(
                LegacyRegistryAnalyzer.Kind.ITEM, "woodPanel", "fixture", "fixture/Items", "field_panel");
        var analysis = new LegacyRecipeAnalyzer.Analysis(List.of(
                new LegacyRecipeAnalyzer.Registration(
                        LegacyRecipeAnalyzer.Kind.ORE_REGISTER,
                        List.of(new LegacyRecipeAnalyzer.TextValue("plankWood"),
                                new LegacyRecipeAnalyzer.ObjectValue(
                                        "net/minecraft/item/ItemStack",
                                        "(Lnet/minecraft/item/Item;II)V",
                                        List.of(localPanel,
                                                new LegacyRecipeAnalyzer.NumberValue(1),
                                                new LegacyRecipeAnalyzer.NumberValue(2)))),
                        "fixture/Recipes", "init", "()V")
        ), List.of());

        var ingredient = LegacyOreDictionaryIndex.build(analysis, context)
                .ingredient("plankWood").orElseThrow().getAsJsonObject();
        assertEquals("fabric:any", ingredient.get("fabric:type").getAsString());
        assertEquals(2, ingredient.getAsJsonArray("ingredients").size());
        assertEquals("#minecraft:planks", ingredient.getAsJsonArray("ingredients").get(0).getAsString());
        var local = ingredient.getAsJsonArray("ingredients").get(1).getAsJsonObject();
        assertEquals("fixture:wood_panel", local.get("base").getAsString());
        assertEquals(2, local.getAsJsonObject("components")
                .get(LegacyStackComponents.LEGACY_META_ID.toString()).getAsInt());
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
