package dev.longyu.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.longyu.legacyforgebridge.convert.LegacyStorageBlockAnalyzer;
import dev.longyu.legacyforgebridge.convert.api.ConversionContext;
import dev.longyu.legacyforgebridge.convert.api.ConversionPass;
import dev.longyu.legacyforgebridge.convert.api.SupportLevel;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Materializes source-proven six-row legacy storage semantics as converted-mod-owned data. */
public final class LegacyStorageBlockPass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/storage-block-rules.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override public String id() { return "legacy-storage-blocks"; }

    @Override
    public void apply(ConversionContext context) throws Exception {
        Path contentPath = context.stagingDir().resolve("legacyforgebridge/converted-content.json");
        if (!Files.isRegularFile(contentPath)) return;

        Map<String, String> idsBySourceClass = new LinkedHashMap<>();
        try (Reader reader = Files.newBufferedReader(contentPath, StandardCharsets.UTF_8)) {
            JsonObject content = JsonParser.parseReader(reader).getAsJsonObject();
            for (var element : content.getAsJsonArray("blocks")) {
                JsonObject block = element.getAsJsonObject();
                if (block.has("sourceClass") && block.has("id")) {
                    idsBySourceClass.put(block.get("sourceClass").getAsString(), block.get("id").getAsString());
                }
            }
        }

        LegacyStorageBlockAnalyzer.Analysis analysis = new LegacyStorageBlockAnalyzer().analyze(context.sourceJar());
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        JsonArray rules = new JsonArray();
        int unmapped = 0;
        for (LegacyStorageBlockAnalyzer.Rule rule : analysis.rules()) {
            String id = idsBySourceClass.get(rule.sourceBlockClass());
            if (id == null) {
                unmapped++;
                continue;
            }
            JsonObject value = new JsonObject();
            value.addProperty("id", id);
            value.addProperty("sourceBlockClass", rule.sourceBlockClass());
            value.addProperty("sourceTileClass", rule.sourceTileClass());
            value.addProperty("legacyTileId", rule.legacyTileId());
            value.addProperty("slots", rule.slots());
            value.addProperty("rows", rule.rows());
            value.addProperty("stackLimit", rule.stackLimit());
            value.addProperty("title", rule.title());
            value.addProperty("interactionDistanceSq", rule.interactionDistanceSq());
            value.addProperty("sneakingPass", rule.sneakingPass());
            value.addProperty("dropContents", rule.dropContents());
            value.addProperty("comparator", rule.comparator());
            value.addProperty("presentationPending", true);
            rules.add(value);
        }
        root.add("rules", rules);

        JsonArray skipped = new JsonArray();
        for (LegacyStorageBlockAnalyzer.Skipped entry : analysis.skipped()) {
            JsonObject value = new JsonObject();
            value.addProperty("registryName", entry.registryName());
            value.addProperty("sourceBlockClass", entry.sourceBlockClass());
            value.addProperty("reason", entry.reason());
            skipped.add(value);
        }
        root.add("skipped", skipped);
        root.addProperty("unmappedRules", unmapped);

        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        for (String diagnostic : analysis.diagnostics()) {
            context.diagnostics().warning("LFB-CONVERT-STORAGE-0002", SupportLevel.MANUAL_REQUIRED, diagnostic);
        }
        if (unmapped > 0) {
            context.diagnostics().warning("LFB-CONVERT-STORAGE-0003", SupportLevel.MANUAL_REQUIRED,
                    "Source-proven storage rules could not be mapped to generated block identities: " + unmapped + ".");
        }
        if (!analysis.skipped().isEmpty()) {
            context.diagnostics().info("LFB-CONVERT-STORAGE-0004", SupportLevel.RUNTIME_BRIDGE,
                    "BlockContainer candidates outside the bounded six-row storage family remain fail-closed: "
                            + analysis.skipped().size() + ".");
        }
        if (!rules.isEmpty()) {
            context.diagnostics().info("LFB-CONVERT-STORAGE-0001", SupportLevel.ADAPTED,
                    "Materialized source-proven six-row BlockContainer/IInventory storage rules: " + rules.size()
                            + ". Inventory gameplay is modernized; source-specific lid/render presentation remains separately auditable.");
        }
    }
}
