package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyGridPotBlockAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Materializes the proof-complete non-render-type portion of a legacy square grid-pot BlockEntity. */
public final class LegacyGridPotBlockPass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/grid-pot-block-rules.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public String id() {
        return "legacy-grid-pot-blocks";
    }

    @Override
    public void apply(ConversionContext context) throws Exception {
        LegacyGridPotBlockAnalyzer.Analysis analysis = new LegacyGridPotBlockAnalyzer().analyze(context.sourceJar());
        if (analysis.rules().isEmpty() && analysis.skipped().isEmpty()) return;
        Map<String, String> ids = generatedBlockIds(context.stagingDir());
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        JsonArray rules = new JsonArray();
        int coreRuntime = 0;
        int insertionBlocked = 0;
        int unmapped = 0;
        for (LegacyGridPotBlockAnalyzer.Rule rule : analysis.rules()) {
            String id = ids.get(rule.sourceBlockClass());
            if (id == null) {
                unmapped++;
                continue;
            }
            JsonObject value = new JsonObject();
            value.addProperty("id", id);
            value.addProperty("sourceBlockClass", rule.sourceBlockClass());
            value.addProperty("sourceItemBlockClass", rule.sourceItemBlockClass());
            value.addProperty("sourceTileClass", rule.sourceTileClass());
            value.addProperty("legacyTileId", rule.legacyTileId());
            value.addProperty("cells", rule.cells());
            value.addProperty("gridWidth", rule.gridWidth());
            value.addProperty("baseHeight", rule.baseHeight());
            value.addProperty("cellHeight", rule.cellHeight());
            value.addProperty("placementCreatesCell", rule.placementCreatesCell());
            value.addProperty("emptyHandRemovalProven", rule.emptyHandRemovalProven());
            value.addProperty("selfItemAddsCellProven", rule.selfItemAddsCellProven());
            value.addProperty("breakDropsEveryEnabledCell", rule.breakDropsEveryEnabledCell());
            value.addProperty("normalBlockDropDisabled", rule.normalBlockDropDisabled());
            value.addProperty("persistenceProven", rule.persistenceProven());
            value.addProperty("dynamicCellShapeProven", rule.dynamicCellShapeProven());
            value.addProperty("nonOpaqueProven", rule.nonOpaqueProven());
            value.addProperty("legacyInsertionPredicateProven", rule.contentInsertionPredicateProven());
            boolean core = rule.cells() == 9 && rule.gridWidth() == 3
                    && rule.placementCreatesCell() && rule.emptyHandRemovalProven()
                    && rule.selfItemAddsCellProven() && rule.breakDropsEveryEnabledCell()
                    && rule.normalBlockDropDisabled() && rule.persistenceProven()
                    && rule.dynamicCellShapeProven() && rule.nonOpaqueProven();
            value.addProperty("coreRuntimeComplete", core);
            value.addProperty("contentInsertionRuntimeComplete", false);
            value.addProperty("presentationRuntimeComplete", false);
            value.addProperty("runtimeComplete", false);
            if (core) coreRuntime++;
            if (rule.contentInsertionPredicateProven()) insertionBlocked++;
            rules.add(value);
        }
        root.add("rules", rules);
        JsonArray skipped = new JsonArray();
        for (LegacyGridPotBlockAnalyzer.Skipped item : analysis.skipped()) {
            JsonObject value = new JsonObject();
            value.addProperty("registryName", item.registryName());
            value.addProperty("sourceBlockClass", item.sourceBlockClass());
            value.addProperty("reason", item.reason());
            skipped.add(value);
        }
        root.add("skipped", skipped);
        root.addProperty("coreRuntimeCompleteRules", coreRuntime);
        root.addProperty("legacyInsertionPredicateProvenButRuntimeClosedRules", insertionBlocked);
        root.addProperty("runtimeCompleteRules", 0);
        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
        analysis.diagnostics().forEach(message -> context.diagnostics().warning(
                "LFB-CONVERT-GRIDPOT-0002", SupportLevel.MANUAL_REQUIRED, message));
        if (unmapped > 0) context.diagnostics().warning(
                "LFB-CONVERT-GRIDPOT-0003", SupportLevel.MANUAL_REQUIRED,
                "Source-proven grid-pot blocks without generated block identity: " + unmapped + ".");
        if (coreRuntime > 0) context.diagnostics().info(
                "LFB-CONVERT-GRIDPOT-0001", SupportLevel.ADAPTED,
                "Proof-complete grid-pot core runtimes: " + coreRuntime
                        + "; legacy content-insertion render predicate remains fail-closed.");
    }

    private static Map<String, String> generatedBlockIds(Path staging) throws Exception {
        Path path = staging.resolve("legacyforgebridge/converted-content.json");
        if (!Files.isRegularFile(path)) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
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
