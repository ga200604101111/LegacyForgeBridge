package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacySeatBedAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Emits proof-only two-part bed/seat rules; gameplay/entity/time/presentation runtimes remain gated. */
public final class LegacySeatBedPass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/seat-bed-rules.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public String id() {
        return "legacy-seat-bed-proof";
    }

    @Override
    public void apply(ConversionContext context) throws Exception {
        LegacySeatBedAnalyzer.Analysis analysis = new LegacySeatBedAnalyzer().analyze(context.sourceJar());
        if (analysis.rules().isEmpty() && analysis.skipped().isEmpty()) return;

        Map<String, String> blockIds = generatedIds(context.stagingDir(), "blocks");
        Map<String, String> itemIds = generatedIds(context.stagingDir(), "items");
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        JsonArray rules = new JsonArray();
        int proven = 0;
        int timeAccel = 0;
        int unmapped = 0;

        for (LegacySeatBedAnalyzer.Rule rule : analysis.rules()) {
            String blockId = blockIds.get(rule.sourceBlockClass());
            String itemId = itemIds.get(rule.sourcePlacementItemClass());
            if (blockId == null || itemId == null) {
                unmapped++;
                continue;
            }
            JsonObject value = new JsonObject();
            value.addProperty("id", blockId);
            value.addProperty("placementItemId", itemId);
            value.addProperty("legacyRegistryName", rule.registryName());
            value.addProperty("legacyPlacementItemRegistryName", rule.placementItemRegistryName());
            value.addProperty("sourceBlockClass", rule.sourceBlockClass());
            value.addProperty("sourcePlacementItemClass", rule.sourcePlacementItemClass());
            value.addProperty("sourceTileClass", rule.sourceTileClass());
            value.addProperty("legacyTileId", rule.legacyTileId());
            value.addProperty("sourceSeatEntityClass", rule.sourceSeatEntityClass());
            value.addProperty("sourceSeatRuntimeClass", rule.sourceSeatRuntimeClass());
            value.addProperty("legacySeatEntityName", rule.legacySeatEntityName());
            value.addProperty("blockHeight", rule.blockHeight());
            value.addProperty("twoPartPlacementProven", rule.twoPartPlacementProven());
            value.addProperty("footOnlyTileProven", rule.footOnlyTileProven());
            value.addProperty("sleepFallbackToSeatProven", rule.sleepFallbackToSeatProven());
            value.addProperty("transientOccupancyProven", rule.transientOccupancyProven());
            value.addProperty("seatLifecycleProven", rule.seatLifecycleProven());
            value.addProperty("timeAccelerationSourceProven", rule.timeAccelerationSourceProven());
            value.addProperty("specialPresentationRequired", rule.specialPresentationRequired());
            value.addProperty("coreSourceProofComplete", rule.coreSourceProofComplete());
            value.addProperty("twoPartPlacementRuntimeComplete", false);
            value.addProperty("sleepSeatRuntimeComplete", false);
            value.addProperty("seatEntityRuntimeComplete", false);
            value.addProperty("timeAccelerationRuntimeComplete", false);
            value.addProperty("presentationRuntimeComplete", false);
            value.addProperty("runtimeComplete", false);
            if (rule.coreSourceProofComplete()) proven++;
            if (rule.timeAccelerationSourceProven()) timeAccel++;
            rules.add(value);
        }
        root.add("rules", rules);

        JsonArray skipped = new JsonArray();
        for (LegacySeatBedAnalyzer.Skipped item : analysis.skipped()) {
            JsonObject value = new JsonObject();
            value.addProperty("registryName", item.registryName());
            value.addProperty("sourceBlockClass", item.sourceBlockClass());
            value.addProperty("reason", item.reason());
            skipped.add(value);
        }
        root.add("skipped", skipped);
        root.addProperty("coreSourceProofCompleteRules", proven);
        root.addProperty("timeAccelerationSourceProvenRules", timeAccel);
        root.addProperty("runtimeCompleteRules", 0);

        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        analysis.diagnostics().forEach(message -> context.diagnostics().warning(
                "LFB-CONVERT-SEATBED-0002", SupportLevel.MANUAL_REQUIRED, message));
        if (unmapped > 0) context.diagnostics().warning(
                "LFB-CONVERT-SEATBED-0003", SupportLevel.MANUAL_REQUIRED,
                "Source-proven bed/seat families without generated block/item identity: " + unmapped + ".");
        if (proven > 0) context.diagnostics().info(
                "LFB-CONVERT-SEATBED-0001", SupportLevel.RUNTIME_BRIDGE,
                "Source-proven two-part bed/seat families: " + proven
                        + "; modern seat entity, time acceleration and special presentation remain runtime-gated.");
    }

    private static Map<String, String> generatedIds(Path staging, String key) throws Exception {
        Path path = staging.resolve("legacyforgebridge/converted-content.json");
        if (!Files.isRegularFile(path)) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject content = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray values = content.getAsJsonArray(key);
            if (values != null) for (var element : values) {
                if (!element.isJsonObject()) continue;
                JsonObject value = element.getAsJsonObject();
                if (value.has("sourceClass") && value.has("id")) {
                    result.put(value.get("sourceClass").getAsString(), value.get("id").getAsString());
                }
            }
        }
        return result;
    }
}
