package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Separates item placement soil proof from Forge 1.7 plant-survival extensibility.
 *
 * <p>A source-proven ItemSeeds/ItemSeedFood constructor can prove the block the player must click to
 * plant. It does not prove the complete later canSustainPlant domain because Forge allows soil
 * blocks to extend that domain. This pass records that distinction explicitly and therefore never
 * marks gameplay runtime complete by itself.</p>
 */
public final class LegacyPlantSoilProofPass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/plant-soil-proof.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override public String id() { return "legacy-plant-soil-proof"; }

    @Override
    public void apply(ConversionContext context) throws Exception {
        JsonObject runtime = read(context.stagingDir().resolve(LegacyPlantRuntimeProofPass.OUTPUT));
        JsonObject bindings = read(context.stagingDir().resolve(LegacyItemBlockBindingPass.OUTPUT));
        if (runtime == null && bindings == null) return;
        boolean sourceAligned = hashMatches(runtime, context.sourceHash()) && hashMatches(bindings, context.sourceHash());

        JsonArray runtimeProofs = runtime == null ? null : runtime.getAsJsonArray("proofs");
        JsonArray itemBindings = bindings == null ? null : bindings.getAsJsonArray("bindings");
        JsonArray proofs = new JsonArray();
        int targetComplete = 0, cropPlacementComplete = 0;

        if (runtimeProofs != null) for (JsonElement element : runtimeProofs) {
            if (!element.isJsonObject()) continue;
            JsonObject plant = element.getAsJsonObject();
            String modernId = string(plant, "modernId");
            String family = string(plant, "family");
            JsonArray matchingItems = new JsonArray();
            Set<String> soilIds = new LinkedHashSet<>();
            int matchingBindings = 0;

            if (modernId != null && itemBindings != null) for (JsonElement bindingElement : itemBindings) {
                if (!bindingElement.isJsonObject()) continue;
                JsonObject binding = bindingElement.getAsJsonObject();
                JsonObject target = object(binding, "targetBlock");
                if (target == null || !modernId.equals(string(target, "modernId"))) continue;
                String itemFamily = string(binding, "family");
                if (!matchesFamily(family, itemFamily)) continue;
                matchingBindings++;
                String itemId = string(binding, "id");
                if (itemId != null) matchingItems.add(itemId);
                JsonObject soil = object(binding, "soilBlock");
                String soilId = string(soil, "modernId");
                if (soilId != null) soilIds.add(soilId);
            }

            boolean placementTarget = sourceAligned && bool(plant, "runtimeProofComplete") && matchingBindings > 0;
            boolean cropSoilIdentity = "crops".equals(family) && soilIds.size() == 1;
            String cropSoilId = cropSoilIdentity ? soilIds.iterator().next() : null;
            boolean cropVanillaPlacement = placementTarget && cropSoilIdentity && "minecraft:farmland".equals(cropSoilId);

            JsonObject value = new JsonObject();
            copy(plant, value, "legacyRegistryName");
            copy(plant, value, "sourceClass");
            copy(plant, value, "family");
            copy(plant, value, "modernId");
            value.addProperty("plantRuntimeProofComplete", bool(plant, "runtimeProofComplete"));
            value.add("matchingPlacementItems", matchingItems);
            value.addProperty("placementTargetProofComplete", placementTarget);
            if (cropSoilId != null) value.addProperty("placementSoilId", cropSoilId);
            value.addProperty("placementSoilIdentityComplete", "crops".equals(family) && cropSoilIdentity);
            value.addProperty("vanillaPlacementSoilSemanticsComplete", "crops".equals(family) && cropVanillaPlacement);
            value.addProperty("forgeCanSustainPlantExtensibilityRequired", "crops".equals(family) || "bush".equals(family) || "reed".equals(family));
            value.addProperty("forgeCanSustainPlantExtensibilityComplete", false);
            value.addProperty("survivalSoilProofComplete", false);
            value.addProperty("runtimeComplete", false);
            JsonArray reasons = new JsonArray();
            if (!sourceAligned) reasons.add("source-proof-hash-mismatch");
            if (!bool(plant, "runtimeProofComplete")) reasons.add("plant-runtime-proof-incomplete");
            if (matchingBindings == 0) reasons.add("placement-item-binding-missing");
            if ("crops".equals(family) && !cropSoilIdentity) reasons.add("crop-placement-soil-identity-incomplete-or-ambiguous");
            if ("crops".equals(family) && cropSoilIdentity && !"minecraft:farmland".equals(cropSoilId)) reasons.add("crop-placement-soil-not-vanilla-farmland");
            reasons.add("forge-can-sustain-plant-extensibility-pending");
            value.add("reasons", reasons);
            proofs.add(value);

            if (placementTarget) targetComplete++;
            if (cropVanillaPlacement) cropPlacementComplete++;
        }

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("sourceProofsAligned", sourceAligned);
        root.add("proofs", proofs);
        root.addProperty("classifiedBlocks", proofs.size());
        root.addProperty("placementTargetProofCompleteBlocks", targetComplete);
        root.addProperty("cropVanillaPlacementSoilCompleteBlocks", cropPlacementComplete);
        root.addProperty("survivalSoilProofCompleteBlocks", 0);
        root.addProperty("runtimeCompleteBlocks", 0);
        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
        context.diagnostics().info("LFB-CONVERT-PLANT-SOIL-0001", SupportLevel.RUNTIME_BRIDGE,
                "Separated plant placement soil from Forge survival soil: blocks=" + proofs.size()
                        + ", placement-target=" + targetComplete
                        + ", vanilla-farmland-placement=" + cropPlacementComplete
                        + "; canSustainPlant extensibility remains gated.");
    }

    private static boolean matchesFamily(String plantFamily, String itemFamily) {
        if ("crops".equals(plantFamily)) return "seeds".equals(itemFamily) || "seed_food".equals(itemFamily);
        if ("reed".equals(plantFamily)) return "reed".equals(itemFamily);
        return false;
    }

    private static JsonObject read(Path path) throws Exception {
        if (!Files.isRegularFile(path)) return null;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        }
    }

    private static boolean hashMatches(JsonObject root, String hash) {
        return root != null && hash.equals(string(root, "sourceSha256"));
    }

    private static boolean bool(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() && value.getAsBoolean();
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
    }

    private static JsonObject object(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static void copy(JsonObject from, JsonObject to, String key) {
        JsonElement value = from.get(key);
        if (value != null) to.add(key, value.deepCopy());
    }
}
