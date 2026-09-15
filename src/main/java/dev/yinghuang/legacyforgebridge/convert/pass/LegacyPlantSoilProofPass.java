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
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Separates item placement soil, Forge survival soil and crop fertility proof.
 * Runtime remains disabled until a modern compatibility block consumes these exact rules.
 */
public final class LegacyPlantSoilProofPass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/plant-soil-proof.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override public String id() { return "legacy-plant-soil-proof"; }

    @Override
    public void apply(ConversionContext context) throws Exception {
        JsonObject runtime = read(context.stagingDir().resolve(LegacyPlantRuntimeProofPass.OUTPUT));
        JsonObject bindings = read(context.stagingDir().resolve(LegacyItemBlockBindingPass.OUTPUT));
        JsonObject extensions = read(context.stagingDir().resolve(LegacyPlantSoilExtensionPass.OUTPUT));
        if (runtime == null && bindings == null && extensions == null) return;

        boolean runtimeAligned = hashMatches(runtime, context.sourceHash());
        boolean bindingsAligned = bindings == null || hashMatches(bindings, context.sourceHash());
        boolean extensionsAligned = hashMatches(extensions, context.sourceHash());
        boolean sourceAligned = runtimeAligned && bindingsAligned && extensionsAligned;
        int sustainOverrides = intValue(extensions, "sourceCanSustainPlantOverrides");
        int fertilityOverrides = intValue(extensions, "sourceFertilityOverrides");

        JsonArray runtimeProofs = runtime == null ? null : runtime.getAsJsonArray("proofs");
        JsonArray itemBindings = bindings == null ? null : bindings.getAsJsonArray("bindings");
        JsonArray proofs = new JsonArray();
        int targetComplete = 0, cropPlacementComplete = 0, survivalComplete = 0, cropFertilityComplete = 0;

        if (runtimeProofs != null) for (JsonElement element : runtimeProofs) {
            if (!element.isJsonObject()) continue;
            JsonObject plant = element.getAsJsonObject();
            String modernId = string(plant, "modernId");
            String family = string(plant, "family");
            boolean plantRuntime = runtimeAligned && bool(plant, "runtimeProofComplete");
            JsonArray matchingItems = new JsonArray();
            Set<String> soilIds = new LinkedHashSet<>();
            int matchingBindings = 0;

            if (bindingsAligned && modernId != null && itemBindings != null) for (JsonElement bindingElement : itemBindings) {
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

            boolean placementRequired = "crops".equals(family) || "reed".equals(family);
            boolean placementTarget = plantRuntime && bindingsAligned && matchingBindings > 0;
            boolean cropSoilIdentity = "crops".equals(family) && soilIds.size() == 1;
            String cropSoilId = cropSoilIdentity ? soilIds.iterator().next() : null;
            boolean cropVanillaPlacement = placementTarget && cropSoilIdentity && "minecraft:farmland".equals(cropSoilId);

            boolean forgeDefaultSurvival = plantRuntime && extensionsAligned && sustainOverrides == 0;
            boolean cropFertility = "crops".equals(family) && plantRuntime && extensionsAligned && fertilityOverrides == 0;

            JsonObject value = new JsonObject();
            copy(plant, value, "legacyRegistryName");
            copy(plant, value, "sourceClass");
            copy(plant, value, "family");
            copy(plant, value, "modernId");
            value.addProperty("plantRuntimeProofComplete", plantRuntime);
            value.add("matchingPlacementItems", matchingItems);
            value.addProperty("placementRequired", placementRequired);
            value.addProperty("placementTargetProofComplete", placementTarget);
            if (cropSoilId != null) value.addProperty("placementSoilId", cropSoilId);
            value.addProperty("placementSoilIdentityComplete", "crops".equals(family) && cropSoilIdentity);
            value.addProperty("vanillaPlacementSoilSemanticsComplete", "crops".equals(family) && cropVanillaPlacement);

            value.addProperty("sourceSoilExtensionProofComplete", extensionsAligned);
            value.addProperty("sourceCanSustainPlantOverrides", sustainOverrides);
            value.addProperty("sourceFertilityOverrides", fertilityOverrides);
            value.add("forgeDefaultSurvivalBlocks", forgeDefaultSurvivalBlocks(family));
            value.addProperty("adjacentWaterRequired", "reed".equals(family));
            value.addProperty("convertedReedSelfStackingByVanillaIdentity", false);
            value.addProperty("forgeCanSustainPlantExtensibilityRequired", true);
            value.addProperty("forgeCanSustainPlantExtensibilityComplete", forgeDefaultSurvival);
            value.addProperty("survivalSoilProofComplete", forgeDefaultSurvival);
            value.addProperty("cropFertilityProofRequired", "crops".equals(family));
            value.addProperty("cropFertilityProofComplete", cropFertility);
            value.addProperty("runtimeComplete", false);

            JsonArray reasons = new JsonArray();
            if (!runtimeAligned) reasons.add("plant-runtime-proof-hash-mismatch");
            else if (!bool(plant, "runtimeProofComplete")) reasons.add("plant-runtime-proof-incomplete");
            if (placementRequired && !bindingsAligned) reasons.add("placement-binding-proof-hash-mismatch");
            if (placementRequired && matchingBindings == 0) reasons.add("placement-item-binding-missing");
            if ("crops".equals(family) && !cropSoilIdentity) reasons.add("crop-placement-soil-identity-incomplete-or-ambiguous");
            if ("crops".equals(family) && cropSoilIdentity && !"minecraft:farmland".equals(cropSoilId)) reasons.add("crop-placement-soil-not-vanilla-farmland");
            if (!extensionsAligned) reasons.add("soil-extension-proof-hash-mismatch");
            if (sustainOverrides > 0) reasons.add("source-can-sustain-plant-overrides-pending");
            if ("crops".equals(family) && fertilityOverrides > 0) reasons.add("source-is-fertile-overrides-pending");
            reasons.add("gameplay-adapter-materialization-pending");
            value.add("reasons", reasons);
            proofs.add(value);

            if (placementTarget) targetComplete++;
            if (cropVanillaPlacement) cropPlacementComplete++;
            if (forgeDefaultSurvival) survivalComplete++;
            if (cropFertility) cropFertilityComplete++;
        }

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 2);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("sourceProofsAligned", sourceAligned);
        root.addProperty("runtimeProofSourceAligned", runtimeAligned);
        root.addProperty("placementBindingSourceAligned", bindingsAligned);
        root.addProperty("soilExtensionSourceAligned", extensionsAligned);
        root.addProperty("sourceCanSustainPlantOverrides", sustainOverrides);
        root.addProperty("sourceFertilityOverrides", fertilityOverrides);
        root.add("proofs", proofs);
        root.addProperty("classifiedBlocks", proofs.size());
        root.addProperty("placementTargetProofCompleteBlocks", targetComplete);
        root.addProperty("cropVanillaPlacementSoilCompleteBlocks", cropPlacementComplete);
        root.addProperty("survivalSoilProofCompleteBlocks", survivalComplete);
        root.addProperty("cropFertilityProofCompleteBlocks", cropFertilityComplete);
        root.addProperty("runtimeCompleteBlocks", 0);
        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
        context.diagnostics().info("LFB-CONVERT-PLANT-SOIL-0001", SupportLevel.RUNTIME_BRIDGE,
                "Separated plant placement, Forge survival and crop fertility proof: blocks=" + proofs.size()
                        + ", placement-target=" + targetComplete
                        + ", vanilla-farmland-placement=" + cropPlacementComplete
                        + ", survival-soil=" + survivalComplete
                        + ", crop-fertility=" + cropFertilityComplete
                        + "; gameplay adapter remains gated.");
    }

    private static JsonArray forgeDefaultSurvivalBlocks(String family) {
        JsonArray result = new JsonArray();
        if ("crops".equals(family) || "bush".equals(family)) {
            result.add("minecraft:grass_block");
            result.add("minecraft:dirt");
            result.add("minecraft:farmland");
        } else if ("reed".equals(family)) {
            result.add("minecraft:grass_block");
            result.add("minecraft:dirt");
            result.add("minecraft:sand");
        }
        return result;
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

    private static int intValue(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsInt() : Integer.MAX_VALUE;
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
