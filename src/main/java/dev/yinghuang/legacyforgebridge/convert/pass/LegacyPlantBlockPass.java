package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyPlantBlockAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Emits plant-family/source-override proof; no growth, survival or seed runtime is enabled here. */
public final class LegacyPlantBlockPass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/plant-block-families.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override public String id() { return "legacy-plant-block-families"; }

    @Override
    public void apply(ConversionContext context) throws Exception {
        var analysis = new LegacyPlantBlockAnalyzer().analyze(context.sourceJar());
        if (analysis.rules().isEmpty()) return;
        Map<String,String> modernIds = modernBlockIds(context.stagingDir());
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        JsonArray rules = new JsonArray();
        int inheritedOnly = 0;
        int identityComplete = 0;
        for (var rule : analysis.rules()) {
            JsonObject value = new JsonObject();
            value.addProperty("legacyRegistryName", rule.registryName());
            if (rule.legacyNamespace() != null) value.addProperty("legacyNamespace", rule.legacyNamespace());
            value.addProperty("sourceClass", rule.sourceClass());
            value.addProperty("family", rule.family().name().toLowerCase());
            value.addProperty("inheritedVanillaLifecycleOnly", rule.inheritedVanillaLifecycleOnly());
            JsonArray overrides = new JsonArray();
            rule.sourceOverrides().forEach(overrides::add);
            value.add("sourceOverrides", overrides);
            String modern = modernIds.get(key(rule.registryName(), rule.sourceClass()));
            if (modern != null) { value.addProperty("modernId", modern); identityComplete++; }
            value.addProperty("modernIdentityComplete", modern != null);
            value.addProperty("runtimeComplete", false);
            if (rule.inheritedVanillaLifecycleOnly()) inheritedOnly++;
            rules.add(value);
        }
        root.add("rules", rules);
        root.addProperty("classifiedBlocks", rules.size());
        root.addProperty("inheritedVanillaLifecycleOnlyBlocks", inheritedOnly);
        root.addProperty("modernIdentityCompleteBlocks", identityComplete);
        root.addProperty("runtimeCompleteBlocks", 0);
        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
        context.diagnostics().info("LFB-CONVERT-PLANT-0001", SupportLevel.RUNTIME_BRIDGE,
                "Classified legacy plant block families: " + rules.size()
                        + "; inherited-vanilla lifecycle only=" + inheritedOnly
                        + "; runtime remains gated pending growth/survival/presentation proof.");
        analysis.diagnostics().forEach(message -> context.diagnostics().warning(
                "LFB-CONVERT-PLANT-0002", SupportLevel.MANUAL_REQUIRED, message));
    }

    private static Map<String,String> modernBlockIds(Path staging) throws Exception {
        Path path = staging.resolve("legacyforgebridge/converted-content.json");
        if (!Files.isRegularFile(path)) return Map.of();
        Map<String,String> result = new LinkedHashMap<>();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject content = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray blocks = content.getAsJsonArray("blocks");
            if (blocks != null) for (var element : blocks) if (element.isJsonObject()) {
                JsonObject block = element.getAsJsonObject();
                if (block.has("legacyRegistryName") && block.has("sourceClass") && block.has("id")) {
                    result.put(key(block.get("legacyRegistryName").getAsString(), block.get("sourceClass").getAsString()),
                            block.get("id").getAsString());
                }
            }
        }
        return result;
    }

    private static String key(String registryName, String sourceClass) {
        return registryName + "\u0000" + sourceClass;
    }
}
