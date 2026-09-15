package dev.yinghuang.legacyforgebridge.convert;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.BuildInfo;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

        LegacyStoragePresentationAnalyzer.Analysis storagePresentation =
                new LegacyStoragePresentationAnalyzer().analyze(source, List.of(storage.sourceBlockClass()));
        assertTrue(storagePresentation.diagnostics().isEmpty(), storagePresentation.diagnostics().toString());
        assertEquals(1, storagePresentation.presentations().size());
        var presentation = storagePresentation.presentations().get(storage.sourceBlockClass());
        assertEquals(LegacyStoragePresentationAnalyzer.ORIENTATION_PLAYER_YAW_OPPOSITE_QUADRANT,
                presentation.orientation());
        assertEquals("bamboo:jpchest_f", presentation.frontTexture());
        assertEquals("bamboo:jpchest_o", presentation.otherTexture());

        LegacySingleInputProcessorAnalyzer.Analysis processorAnalysis =
                new LegacySingleInputProcessorAnalyzer().analyze(source);
        assertTrue(processorAnalysis.diagnostics().isEmpty(), processorAnalysis.diagnostics().toString());
        assertEquals(1, processorAnalysis.rules().size(), processorAnalysis.skipped().toString());
        LegacySingleInputProcessorAnalyzer.Rule millStone = processorAnalysis.rules().getFirst();
        assertEquals("bambooMillStone", millStone.registryName());
        assertEquals("ruby/bamboo/block/BlockMillStone", millStone.sourceBlockClass());
        assertEquals("ruby/bamboo/tileentity/TileEntityMillStone", millStone.sourceTileClass());
        assertEquals("MillStone", millStone.legacyTileId());
        assertEquals(3, millStone.slots());
        assertEquals(64, millStone.stackLimit());
        assertEquals(0, millStone.inputSlot());
        assertEquals(List.of(1, 2), millStone.outputSlots());
        assertEquals(List.of(0), millStone.topSlots());
        assertEquals(List.of(2, 1), millStone.bottomSlots());
        assertEquals(List.of(0), millStone.sideSlots());
        assertEquals(400, millStone.processTicks());
        assertEquals(64.0, millStone.interactionDistanceSq());
        assertEquals(1, millStone.guiId());
        assertEquals("ruby/bamboo/item/crafting/GrindManager", millStone.recipeManagerOwner());
        assertEquals("getOutput", millStone.recipeLookupName());
        assertEquals("(Lnet/minecraft/item/ItemStack;)Lruby/bamboo/api/crafting/grind/IGrindRecipe;",
                millStone.recipeLookupDescriptor());
        assertTrue(millStone.comparator());
        assertTrue(millStone.dropContents());
        assertTrue(millStone.legacyEnergyApiPresent());

        LegacySingleInputProcessorRuntimeAnalyzer.Proof runtimeProof =
                new LegacySingleInputProcessorRuntimeAnalyzer().analyze(source, millStone);
        assertTrue(runtimeProof.complete(), runtimeProof.diagnostics().toString());
        assertTrue(runtimeProof.sidedExtractionProven());
        assertTrue(runtimeProof.legacyEnergyApiPresent());
        assertEquals(100, runtimeProof.minUseEnergy());
        assertEquals(500, runtimeProof.maxUseEnergy());
        assertEquals("innerEnergy", runtimeProof.energyNbtKey());
        assertTrue(runtimeProof.energyAccelerationProven());
        assertEquals(6, LegacySingleInputProcessorRuntimeAnalyzer.sourceProgressStep(1, 500, 100));
        assertEquals(-100, LegacySingleInputProcessorRuntimeAnalyzer.sourceEnergyAfterStep(1, 500, 100));

        LegacySingleInputProcessorEnergyIngressAnalyzer.Proof ingressProof =
                new LegacySingleInputProcessorEnergyIngressAnalyzer().analyze(source, millStone, runtimeProof);
        assertTrue(ingressProof.complete(), ingressProof.diagnostics().toString());
        assertTrue(ingressProof.legacyEnergyApiPresent());
        assertTrue(ingressProof.allSidesConnect());
        assertTrue(ingressProof.extractionDisabled());
        assertTrue(ingressProof.queryMethodsReturnZero());
        assertTrue(ingressProof.receiveSimulationProven());

        LegacySingleInputProcessorRecipeAnalyzer.Analysis grindRecipes =
                new LegacySingleInputProcessorRecipeAnalyzer().analyze(source, millStone);
        assertTrue(grindRecipes.diagnostics().isEmpty(), grindRecipes.diagnostics().toString());
        assertEquals(13, grindRecipes.recipes().size());

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
            assertEquals(2, storageRules.get("schemaVersion").getAsInt());
            assertEquals(1, storageRules.getAsJsonArray("rules").size());
            JsonObject storageRule = storageRules.getAsJsonArray("rules").get(0).getAsJsonObject();
            assertEquals("bamboomod:jpchest", storageRule.get("id").getAsString());
            assertEquals(54, storageRule.get("slots").getAsInt());
            assertEquals(6, storageRule.get("rows").getAsInt());
            assertEquals(64, storageRule.get("stackLimit").getAsInt());
            assertTrue(storageRule.get("presentationComplete").getAsBoolean());
            assertFalse(storageRule.get("presentationPending").getAsBoolean());
            assertEquals(LegacyStoragePresentationAnalyzer.ORIENTATION_PLAYER_YAW_OPPOSITE_QUADRANT,
                    storageRule.get("orientation").getAsString());
            assertEquals("bamboo:blocks/jpchest_f", storageRule.get("frontTexture").getAsString());
            assertEquals("bamboo:blocks/jpchest_o", storageRule.get("otherTexture").getAsString());
            assertEquals(1, storageRules.get("presentationCompleteRules").getAsInt());
            assertEquals(0, storageRules.get("presentationPendingRules").getAsInt());

            JsonObject blockState = readJson(jar, "assets/bamboomod/blockstates/jpchest.json");
            JsonObject variants = blockState.getAsJsonObject("variants");
            assertEquals("bamboomod:block/jpchest", variants.getAsJsonObject("legacy_meta=0").get("model").getAsString());
            assertEquals(90, variants.getAsJsonObject("legacy_meta=1").get("y").getAsInt());
            assertEquals(180, variants.getAsJsonObject("legacy_meta=2").get("y").getAsInt());
            assertEquals(270, variants.getAsJsonObject("legacy_meta=3").get("y").getAsInt());
            assertEquals("bamboomod:block/jpchest_all_other",
                    variants.getAsJsonObject("legacy_meta=15").get("model").getAsString());

            JsonObject blockModel = readJson(jar, "assets/bamboomod/models/block/jpchest.json");
            JsonObject textures = blockModel.getAsJsonObject("textures");
            assertEquals("bamboo:blocks/jpchest_f", textures.get("north").getAsString());
            assertEquals("bamboo:blocks/jpchest_o", textures.get("south").getAsString());
            assertNotNull(jar.getJarEntry("assets/bamboomod/models/item/jpchest.json"));
            assertNotNull(jar.getJarEntry("assets/bamboomod/items/jpchest.json"));
            assertNotNull(jar.getJarEntry("assets/bamboo/textures/blocks/jpchest_f.png"));
            assertNotNull(jar.getJarEntry("assets/bamboo/textures/blocks/jpchest_o.png"));

            JsonObject processors = readJson(jar, "legacyforgebridge/single-input-processor-rules.json");
            assertEquals(4, processors.get("schemaVersion").getAsInt());
            assertEquals(1, processors.getAsJsonArray("machines").size());
            assertEquals(1, processors.get("runtimeProofCompleteMachines").getAsInt());
            assertEquals(1, processors.get("energyIngressProofCompleteMachines").getAsInt());
            assertEquals(1, processors.get("baseRuntimeCompleteMachines").getAsInt());
            assertEquals(1, processors.get("presentationProofCompleteMachines").getAsInt());
            assertEquals(1, processors.get("guiPresentationRuntimeCompleteMachines").getAsInt());
            assertEquals(1, processors.get("worldPresentationRuntimeCompleteMachines").getAsInt());
            assertEquals(1, processors.get("inventoryPresentationRuntimeCompleteMachines").getAsInt());
            assertEquals(1, processors.get("particlePresentationRuntimeCompleteMachines").getAsInt());
            assertEquals(1, processors.get("sourcePresentationCompleteMachines").getAsInt());
            assertEquals(1, processors.get("runtimeCompleteMachines").getAsInt());
            JsonObject machine = processors.getAsJsonArray("machines").get(0).getAsJsonObject();
            assertEquals("bamboomod:bamboomillstone", machine.get("id").getAsString());
            assertEquals("MillStone", machine.get("legacyTileId").getAsString());
            assertEquals(3, machine.get("slots").getAsInt());
            assertEquals(400, machine.get("processTicks").getAsInt());
            assertEquals(1, machine.get("legacyGuiId").getAsInt());
            assertTrue(machine.get("legacyEnergyApiPresent").getAsBoolean());
            assertEquals(100, machine.get("minUseEnergy").getAsInt());
            assertEquals(500, machine.get("maxUseEnergy").getAsInt());
            assertEquals("innerEnergy", machine.get("energyNbtKey").getAsString());
            assertTrue(machine.get("energyAccelerationProven").getAsBoolean());
            assertTrue(machine.get("runtimeProofComplete").getAsBoolean());
            assertTrue(machine.get("energyIngressProofComplete").getAsBoolean());
            assertTrue(machine.get("energyAllSidesConnectProven").getAsBoolean());
            assertTrue(machine.get("energyExtractionDisabledProven").getAsBoolean());
            assertTrue(machine.get("energyQueryZeroSemanticsProven").getAsBoolean());
            assertTrue(machine.get("energyReceiveSimulationProven").getAsBoolean());
            assertTrue(machine.get("baseRuntimeComplete").getAsBoolean());
            assertTrue(machine.get("tickingRuntimeComplete").getAsBoolean());
            assertTrue(machine.get("menuRuntimeComplete").getAsBoolean());
            assertTrue(machine.get("sidedTransferRuntimeComplete").getAsBoolean());
            assertTrue(machine.get("progressNbtRuntimeComplete").getAsBoolean());
            assertTrue(machine.get("energyStorageRuntimeComplete").getAsBoolean());
            assertTrue(machine.get("energyIngressRuntimeComplete").getAsBoolean());
            assertTrue(machine.get("genericScreenRuntimeComplete").getAsBoolean());
            assertTrue(machine.get("sourcePresentationComplete").getAsBoolean());
            assertTrue(machine.get("runtimeComplete").getAsBoolean());
            assertEquals(13, machine.get("sourceRecipeCount").getAsInt());
            assertEquals(13, machine.get("materializedRecipeCount").getAsInt());
            assertEquals(13, processors.get("materializedRecipes").getAsInt());
            assertEquals(0, processors.get("skippedRecipes").getAsInt());

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
