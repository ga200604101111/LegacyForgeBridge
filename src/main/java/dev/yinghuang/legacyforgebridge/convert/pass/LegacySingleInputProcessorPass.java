package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacySingleInputProcessorAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacySingleInputProcessorEnergyIngressAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacySingleInputProcessorRecipeAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacySingleInputProcessorRecipeMaterializer;
import dev.yinghuang.legacyforgebridge.convert.LegacySingleInputProcessorRuntimeAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Emits source-proven topology, recipes and bounded runtime eligibility for three-slot processors. */
public final class LegacySingleInputProcessorPass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/single-input-processor-rules.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override public String id() { return "legacy-single-input-processors"; }

    @Override
    public void apply(ConversionContext context) throws Exception {
        var analysis = new LegacySingleInputProcessorAnalyzer().analyze(context.sourceJar());
        if (analysis.rules().isEmpty() && analysis.skipped().isEmpty()) return;

        Map<String,String> blockIds = generatedBlockIds(context.stagingDir());
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 4);
        root.addProperty("sourceSha256", context.sourceHash());
        JsonArray machines = new JsonArray();
        int emittedRecipes = 0;
        int skippedRecipes = 0;
        int runtimeProofs = 0;
        int energyIngressProofs = 0;
        int baseRuntimeMachines = 0;
        LegacySingleInputProcessorRuntimeAnalyzer runtimeAnalyzer = new LegacySingleInputProcessorRuntimeAnalyzer();
        LegacySingleInputProcessorEnergyIngressAnalyzer ingressAnalyzer = new LegacySingleInputProcessorEnergyIngressAnalyzer();

        for (var machine : analysis.rules()) {
            String id = blockIds.get(machine.sourceBlockClass());
            if (id == null) {
                context.diagnostics().warning(
                        "LFB-CONVERT-PROCESSOR-0003", SupportLevel.MANUAL_REQUIRED,
                        "Source-proven processor has no generated block identity: " + machine.sourceBlockClass() + ".");
                continue;
            }

            var runtime = runtimeAnalyzer.analyze(context.sourceJar(), machine);
            if (runtime.complete()) runtimeProofs++;
            var ingress = ingressAnalyzer.analyze(context.sourceJar(), machine, runtime);
            if (ingress.complete()) energyIngressProofs++;
            var recipes = new LegacySingleInputProcessorRecipeAnalyzer().analyze(context.sourceJar(), machine);
            JsonArray recipeJson = new JsonArray();
            for (var recipe : recipes.recipes()) {
                var modern = LegacySingleInputProcessorRecipeMaterializer.materialize(recipe, context);
                if (modern.isEmpty()) {
                    skippedRecipes++;
                    context.diagnostics().warning(
                            "LFB-CONVERT-PROCESSOR-0004", SupportLevel.MANUAL_REQUIRED,
                            "Processor recipe could not be materialized without guessing: "
                                    + recipe.sourceOwner() + "." + recipe.sourceMethod() + ".");
                    continue;
                }
                recipeJson.add(modern.get());
                emittedRecipes++;
            }

            boolean allRecipesMaterialized = !recipes.recipes().isEmpty()
                    && recipeJson.size() == recipes.recipes().size();
            boolean baseRuntimeComplete = runtime.complete() && allRecipesMaterialized;
            boolean energyStorageRuntimeComplete = baseRuntimeComplete
                    && (!runtime.legacyEnergyApiPresent() || !runtime.energyNbtKey().isBlank());
            boolean energyIngressRuntimeComplete = baseRuntimeComplete && ingress.complete();
            boolean runtimeComplete = baseRuntimeComplete && energyStorageRuntimeComplete
                    && energyIngressRuntimeComplete;
            if (baseRuntimeComplete) baseRuntimeMachines++;

            JsonObject value = new JsonObject();
            value.addProperty("id", id);
            value.addProperty("sourceBlockClass", machine.sourceBlockClass());
            value.addProperty("sourceTileClass", machine.sourceTileClass());
            value.addProperty("legacyTileId", machine.legacyTileId());
            value.addProperty("slots", machine.slots());
            value.addProperty("stackLimit", machine.stackLimit());
            value.addProperty("inputSlot", machine.inputSlot());
            value.add("outputSlots", ints(machine.outputSlots()));
            value.add("topSlots", ints(machine.topSlots()));
            value.add("bottomSlots", ints(machine.bottomSlots()));
            value.add("sideSlots", ints(machine.sideSlots()));
            value.addProperty("processTicks", machine.processTicks());
            value.addProperty("interactionDistanceSq", machine.interactionDistanceSq());
            value.addProperty("legacyGuiId", machine.guiId());
            value.addProperty("recipeManagerOwner", machine.recipeManagerOwner());
            value.addProperty("recipeLookupName", machine.recipeLookupName());
            value.addProperty("recipeLookupDescriptor", machine.recipeLookupDescriptor());
            value.addProperty("comparator", machine.comparator());
            value.addProperty("dropContents", machine.dropContents());
            value.addProperty("sidedExtractionProven", runtime.sidedExtractionProven());
            value.addProperty("legacyEnergyApiPresent", runtime.legacyEnergyApiPresent());
            value.addProperty("minUseEnergy", runtime.minUseEnergy());
            value.addProperty("maxUseEnergy", runtime.maxUseEnergy());
            value.addProperty("energyNbtKey", runtime.energyNbtKey());
            value.addProperty("energyAccelerationProven", runtime.energyAccelerationProven());
            value.addProperty("runtimeProofComplete", runtime.complete());
            JsonArray runtimeDiagnostics = new JsonArray();
            runtime.diagnostics().forEach(runtimeDiagnostics::add);
            value.add("runtimeDiagnostics", runtimeDiagnostics);

            value.addProperty("energyIngressProofComplete", ingress.complete());
            value.addProperty("energyAllSidesConnectProven", ingress.allSidesConnect());
            value.addProperty("energyExtractionDisabledProven", ingress.extractionDisabled());
            value.addProperty("energyQueryZeroSemanticsProven", ingress.queryMethodsReturnZero());
            value.addProperty("energyReceiveSimulationProven", ingress.receiveSimulationProven());
            JsonArray ingressDiagnostics = new JsonArray();
            ingress.diagnostics().forEach(ingressDiagnostics::add);
            value.add("energyIngressDiagnostics", ingressDiagnostics);

            value.add("recipes", recipeJson);
            value.addProperty("sourceRecipeCount", recipes.recipes().size());
            value.addProperty("materializedRecipeCount", recipeJson.size());
            JsonArray recipeDiagnostics = new JsonArray();
            recipes.diagnostics().forEach(recipeDiagnostics::add);
            value.add("recipeDiagnostics", recipeDiagnostics);

            value.addProperty("baseRuntimeComplete", baseRuntimeComplete);
            value.addProperty("tickingRuntimeComplete", baseRuntimeComplete);
            value.addProperty("menuRuntimeComplete", baseRuntimeComplete);
            value.addProperty("sidedTransferRuntimeComplete", baseRuntimeComplete);
            value.addProperty("progressNbtRuntimeComplete", baseRuntimeComplete);
            value.addProperty("energyStorageRuntimeComplete", energyStorageRuntimeComplete);
            value.addProperty("energyIngressRuntimeComplete", energyIngressRuntimeComplete);
            value.addProperty("genericScreenRuntimeComplete", baseRuntimeComplete);
            value.addProperty("sourcePresentationComplete", false);
            value.addProperty("runtimeComplete", runtimeComplete);
            machines.add(value);

            if (baseRuntimeComplete && runtime.legacyEnergyApiPresent() && ingress.complete()) {
                context.diagnostics().info(
                        "LFB-CONVERT-PROCESSOR-0006", SupportLevel.ADAPTED,
                        "Processor source energy receiver contract is proven for " + id
                                + "; modern ingress is exposed through the transaction-safe Team Reborn Energy API with 1:1 source units.");
            } else if (baseRuntimeComplete && runtime.legacyEnergyApiPresent()) {
                context.diagnostics().warning(
                        "LFB-CONVERT-PROCESSOR-0006", SupportLevel.RUNTIME_BRIDGE,
                        "Processor base runtime is available for " + id
                                + ", but the external legacy energy receiver contract is not proven safe for modern ingress.");
            }
            if (baseRuntimeComplete) {
                context.diagnostics().warning(
                        "LFB-CONVERT-PROCESSOR-0007", SupportLevel.RUNTIME_BRIDGE,
                        "Processor " + id
                                + " has functional gameplay runtime; source presentation completion is evaluated by the presentation pass.");
            }
        }

        root.add("machines", machines);
        JsonArray skipped = new JsonArray();
        for (var candidate : analysis.skipped()) {
            JsonObject value = new JsonObject();
            value.addProperty("registryName", candidate.registryName());
            value.addProperty("sourceBlockClass", candidate.sourceBlockClass());
            value.addProperty("reason", candidate.reason());
            skipped.add(value);
        }
        root.add("skippedMachines", skipped);
        root.addProperty("runtimeProofCompleteMachines", runtimeProofs);
        root.addProperty("energyIngressProofCompleteMachines", energyIngressProofs);
        root.addProperty("baseRuntimeCompleteMachines", baseRuntimeMachines);
        root.addProperty("materializedRecipes", emittedRecipes);
        root.addProperty("skippedRecipes", skippedRecipes);

        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
        analysis.diagnostics().forEach(message -> context.diagnostics().warning(
                "LFB-CONVERT-PROCESSOR-0002", SupportLevel.MANUAL_REQUIRED, message));
        if (!machines.isEmpty()) {
            context.diagnostics().info(
                    "LFB-CONVERT-PROCESSOR-0001", SupportLevel.ADAPTED,
                    "Materialized source-proven single-input processors: machines=" + machines.size()
                            + ", runtimeProofs=" + runtimeProofs
                            + ", energyIngressProofs=" + energyIngressProofs
                            + ", baseRuntime=" + baseRuntimeMachines
                            + ", recipes=" + emittedRecipes
                            + ", skippedRecipes=" + skippedRecipes + ".");
        }
    }

    private static JsonArray ints(List<Integer> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    private static Map<String,String> generatedBlockIds(Path staging) throws Exception {
        Path path = staging.resolve("legacyforgebridge/converted-content.json");
        if (!Files.isRegularFile(path)) return Map.of();
        Map<String,String> result = new LinkedHashMap<>();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject content = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray blocks = content.getAsJsonArray("blocks");
            if (blocks != null) for (var element : blocks) {
                if (!element.isJsonObject()) continue;
                JsonObject block = element.getAsJsonObject();
                if (block.has("sourceClass") && block.has("id")) {
                    result.put(block.get("sourceClass").getAsString(), block.get("id").getAsString());
                }
            }
        }
        return result;
    }
}
