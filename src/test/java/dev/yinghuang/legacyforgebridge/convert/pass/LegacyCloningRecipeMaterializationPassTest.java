package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyCloningRecipeAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyRecipeAnalyzer;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyCloningRecipeMaterializationPassTest {
    @TempDir Path tempDir;

    @Test void sourceProvenCloneRuleBecomesSharedSerializerRecipeJson() throws Exception {
        ConversionContext context = context();
        context.recordRegistryIdentity("items", "fixture:fullMap", "fixture:full_map");
        context.recordRegistryIdentity("items", "fixture:blankMap", "fixture:blank_map");

        LegacyCloningRecipeAnalyzer.Rule rule = new LegacyCloningRecipeAnalyzer.Rule(
                new LegacyCloningRecipeAnalyzer.ItemRef("fullMap", "fixture", "foreign/items/Items", "FULL"),
                new LegacyCloningRecipeAnalyzer.ItemRef("blankMap", "fixture", "foreign/items/Items", "BLANK"),
                true,
                "foreign/clone/ReplicaRule",
                "foreign/recipes/Bootstrap",
                "init");

        LegacyRecipeMaterializationPass.Summary summary = LegacyRecipeMaterializationPass.materialize(
                context,
                new LegacyRecipeAnalyzer.Analysis(List.of(), List.of()),
                new LegacyCloningRecipeAnalyzer.Analysis(List.of(rule), List.of()));

        assertEquals(1, summary.emittedRecipes());
        assertEquals(0, summary.skippedRecipes());

        Path output = tempDir.resolve("staging/data/fixture/recipe/legacy_clone_0000.json");
        assertTrue(Files.isRegularFile(output));
        JsonObject json = JsonParser.parseString(Files.readString(output, StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("legacyforgebridge:legacy_clone", json.get("type").getAsString());
        assertEquals("fixture:full_map", json.get("full_item").getAsString());
        assertEquals("fixture:blank_map", json.get("blank_item").getAsString());
        assertTrue(json.get("copy_custom_name").getAsBoolean());

        JsonObject report = JsonParser.parseString(Files.readString(
                tempDir.resolve("staging/legacyforgebridge/recipe-materialization.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(1, report.get("provenCloningRecipes").getAsInt());
        assertEquals(1, report.get("emittedCloningRecipes").getAsInt());
    }

    private ConversionContext context() throws Exception {
        Path staging = tempDir.resolve("staging");
        Files.createDirectories(staging);
        LegacyModMetadata metadata = new LegacyModMetadata(
                "fixture.jar", "test",
                List.of(new LegacyModMetadata.ModEntry("fixture", "Fixture", "1.0", "1.7.10", List.of())));
        LegacyJarAnalyzer.Analysis analysis = new LegacyJarAnalyzer.Analysis(
                "fixture.jar", 0, 0, false, false,
                0, 0, 0, 0,
                Set.of(), Set.of(), Set.of());
        return new ConversionContext(
                tempDir.resolve("fixture.jar"),
                staging,
                tempDir.resolve("candidate.jar"),
                "sha",
                0L,
                metadata,
                analysis,
                new DiagnosticCollector(),
                "generic-test");
    }
}
