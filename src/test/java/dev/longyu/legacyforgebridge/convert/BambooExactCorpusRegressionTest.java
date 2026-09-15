package dev.longyu.legacyforgebridge.convert;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.longyu.legacyforgebridge.BuildInfo;
import dev.longyu.legacyforgebridge.convert.api.ConversionStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Formal exact Bamboo corpus gate. This test is never silently skipped when its task is invoked. */
@Tag("exact-corpus")
class BambooExactCorpusRegressionTest {
    private static final String BAMBOO_SHA256 = "bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";

    @TempDir
    Path tempDir;

    @Test
    void exactBambooWholeJarBaselineIsDeterministicAndStillFailClosed() throws Exception {
        String input = System.getProperty("lfb.exactCorpus.jar");
        assertNotNull(input, "Run exactCorpusTest with -PlfbExactCorpusJar=/path/to/Bamboo-2.6.8.5.jar or LFB_EXACT_CORPUS_JAR");
        Path source = Path.of(input);
        assertTrue(Files.isRegularFile(source), "Exact Bamboo corpus path must be a regular file: " + source);
        assertEquals(BAMBOO_SHA256, Hashing.sha256(source), "Wrong Bamboo corpus binary");

        LegacyRegistryAnalyzer.Analysis registry = new LegacyRegistryAnalyzer().analyze(source);
        assertEquals(42, registry.items().size());
        assertEquals(63, registry.blocks().size());

        LegacyLifecycleAnalyzer.Analysis lifecycle = new LegacyLifecycleAnalyzer().analyze(source);
        assertTrue(lifecycle.diagnostics().isEmpty(), lifecycle.diagnostics().toString());
        assertEquals(24, lifecycle.of(LegacyLifecycleAnalyzer.Kind.ENTITY).size());
        assertEquals(11, lifecycle.of(LegacyLifecycleAnalyzer.Kind.TILE_ENTITY).size());
        assertEquals(1, lifecycle.of(LegacyLifecycleAnalyzer.Kind.ENTITY_SPAWN).size());
        assertEquals(1, lifecycle.of(LegacyLifecycleAnalyzer.Kind.GUI_HANDLER).size());
        assertEquals(1, lifecycle.of(LegacyLifecycleAnalyzer.Kind.WORLD_GENERATOR).size());
        assertEquals(1, lifecycle.of(LegacyLifecycleAnalyzer.Kind.DIMENSION_PROVIDER).size());
        assertEquals(1, lifecycle.of(LegacyLifecycleAnalyzer.Kind.DIMENSION).size());
        assertEquals(1, lifecycle.of(LegacyLifecycleAnalyzer.Kind.DIMENSION_UNREGISTER).size());
        assertEquals(1, lifecycle.of(LegacyLifecycleAnalyzer.Kind.DIMENSION_PROVIDER_UNREGISTER).size());

        LegacyRecipeAnalyzer.Analysis recipes = new LegacyRecipeAnalyzer().analyze(source);
        assertTrue(recipes.diagnostics().isEmpty(), recipes.diagnostics().toString());
        assertEquals(134, recipes.of(LegacyRecipeAnalyzer.Kind.SHAPED).size());
        assertEquals(23, recipes.of(LegacyRecipeAnalyzer.Kind.SHAPELESS).size());
        assertEquals(5, recipes.of(LegacyRecipeAnalyzer.Kind.SMELTING).size());
        assertEquals(24, recipes.of(LegacyRecipeAnalyzer.Kind.ORE_REGISTER).size());
        assertEquals(1, recipes.of(LegacyRecipeAnalyzer.Kind.FUEL_HANDLER).size());

        LegacyStorageBlockAnalyzer.Analysis storageAnalysis = new LegacyStorageBlockAnalyzer().analyze(source);
        assertTrue(storageAnalysis.diagnostics().isEmpty(), storageAnalysis.diagnostics().toString());
        assertEquals(1, storageAnalysis.rules().size());
        LegacyStorageBlockAnalyzer.Rule storage = storageAnalysis.rules().getFirst();
        assertEquals("jpChest", storage.registryName());
        assertEquals("ruby/bamboo/block/BlockJpchest", storage.sourceBlockClass());
        assertEquals("ruby/bamboo/tileentity/TileEntityJPChest", storage.sourceTileClass());
        assertEquals("JP Chest", storage.legacyTileId());
        assertEquals(54, storage.slots());
        assertEquals(6, storage.rows());
        assertEquals(64, storage.stackLimit());
        assertEquals("Chest", storage.title());
        assertEquals(64.0, storage.interactionDistanceSq());
        assertTrue(storage.sneakingPass());
        assertTrue(storage.dropContents());
        assertTrue(storage.comparator());

        LegacyConversionEngine engine = new LegacyConversionEngine();
        var first = engine.convert(source, tempDir.resolve("converted-a"), tempDir.resolve("manifest-a"));
        var second = engine.convert(source, tempDir.resolve("converted-b"), tempDir.resolve("manifest-b"));
        assertEquals(ConversionStatus.PARTIAL, first.status());
        assertFalse(first.installable());
        Path firstCandidate = first.candidateJar().orElseThrow();
        Path secondCandidate = second.candidateJar().orElseThrow();
        assertEquals(Hashing.sha256(firstCandidate), Hashing.sha256(secondCandidate),
                "Same exact input and converter revision must produce identical candidate bytes");

        try (JarFile jar = new JarFile(firstCandidate.toFile())) {
            JsonObject content = readJson(jar, "legacyforgebridge/converted-content.json");
            assertEquals(42, content.getAsJsonArray("items").size());
            assertEquals(63, content.getAsJsonArray("blocks").size());

            JsonObject storageRules = readJson(jar, "legacyforgebridge/storage-block-rules.json");
            assertEquals(1, storageRules.getAsJsonArray("rules").size());
            JsonObject storageRule = storageRules.getAsJsonArray("rules").get(0).getAsJsonObject();
            assertEquals("bamboomod:jpchest", storageRule.get("id").getAsString());
            assertEquals(54, storageRule.get("slots").getAsInt());
            assertEquals(6, storageRule.get("rows").getAsInt());
            assertEquals(64, storageRule.get("stackLimit").getAsInt());
            assertTrue(storageRule.get("presentationPending").getAsBoolean());

            JsonObject recipeMaterialization = readJson(jar, "legacyforgebridge/recipe-materialization.json");
            assertEquals(125, recipeMaterialization.get("emittedRecipes").getAsInt());
            assertEquals(37, recipeMaterialization.get("skippedRecipes").getAsInt());

            JsonObject fuel = readJson(jar, "legacyforgebridge/fuel-rules.json");
            assertEquals(2, fuel.getAsJsonArray("rules").size());
            assertEquals(0, fuel.get("skippedRules").getAsInt());

            JsonObject dependencies = readJson(jar, "legacyforgebridge/class-dependency-analysis.json");
            assertEquals(341, dependencies.get("classCount").getAsInt());
            assertEquals(341, dependencies.getAsJsonArray("classes").size());
            assertEquals(0, dependencies.get("exclusionAuthorizations").getAsInt());
            dependencies.getAsJsonArray("classes").forEach(value ->
                    assertFalse(value.getAsJsonObject().get("action").getAsString().equals("exclude")));

            JsonObject manifest = readJson(jar, "legacyforgebridge/conversion-manifest.json");
            assertEquals(BuildInfo.CONVERSION_SCHEMA, manifest.get("schemaVersion").getAsInt());
            assertEquals(BuildInfo.VERSION, manifest.get("converterVersion").getAsString());
            assertEquals(BuildInfo.CONVERTER_REVISION, manifest.get("converterRevision").getAsString());
            assertEquals(BAMBOO_SHA256, manifest.getAsJsonObject("source").get("sha256").getAsString());
            assertEquals("partial", manifest.get("status").getAsString());
            assertFalse(manifest.get("installable").getAsBoolean());
        }
    }

    private static JsonObject readJson(JarFile jar, String path) throws Exception {
        var entry = jar.getJarEntry(path);
        assertNotNull(entry, "Missing exact-corpus output " + path);
        try (InputStreamReader reader = new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
