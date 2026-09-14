package dev.longyu.legacyforgebridge.convert;

import com.google.gson.JsonObject;
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

class LegacyRecipeJsonMaterializerTest {
    @TempDir Path tempDir;

    @Test void shapedRecipePreservesPatternComponentsAndProvenOreUnion() {
        ConversionContext context = context();
        context.recordRegistryIdentity("items", "fixture:teaCup", "fixture:tea_cup");
        context.recordRegistryIdentity("items", "fixture:rice", "fixture:rice");

        var ore = new LegacyRecipeAnalyzer.Analysis(List.of(
                registration(LegacyRecipeAnalyzer.Kind.ORE_REGISTER,
                        new LegacyRecipeAnalyzer.TextValue("oreWood"), stack(vanillaBlock("planks"), 1, 1)),
                registration(LegacyRecipeAnalyzer.Kind.ORE_REGISTER,
                        new LegacyRecipeAnalyzer.TextValue("oreWood"), stack(modItem("rice"), 1, 2))
        ), List.of());
        LegacyOreDictionaryIndex index = LegacyOreDictionaryIndex.build(ore, context);
        assertEquals(2, index.entryCount("oreWood"));

        var recipe = registration(LegacyRecipeAnalyzer.Kind.SHAPED,
                stack(modItem("teaCup"), 2, 3),
                new LegacyRecipeAnalyzer.ArrayValue(List.of(
                        new LegacyRecipeAnalyzer.TextValue("AB"),
                        new LegacyRecipeAnalyzer.TextValue(" A"),
                        new LegacyRecipeAnalyzer.CharacterValue('A'), stack(modItem("rice"), 1, 1),
                        new LegacyRecipeAnalyzer.CharacterValue('B'), new LegacyRecipeAnalyzer.TextValue("oreWood")
                )));

        JsonObject json = LegacyRecipeJsonMaterializer.materialize(recipe, context, index).orElseThrow();
        assertEquals("minecraft:crafting_shaped", json.get("type").getAsString());
        assertEquals("AB", json.getAsJsonArray("pattern").get(0).getAsString());
        assertEquals(3, json.getAsJsonObject("result").getAsJsonObject("components")
                .get(LegacyStackComponents.LEGACY_META_ID.toString()).getAsInt());

        JsonObject a = json.getAsJsonObject("key").getAsJsonObject("A");
        assertEquals("fabric:components", a.get("fabric:type").getAsString());
        assertEquals(1, a.getAsJsonObject("components")
                .get(LegacyStackComponents.LEGACY_META_ID.toString()).getAsInt());
        JsonObject b = json.getAsJsonObject("key").getAsJsonObject("B");
        assertEquals("fabric:any", b.get("fabric:type").getAsString());
        assertEquals(2, b.getAsJsonArray("ingredients").size());
    }

    @Test void shapelessAndSmeltingUseCurrentRecipeShapes() {
        ConversionContext context = context();
        context.recordRegistryIdentity("items", "fixture:teaCup", "fixture:tea_cup");
        LegacyOreDictionaryIndex empty = LegacyOreDictionaryIndex.build(
                new LegacyRecipeAnalyzer.Analysis(List.of(), List.of()), context);

        var shapeless = registration(LegacyRecipeAnalyzer.Kind.SHAPELESS,
                stack(modItem("teaCup"), 1, 0),
                new LegacyRecipeAnalyzer.ArrayValue(List.of(
                        vanillaItem("coal"),
                        stack(vanillaBlock("wool"), 1, 11)
                )));
        JsonObject shapelessJson = LegacyRecipeJsonMaterializer.materialize(shapeless, context, empty).orElseThrow();
        assertEquals("minecraft:crafting_shapeless", shapelessJson.get("type").getAsString());
        assertEquals(2, shapelessJson.getAsJsonArray("ingredients").size());
        assertEquals("minecraft:blue_wool", shapelessJson.getAsJsonArray("ingredients").get(1).getAsString());

        var smelting = registration(LegacyRecipeAnalyzer.Kind.SMELTING,
                vanillaBlock("iron_ore"),
                stack(modItem("teaCup"), 2, 4),
                new LegacyRecipeAnalyzer.NumberValue(0.35F));
        JsonObject smeltingJson = LegacyRecipeJsonMaterializer.materialize(smelting, context, empty).orElseThrow();
        assertEquals("minecraft:smelting", smeltingJson.get("type").getAsString());
        assertEquals("minecraft:iron_ore", smeltingJson.get("ingredient").getAsString());
        assertEquals(2, smeltingJson.getAsJsonObject("result").get("count").getAsInt());
        assertEquals(4, smeltingJson.getAsJsonObject("result").getAsJsonObject("components")
                .get(LegacyStackComponents.LEGACY_META_ID.toString()).getAsInt());
        assertEquals(200, smeltingJson.get("cookingtime").getAsInt());
    }

    @Test void unprovenOreNameAndMalformedPatternFailClosed() {
        ConversionContext context = context();
        context.recordRegistryIdentity("items", "fixture:teaCup", "fixture:tea_cup");
        LegacyOreDictionaryIndex empty = LegacyOreDictionaryIndex.build(
                new LegacyRecipeAnalyzer.Analysis(List.of(), List.of()), context);

        var missingOre = registration(LegacyRecipeAnalyzer.Kind.SHAPELESS,
                modItem("teaCup"),
                new LegacyRecipeAnalyzer.ArrayValue(List.of(new LegacyRecipeAnalyzer.TextValue("oreMissing"))));
        assertFalse(LegacyRecipeJsonMaterializer.materialize(missingOre, context, empty).isPresent());

        var malformed = registration(LegacyRecipeAnalyzer.Kind.SHAPED,
                modItem("teaCup"),
                new LegacyRecipeAnalyzer.ArrayValue(List.of(
                        new LegacyRecipeAnalyzer.TextValue("AA"),
                        new LegacyRecipeAnalyzer.TextValue("A"),
                        new LegacyRecipeAnalyzer.CharacterValue('A'), vanillaItem("stick")
                )));
        assertTrue(LegacyRecipeJsonMaterializer.materialize(malformed, context, empty).isEmpty());
    }

    private LegacyRecipeAnalyzer.Registration registration(
            LegacyRecipeAnalyzer.Kind kind, LegacyRecipeAnalyzer.Value... args) {
        return new LegacyRecipeAnalyzer.Registration(kind, List.of(args), "fixture/Recipes", "init", "()V");
    }

    private LegacyRecipeAnalyzer.RegistryValue modItem(String name) {
        return new LegacyRecipeAnalyzer.RegistryValue(
                LegacyRegistryAnalyzer.Kind.ITEM, name, "fixture", "fixture/Items", "field_" + name);
    }

    private LegacyRecipeAnalyzer.RegistryValue vanillaItem(String name) {
        return new LegacyRecipeAnalyzer.RegistryValue(
                LegacyRegistryAnalyzer.Kind.ITEM, name, "minecraft", "net/minecraft/init/Items", "field_vanilla");
    }

    private LegacyRecipeAnalyzer.RegistryValue vanillaBlock(String name) {
        return new LegacyRecipeAnalyzer.RegistryValue(
                LegacyRegistryAnalyzer.Kind.BLOCK, name, "minecraft", "net/minecraft/init/Blocks", "field_vanilla");
    }

    private LegacyRecipeAnalyzer.ObjectValue stack(
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
