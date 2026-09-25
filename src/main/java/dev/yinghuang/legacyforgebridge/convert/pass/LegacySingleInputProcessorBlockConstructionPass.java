package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacySingleInputProcessorBlockConstructionAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Proves and materializes constructor-local physical properties for runtime-complete processor
 * Blocks. Allocation-site fluent setters remain intentionally outside this pass.
 */
public final class LegacySingleInputProcessorBlockConstructionPass
        implements ConversionPass {
    public static final String OUTPUT =
            "legacyforgebridge/single-input-processor-block-construction-replacement.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public String id() {
        return "legacy-single-input-processor-block-construction-replacement";
    }

    @Override
    public void apply(ConversionContext context) throws Exception {
        Path processorPath =
                context.stagingDir().resolve(LegacySingleInputProcessorPass.OUTPUT);
        if (!Files.isRegularFile(processorPath)) return;

        JsonObject processor = JsonParser.parseString(
                Files.readString(processorPath, StandardCharsets.UTF_8)).getAsJsonObject();
        if (integer(processor, "schemaVersion", -1) != 4
                || !context.sourceHash().equals(
                        string(processor, "sourceSha256", ""))) {
            return;
        }

        LegacySingleInputProcessorBlockConstructionAnalyzer.Analysis analysis =
                new LegacySingleInputProcessorBlockConstructionAnalyzer()
                        .analyze(context.sourceJar());
        Map<String, LegacySingleInputProcessorBlockConstructionAnalyzer.Proof> proofs =
                new LinkedHashMap<>();
        for (var proof : analysis.proofs()) {
            proofs.putIfAbsent(proof.sourceBlockClass(), proof);
        }

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("blockConstructionReplacementAnalysisWired", true);
        root.addProperty("modernBlockPropertiesRuntimeWired", true);
        root.addProperty("allocationSiteEffectsIncluded", false);
        root.addProperty("sourceAllocationStripWired", false);
        root.addProperty("sourceClassDeletionWired", false);

        JsonArray rules = new JsonArray();
        int proven = 0;
        int blocked = 0;

        for (JsonElement element : array(processor, "machines")) {
            if (!element.isJsonObject()) continue;
            JsonObject machine = element.getAsJsonObject();
            if (!bool(machine, "runtimeComplete", false)
                    || !bool(machine, "baseRuntimeComplete", false)
                    || !bool(machine, "sourcePresentationComplete", false)) {
                continue;
            }

            String id = string(machine, "id", null);
            String sourceBlockClass =
                    string(machine, "sourceBlockClass", null);
            if (sourceBlockClass == null) continue;

            var proof = proofs.get(sourceBlockClass);
            JsonObject value = new JsonObject();
            if (id != null) value.addProperty("id", id);
            value.addProperty("sourceBlockClass", sourceBlockClass);
            value.addProperty("runtimeComplete", true);
            value.addProperty("allocationSiteEffectsIncluded", false);
            value.addProperty("sourceAllocationRetirementRequired", true);

            JsonArray blockers = new JsonArray();
            boolean replacement = proof != null
                    && proof.replacementProofComplete()
                    && proof.properties() != null;

            if (proof == null) {
                blockers.add("source-block-construction-proof-missing");
                value.addProperty("constructorPresent", false);
                value.addProperty("constructorChainComplete", false);
                value.addProperty("constructorControlFlowSimple", false);
                value.addProperty("materialSemanticsProven", false);
                value.addProperty("propertyEffectsSupported", false);
                value.addProperty("noAdditionalEffects", false);
            } else {
                value.addProperty("constructorPresent", proof.constructorPresent());
                value.addProperty(
                        "constructorChainComplete", proof.constructorChainComplete());
                value.addProperty(
                        "constructorControlFlowSimple",
                        proof.constructorControlFlowSimple());
                value.addProperty(
                        "materialSemanticsProven", proof.materialSemanticsProven());
                value.addProperty(
                        "propertyEffectsSupported", proof.propertyEffectsSupported());
                value.addProperty(
                        "noAdditionalEffects", proof.noAdditionalEffects());

                if (proof.materialOwner() != null) {
                    JsonObject material = new JsonObject();
                    material.addProperty("owner", proof.materialOwner());
                    material.addProperty("fieldName", proof.materialField());
                    material.addProperty(
                            "descriptor", proof.materialDescriptor());
                    value.add("material", material);
                }

                JsonArray chain = new JsonArray();
                proof.constructorChain().forEach(chain::add);
                value.add("constructorChain", chain);

                if (replacement) {
                    JsonObject properties = properties(proof.properties());
                    value.add("properties", properties);
                    machine.add("blockConstruction", properties.deepCopy());
                }
                proof.blockers().forEach(blockers::add);
            }

            machine.addProperty("blockConstructorReplacementProven", replacement);
            machine.addProperty("blockConstructionRuntimeWired", replacement);
            value.addProperty("blockConstructorReplacementProven", replacement);
            value.addProperty("modernBlockPropertiesRuntimeWired", replacement);
            value.addProperty("sourceAllocationStripWired", false);
            value.addProperty("sourceClassDeletionAuthorized", false);
            value.add("blockers", blockers);
            rules.add(value);

            if (replacement) proven++;
            else blocked++;
        }

        processor.addProperty("blockConstructionReplacementAnalysisWired", true);
        processor.addProperty("blockConstructionReplacementProvenMachines", proven);
        processor.addProperty("blockConstructionReplacementBlockedMachines", blocked);
        Files.writeString(
                processorPath,
                GSON.toJson(processor) + "\n",
                StandardCharsets.UTF_8);

        root.add("rules", rules);
        root.addProperty("evaluatedRuntimeRules", rules.size());
        root.addProperty("blockConstructorReplacementProvenRules", proven);
        root.addProperty("blockConstructorReplacementBlockedRules", blocked);

        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(
                output,
                GSON.toJson(root) + "\n",
                StandardCharsets.UTF_8);

        if (proven > 0) {
            context.diagnostics().info(
                    "LFB-CONVERT-PROCESSOR-BLOCKCONSTRUCT-0001",
                    SupportLevel.ADAPTED,
                    "Proved and materialized source Block constructor properties for "
                            + proven + " runtime-complete processor rule(s); allocation-site "
                            + "effects remain a separate retirement boundary.");
        }
        if (blocked > 0) {
            context.diagnostics().warning(
                    "LFB-CONVERT-PROCESSOR-BLOCKCONSTRUCT-0002",
                    SupportLevel.RUNTIME_BRIDGE,
                    "Kept source Block constructor retirement blocked for "
                            + blocked + " processor rule(s); inspect material, constructor "
                            + "control flow, property setters, or additional side effects.");
        }
        for (String diagnostic : analysis.diagnostics()) {
            context.diagnostics().warning(
                    "LFB-CONVERT-PROCESSOR-BLOCKCONSTRUCT-0003",
                    SupportLevel.MANUAL_REQUIRED,
                    diagnostic);
        }
    }

    private static JsonObject properties(
            LegacySingleInputProcessorBlockConstructionAnalyzer.Properties source) {
        JsonObject value = new JsonObject();
        value.addProperty("destroyTime", source.destroyTime());
        value.addProperty(
                "explosionResistance", source.explosionResistance());
        value.addProperty("soundType", source.soundType());
        value.addProperty("mapColor", source.mapColor());
        value.addProperty("lightLevel", source.lightLevel());
        return value;
    }

    private static JsonArray array(JsonObject root, String name) {
        JsonElement value = root == null ? null : root.get(name);
        return value != null && value.isJsonArray()
                ? value.getAsJsonArray() : new JsonArray();
    }

    private static boolean bool(
            JsonObject root, String name, boolean fallback) {
        JsonElement value = root == null ? null : root.get(name);
        return value != null && value.isJsonPrimitive()
                ? value.getAsBoolean() : fallback;
    }

    private static int integer(
            JsonObject root, String name, int fallback) {
        JsonElement value = root == null ? null : root.get(name);
        return value != null && value.isJsonPrimitive()
                ? value.getAsInt() : fallback;
    }

    private static String string(
            JsonObject root, String name, String fallback) {
        JsonElement value = root == null ? null : root.get(name);
        return value != null && value.isJsonPrimitive()
                ? value.getAsString() : fallback;
    }
}
