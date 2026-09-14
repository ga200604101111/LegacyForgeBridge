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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyRecipeStackMaterializerTest {
    @TempDir Path tempDir;

    @Test void vanillaStacksUseDfuWhileModStacksUseInternalLegacyMetaComponent() {
        ConversionContext context = context();
        context.recordRegistryIdentity("items", "fixture:teaCup", "fixture:tea_cup");

        var wool = LegacyRecipeStackMaterializer.materialize(
                spec(LegacyRegistryAnalyzer.Kind.BLOCK, "wool", "minecraft", 2, 11), context).orElseThrow();
        assertEquals("minecraft:blue_wool", wool.id());
        assertEquals(2, wool.count());
        assertTrue(wool.components().isEmpty());
        assertEquals("minecraft:blue_wool", wool.ingredientJson().getAsString());
        assertEquals(2, wool.resultJson().get("count").getAsInt());

        var sword = LegacyRecipeStackMaterializer.materialize(
                spec(LegacyRegistryAnalyzer.Kind.ITEM, "iron_sword", "minecraft", 1, 5), context).orElseThrow();
        assertEquals(5, sword.components().get("minecraft:damage").getAsInt());
        assertEquals("fabric:components", sword.ingredientJson().getAsJsonObject().get("fabric:type").getAsString());

        var custom = LegacyRecipeStackMaterializer.materialize(
                spec(LegacyRegistryAnalyzer.Kind.ITEM, "teaCup", "fixture", 4, 3), context).orElseThrow();
        assertEquals("fixture:tea_cup", custom.id());
        assertEquals(3, custom.components().get(LegacyStackComponents.LEGACY_META_ID.toString()).getAsInt());
        assertEquals(4, custom.resultJson().get("count").getAsInt());
    }

    @Test void wildcardIsSafeForModIngredientButVanillaPreFlatteningWildcardFailsClosed() {
        ConversionContext context = context();
        context.recordRegistryIdentity("items", "fixture:teaCup", "fixture:tea_cup");

        var custom = LegacyRecipeStackMaterializer.materialize(
                spec(LegacyRegistryAnalyzer.Kind.ITEM, "teaCup", "fixture", 1, LegacyRecipeStackResolver.WILDCARD_META),
                context).orElseThrow();
        assertTrue(custom.wildcardMeta());
        assertTrue(custom.components().isEmpty());
        assertEquals("fixture:tea_cup", custom.ingredientJson().getAsString());
        assertThrows(IllegalStateException.class, custom::resultJson);

        assertFalse(LegacyRecipeStackMaterializer.materialize(
                spec(LegacyRegistryAnalyzer.Kind.BLOCK, "wool", "minecraft", 1, LegacyRecipeStackResolver.WILDCARD_META),
                context).isPresent());
    }

    private LegacyRecipeStackResolver.StackSpec spec(
            LegacyRegistryAnalyzer.Kind kind, String name, String namespace, int count, int meta) {
        return new LegacyRecipeStackResolver.StackSpec(
                new LegacyRecipeAnalyzer.RegistryValue(kind, name, namespace, "source/Owner", "field"),
                count,
                meta);
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
