package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyPlantSoilExtensionAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Emits source-owned Forge canSustainPlant/isFertile extension inventory; no runtime is installed. */
public final class LegacyPlantSoilExtensionPass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/plant-soil-extension-proof.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override public String id() { return "legacy-plant-soil-extension-proof"; }

    @Override
    public void apply(ConversionContext context) throws Exception {
        var analysis = new LegacyPlantSoilExtensionAnalyzer().analyze(context.sourceJar());
        if (analysis.rules().isEmpty()) return;
        Map<String,String> modernIds = modernBlockIds(context.stagingDir());
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        JsonArray rules = new JsonArray();
        int identity = 0, defaultSustain = 0, defaultFertility = 0, sustainOverrides = 0, fertilityOverrides = 0;
        for (var rule : analysis.rules()) {
            JsonObject value = new JsonObject();
            value.addProperty("legacyRegistryName", rule.registryName());
            if (rule.sourceClass() != null) value.addProperty("sourceClass", rule.sourceClass());
            String modern = modernIds.get(key(rule.registryName(), rule.sourceClass()));
            if (modern != null) { value.addProperty("modernId", modern); identity++; }
            value.addProperty("modernIdentityComplete", modern != null);
            value.addProperty("forgeDefaultSustainInherited", rule.inheritsForgeDefaultSustain());
            value.addProperty("forgeDefaultFertilityInherited", rule.inheritsForgeDefaultFertility());
            value.add("canSustainPlantHooks", strings(rule.canSustainPlantHooks()));
            value.add("fertilityHooks", strings(rule.fertilityHooks()));
            value.addProperty("runtimeComplete", false);
            if (rule.inheritsForgeDefaultSustain()) defaultSustain++; else sustainOverrides += rule.canSustainPlantHooks().size();
            if (rule.inheritsForgeDefaultFertility()) defaultFertility++; else fertilityOverrides += rule.fertilityHooks().size();
            rules.add(value);
        }
        root.add("rules", rules);
        root.addProperty("registeredBlocks", rules.size());
        root.addProperty("modernIdentityCompleteBlocks", identity);
        root.addProperty("forgeDefaultSustainInheritedBlocks", defaultSustain);
        root.addProperty("forgeDefaultFertilityInheritedBlocks", defaultFertility);
        root.addProperty("sourceCanSustainPlantOverrides", sustainOverrides);
        root.addProperty("sourceFertilityOverrides", fertilityOverrides);
        root.addProperty("runtimeCompleteBlocks", 0);
        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
        context.diagnostics().info("LFB-CONVERT-PLANT-SOIL-EXT-0001", SupportLevel.RUNTIME_BRIDGE,
                "Inventoried Forge 1.7 soil extension hooks: registered-blocks=" + rules.size()
                        + ", canSustainPlant-overrides=" + sustainOverrides
                        + ", isFertile-overrides=" + fertilityOverrides + ".");
        analysis.diagnostics().forEach(message -> context.diagnostics().warning(
                "LFB-CONVERT-PLANT-SOIL-EXT-0002", SupportLevel.MANUAL_REQUIRED, message));
    }

    private static JsonArray strings(java.util.List<String> values) {
        JsonArray result = new JsonArray();
        values.forEach(result::add);
        return result;
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
        return registryName + "\u0000" + String.valueOf(sourceClass);
    }
}
