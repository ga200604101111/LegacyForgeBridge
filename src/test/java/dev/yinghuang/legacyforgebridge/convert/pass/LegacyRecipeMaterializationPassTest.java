package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.compat.LegacyStackComponents;
import dev.yinghuang.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyRecipeAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyRegistryAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyRecipeMaterializationPassTest {
    @TempDir Path tempDir;

    @Test void completeRecipesAreWrittenAndUnprovenOnesStayOutOfCandidateResources() throws Exception {
        ConversionContext context = context();
        context.recordRegistryIdentity("items", "fixture:teaCup", "fixture:tea_cup");
        context.recordRegistryIdentity("items", "fixture:rice", "fixture:rice");

        LegacyRecipeAnalyzer.Analysis analysis = new LegacyRecipeAnalyzer.Analysis(List.of(
                registration(LegacyRecipeAnalyzer.Kind.ORE_REGISTER,
                        new LegacyRecipeAnalyzer.TextValue("foodRice"), modItem("rice")),
                registration(LegacyRecipeAnalyzer.Kind.SHAPELESS,
                        modItem("teaCup"),
                        new LegacyRecipeAnalyzer.ArrayValue(List.of(
                                new LegacyRecipeAnalyzer.TextValue("foodRice"), vanillaItem("stick")))),
                registration(LegacyRecipeAnalyzer.Kind.SHAPELESS,
                        modItem("teaCup"),
                        new LegacyRecipeAnalyzer.ArrayValue(List.of(
                                new LegacyRecipeAnalyzer.TextValue("missingGlobalOre"))))
        ), List.of());

        LegacyRecipeMaterializationPass.Summary summary =
                LegacyRecipeMaterializationPass.materialize(context, analysis);
        assertEquals(1, summary.emittedRecipes());
        assertEquals(1, summary.skippedRecipes());
        assertEquals(1, summary.oreDictionaryNames());

        Path emitted = tempDir.resolve("staging/data/fixture/recipe/legacy_shapeless_0000.json");
        assertTrue(Files.isRegularFile(emitted));
        JsonObject json = JsonParser.parseString(Files.readString(emitted, StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("minecraft:crafting_shapeless", json.get("type").getAsString());
        JsonObject rice = json.getAsJsonArray("ingredients").get(0).getAsJsonObject();
        assertEquals("fabric:components", rice.get("fabric:type").getAsString());
        assertEquals("fixture:rice", rice.get("base").getAsString());
        assertEquals(0, rice.getAsJsonObject("components")
                .get(LegacyStackComponents.LEGACY_META_ID.toString()).getAsInt());
        assertFalse(Files.exists(tempDir.resolve("staging/data/fixture/recipe/legacy_shapeless_0001.json")));

        JsonObject report = JsonParser.parseString(Files.readString(
                tempDir.resolve("staging/legacyforgebridge/recipe-materialization.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(1, report.get("emittedRecipes").getAsInt());
        assertEquals(1, report.get("skippedRecipes").getAsInt());
        assertTrue(context.diagnostics().snapshot().stream()
                .anyMatch(diagnostic -> "LFB-CONVERT-RECIPE-0011".equals(diagnostic.ruleId())));
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

    private ConversionContext context() throws Exception {
        Path staging = tempDir.resolve("staging");
        Files.createDirectories(staging);
        LegacyModMetadata metadata = new LegacyModMetadata(
                "fixture.jar", "test",
                List.of(new LegacyModMetadata.ModEntry("fixture", "Fixture", "1.0", "1.7.10", List.of())));
        LegacyJarAnalyzer.Analysis jarAnalysis = new LegacyJarAnalyzer.Analysis(
                "fixture.jar", 0, 0, false, false,
                0, 0, 0, 0,
                Set.of(), Set.of(), Set.of());
        return new ConversionContext(
                tempDir.resolve("fixture.jar"), staging, tempDir.resolve("candidate.jar"),
                "sha", 0L, metadata, jarAnalysis, new DiagnosticCollector(), "generic-test");
    }
}
