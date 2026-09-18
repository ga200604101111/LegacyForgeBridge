package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacySingleInputProcessorTileConstructionAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Materializes bounded source TileEntity constructor replacement proof for complete processors. */
public final class LegacySingleInputProcessorTileConstructionPass
        implements ConversionPass {
    public static final String OUTPUT =
            "legacyforgebridge/single-input-processor-tile-construction-replacement.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public String id() {
        return "legacy-single-input-processor-tile-construction-replacement";
    }

    @Override
    public void apply(ConversionContext context) throws Exception {
        Path processorPath =
                context.stagingDir().resolve(LegacySingleInputProcessorPass.OUTPUT);
        if (!Files.isRegularFile(processorPath)) return;

        JsonObject processor = JsonParser.parseString(
                Files.readString(processorPath, StandardCharsets.UTF_8)).getAsJsonObject();
        if (integer(processor, "schemaVersion", -1) != 4
                || !context.sourceHash().equals(string(processor, "sourceSha256", ""))) {
            return;
        }

        LegacySingleInputProcessorTileConstructionAnalyzer.Analysis analysis =
                new LegacySingleInputProcessorTileConstructionAnalyzer()
                        .analyze(context.sourceJar());
        Map<String, LegacySingleInputProcessorTileConstructionAnalyzer.Proof> proofs =
                new LinkedHashMap<>();
        for (var proof : analysis.proofs()) {
            proofs.putIfAbsent(proof.sourceTileClass(), proof);
        }

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("tileConstructionReplacementAnalysisWired", true);
        root.addProperty("modernProcessorBlockEntityConstructorWired", true);
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
            String sourceTileClass = string(machine, "sourceTileClass", null);
            if (sourceTileClass == null) continue;

            var proof = proofs.get(sourceTileClass);
            JsonObject value = new JsonObject();
            if (id != null) value.addProperty("id", id);
            value.addProperty("sourceTileClass", sourceTileClass);
            value.addProperty("runtimeComplete", true);

            JsonArray blockers = new JsonArray();
            if (proof == null) {
                blockers.add("source-tile-construction-proof-missing");
                value.addProperty("constructorPresent", false);
                value.addProperty("constructorChainComplete", false);
                value.addProperty("constructorControlFlowSimple", false);
                value.addProperty("inventoryArrayInitializationProven", false);
                value.addProperty("defaultFieldWritesOnly", false);
                value.addProperty("noAdditionalMethodCalls", false);
                value.addProperty("tileConstructorReplacementProven", false);
                blocked++;
            } else {
                value.addProperty("expectedSlots", proof.expectedSlots());
                value.addProperty("constructorPresent", proof.constructorPresent());
                value.addProperty("constructorChainComplete",
                        proof.constructorChainComplete());
                value.addProperty("constructorControlFlowSimple",
                        proof.constructorControlFlowSimple());
                value.addProperty("inventoryArrayInitializationProven",
                        proof.inventoryArrayInitializationProven());
                value.addProperty("defaultFieldWritesOnly",
                        proof.defaultFieldWritesOnly());
                value.addProperty("noAdditionalMethodCalls",
                        proof.noAdditionalMethodCalls());
                value.addProperty("modernSlotInitializationWired", true);
                value.addProperty("modernDefaultFieldInitializationWired", true);
                value.addProperty("tileConstructorReplacementProven",
                        proof.replacementProofComplete());

                JsonArray chain = new JsonArray();
                proof.constructorChain().forEach(chain::add);
                value.add("constructorChain", chain);

                JsonArray initializations = new JsonArray();
                for (var initialization : proof.fieldInitializations()) {
                    JsonObject init = new JsonObject();
                    init.addProperty("owner", initialization.owner());
                    init.addProperty("name", initialization.name());
                    init.addProperty("descriptor", initialization.descriptor());
                    init.addProperty("kind", initialization.kind());
                    if (initialization.arrayLength() != null) {
                        init.addProperty("arrayLength", initialization.arrayLength());
                    }
                    initializations.add(init);
                }
                value.add("fieldInitializations", initializations);
                proof.blockers().forEach(blockers::add);

                if (proof.replacementProofComplete()) proven++;
                else blocked++;
            }

            value.add("blockers", blockers);
            rules.add(value);
        }

        root.add("rules", rules);
        root.addProperty("evaluatedRuntimeRules", rules.size());
        root.addProperty("tileConstructorReplacementProvenRules", proven);
        root.addProperty("tileConstructorReplacementBlockedRules", blocked);

        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        if (proven > 0) {
            context.diagnostics().info(
                    "LFB-CONVERT-PROCESSOR-TILECONSTRUCT-0001",
                    SupportLevel.ADAPTED,
                    "Proved source TileEntity constructor replacement for "
                            + proven + " runtime-complete processor rule(s).");
        }
        if (blocked > 0) {
            context.diagnostics().warning(
                    "LFB-CONVERT-PROCESSOR-TILECONSTRUCT-0002",
                    SupportLevel.RUNTIME_BRIDGE,
                    "Kept TileEntity constructor retirement blocked for "
                            + blocked + " processor rule(s); inspect constructor effects.");
        }
        for (String diagnostic : analysis.diagnostics()) {
            context.diagnostics().warning(
                    "LFB-CONVERT-PROCESSOR-TILECONSTRUCT-0003",
                    SupportLevel.MANUAL_REQUIRED,
                    diagnostic);
        }
    }

    private static JsonArray array(JsonObject root, String name) {
        JsonElement value = root == null ? null : root.get(name);
        return value != null && value.isJsonArray()
                ? value.getAsJsonArray() : new JsonArray();
    }

    private static boolean bool(JsonObject root, String name, boolean fallback) {
        JsonElement value = root == null ? null : root.get(name);
        return value != null && value.isJsonPrimitive()
                ? value.getAsBoolean() : fallback;
    }

    private static int integer(JsonObject root, String name, int fallback) {
        JsonElement value = root == null ? null : root.get(name);
        return value != null && value.isJsonPrimitive()
                ? value.getAsInt() : fallback;
    }

    private static String string(JsonObject root, String name, String fallback) {
        JsonElement value = root == null ? null : root.get(name);
        return value != null && value.isJsonPrimitive()
                ? value.getAsString() : fallback;
    }
}
