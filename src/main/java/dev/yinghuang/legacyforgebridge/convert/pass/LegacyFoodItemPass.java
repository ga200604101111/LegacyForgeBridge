package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyFoodItemAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Materializes only source-proven vanilla ItemFood semantics into a modern runtime sidecar. */
public final class LegacyFoodItemPass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/food-item-rules.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override public String id() { return "legacy-food-items"; }

    @Override
    public void apply(ConversionContext context) throws Exception {
        var analysis = new LegacyFoodItemAnalyzer().analyze(context.sourceJar());
        if (analysis.rules().isEmpty() && analysis.skipped().isEmpty()) return;

        Map<String,String> generatedIds = generatedItemIds(context.stagingDir());
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        JsonArray rules = new JsonArray();
        int unmapped = 0;
        for (var rule : analysis.rules()) {
            String id = generatedIds.get(key(rule.registryName(), rule.sourceClass()));
            if (id == null) { unmapped++; continue; }
            JsonObject value = new JsonObject();
            value.addProperty("id", id);
            value.addProperty("legacyRegistryName", rule.registryName());
            value.addProperty("sourceClass", rule.sourceClass());
            value.addProperty("nutrition", rule.nutrition());
            value.addProperty("saturationModifier", rule.saturationModifier());
            value.addProperty("alwaysEdible", rule.alwaysEdible());
            value.addProperty("runtimeComplete", true);
            rules.add(value);
        }
        root.add("rules", rules);

        JsonArray skipped = new JsonArray();
        for (var item : analysis.skipped()) {
            JsonObject value = new JsonObject();
            value.addProperty("legacyRegistryName", item.registryName());
            value.addProperty("sourceClass", item.sourceClass());
            value.addProperty("reason", item.reason());
            skipped.add(value);
            context.diagnostics().warning("LFB-CONVERT-FOOD-0002", SupportLevel.MANUAL_REQUIRED,
                    "Skipped legacy ItemFood " + item.registryName() + " (" + item.sourceClass() + "): " + item.reason());
        }
        root.add("skipped", skipped);
        root.addProperty("runtimeCompleteRules", rules.size());
        root.addProperty("skippedRules", skipped.size());

        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
        analysis.diagnostics().forEach(message -> context.diagnostics().warning(
                "LFB-CONVERT-FOOD-0003", SupportLevel.MANUAL_REQUIRED, message));
        if (unmapped > 0) context.diagnostics().warning("LFB-CONVERT-FOOD-0004", SupportLevel.MANUAL_REQUIRED,
                "Source-proven ItemFood rules without generated item identity: " + unmapped + ".");
        if (!rules.isEmpty()) context.diagnostics().info("LFB-CONVERT-FOOD-0001", SupportLevel.ADAPTED,
                "Materialized source-proven legacy ItemFood semantics: " + rules.size() + ".");
    }

    private static Map<String,String> generatedItemIds(Path staging) throws Exception {
        Path path = staging.resolve("legacyforgebridge/converted-content.json");
        if (!Files.isRegularFile(path)) return Map.of();
        Map<String,String> result = new LinkedHashMap<>();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject content = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray items = content.getAsJsonArray("items");
            if (items != null) for (var element : items) {
                if (!element.isJsonObject()) continue;
                JsonObject item = element.getAsJsonObject();
                if (item.has("legacyRegistryName") && item.has("sourceClass") && item.has("id")) {
                    result.put(key(item.get("legacyRegistryName").getAsString(), item.get("sourceClass").getAsString()),
                            item.get("id").getAsString());
                }
            }
        }
        return result;
    }

    private static String key(String registryName, String sourceClass) {
        return registryName + "\u0000" + sourceClass;
    }
}
