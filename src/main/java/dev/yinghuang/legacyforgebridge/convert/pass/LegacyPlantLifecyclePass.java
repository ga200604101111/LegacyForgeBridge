package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyPlantLifecycleAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Emits independent plant lifecycle gates; runtime stays fail-closed. */
public final class LegacyPlantLifecyclePass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/plant-lifecycle-proof.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override public String id() { return "legacy-plant-lifecycle-proof"; }

    @Override
    public void apply(ConversionContext context) throws Exception {
        var analysis = new LegacyPlantLifecycleAnalyzer().analyze(context.sourceJar());
        if (analysis.proofs().isEmpty()) return;
        Map<String,String> modernIds = modernBlockIds(context.stagingDir());
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        JsonArray proofs = new JsonArray();
        int survival = 0, growth = 0, bonemeal = 0, drops = 0, age = 0;
        for (var proof : analysis.proofs()) {
            JsonObject value = new JsonObject();
            value.addProperty("legacyRegistryName", proof.registryName());
            value.addProperty("sourceClass", proof.sourceClass());
            value.addProperty("family", proof.family().name().toLowerCase());
            String modern = modernIds.get(key(proof.registryName(), proof.sourceClass()));
            if (modern != null) value.addProperty("modernId", modern);
            value.addProperty("modernIdentityComplete", modern != null);
            value.addProperty("survivalInheritedVanilla", proof.survivalInheritedVanilla());
            value.addProperty("growthInheritedVanilla", proof.growthInheritedVanilla());
            value.addProperty("bonemealInheritedVanilla", proof.bonemealInheritedVanilla());
            value.addProperty("dropsInheritedVanilla", proof.dropsInheritedVanilla());
            value.addProperty("ageModel", proof.ageModel().name().toLowerCase());
            value.addProperty("survivalModel", proof.survivalModel().name().toLowerCase());
            value.add("survivalHooks", strings(proof.survivalHooks()));
            value.add("growthHooks", strings(proof.growthHooks()));
            value.add("bonemealHooks", strings(proof.bonemealHooks()));
            value.add("dropHooks", strings(proof.dropHooks()));
            value.add("presentationHooks", strings(proof.presentationHooks()));
            value.add("constructorLifecycleMutations", strings(proof.constructorLifecycleMutations()));
            value.addProperty("runtimeComplete", false);
            if (proof.survivalInheritedVanilla()) survival++;
            if (proof.growthInheritedVanilla()) growth++;
            if (proof.bonemealInheritedVanilla()) bonemeal++;
            if (proof.dropsInheritedVanilla()) drops++;
            if (proof.ageModel() != LegacyPlantLifecycleAnalyzer.AgeModel.UNKNOWN) age++;
            proofs.add(value);
        }
        root.add("proofs", proofs);
        root.addProperty("classifiedBlocks", proofs.size());
        root.addProperty("survivalProofCompleteBlocks", survival);
        root.addProperty("growthProofCompleteBlocks", growth);
        root.addProperty("bonemealProofCompleteBlocks", bonemeal);
        root.addProperty("dropProofCompleteBlocks", drops);
        root.addProperty("ageModelProofCompleteBlocks", age);
        root.addProperty("runtimeCompleteBlocks", 0);
        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
        context.diagnostics().info("LFB-CONVERT-PLANT-LIFECYCLE-0001", SupportLevel.RUNTIME_BRIDGE,
                "Split legacy plant lifecycle proof: blocks=" + proofs.size()
                        + ", survival=" + survival + ", growth=" + growth
                        + ", bonemeal=" + bonemeal + ", drops=" + drops
                        + ", age-model=" + age + "; runtime remains gated.");
        analysis.diagnostics().forEach(message -> context.diagnostics().warning(
                "LFB-CONVERT-PLANT-LIFECYCLE-0002", SupportLevel.MANUAL_REQUIRED, message));
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
                    result.put(key(block.get("legacyRegistryName").getAsString(), block.get("sourceClass").getAsString()), block.get("id").getAsString());
                }
            }
        }
        return result;
    }

    private static String key(String registryName, String sourceClass) {
        return registryName + "\u0000" + sourceClass;
    }
}
