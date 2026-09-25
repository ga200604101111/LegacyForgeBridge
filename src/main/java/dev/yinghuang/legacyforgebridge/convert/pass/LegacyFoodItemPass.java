package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyFoodItemAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Materializes only source-proven vanilla ItemFood semantics into modern runtime/data resources. */
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
        root.addProperty("schemaVersion", 2);
        root.addProperty("sourceSha256", context.sourceHash());
        JsonArray rules = new JsonArray();
        Set<String> wolfFoods = new LinkedHashSet<>();
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
            value.addProperty("wolfFavorite", rule.wolfFavorite());
            value.addProperty("runtimeComplete", true);
            rules.add(value);
            if (rule.wolfFavorite()) wolfFoods.add(id);
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
        root.addProperty("wolfFoodRules", wolfFoods.size());
        root.addProperty("skippedRules", skipped.size());

        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
        if (!wolfFoods.isEmpty()) writeMergedWolfTag(context.stagingDir(), wolfFoods);
        analysis.diagnostics().forEach(message -> context.diagnostics().warning(
                "LFB-CONVERT-FOOD-0003", SupportLevel.MANUAL_REQUIRED, message));
        if (unmapped > 0) context.diagnostics().warning("LFB-CONVERT-FOOD-0004", SupportLevel.MANUAL_REQUIRED,
                "Source-proven ItemFood rules without generated item identity: " + unmapped + ".");
        if (!rules.isEmpty()) context.diagnostics().info("LFB-CONVERT-FOOD-0001", SupportLevel.ADAPTED,
                "Materialized source-proven legacy ItemFood semantics: " + rules.size()
                        + ", native wolf-food tags=" + wolfFoods.size() + ".");
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

    private static void writeMergedWolfTag(Path staging, Set<String> additions) throws Exception {
        Path target = staging.resolve("data/minecraft/tags/item/wolf_food.json");
        JsonObject output = new JsonObject();
        JsonArray values = new JsonArray();
        Set<String> known = new LinkedHashSet<>();
        if (Files.isRegularFile(target)) {
            try (Reader reader = Files.newBufferedReader(target, StandardCharsets.UTF_8)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (parsed.isJsonObject()) {
                    JsonObject existing = parsed.getAsJsonObject();
                    if (existing.has("replace") && existing.get("replace").isJsonPrimitive()) {
                        output.addProperty("replace", existing.get("replace").getAsBoolean());
                    }
                    JsonArray existingValues = existing.getAsJsonArray("values");
                    if (existingValues != null) for (JsonElement value : existingValues) {
                        values.add(value.deepCopy());
                        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) known.add(value.getAsString());
                    }
                }
            } catch (RuntimeException ignored) {
                output = new JsonObject(); values = new JsonArray(); known.clear();
            }
        }
        if (!output.has("replace")) output.addProperty("replace", false);
        for (String addition : additions) if (known.add(addition)) values.add(addition);
        output.add("values", values);
        Files.createDirectories(target.getParent());
        try (Writer writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            GSON.toJson(output, writer); writer.write('\n');
        }
    }

    private static String key(String registryName, String sourceClass) {
        return registryName + "\u0000" + sourceClass;
    }
}
