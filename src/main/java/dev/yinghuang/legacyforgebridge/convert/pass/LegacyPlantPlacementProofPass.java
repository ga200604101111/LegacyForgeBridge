package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyPlantingItemBehaviorAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Intersects source item behavior, constructor bindings and proven plant target semantics before a
 * planting item may be materialized. ItemSeeds, ItemSeedFood and ItemReed remain fail-closed until
 * their family-specific source/runtime proofs are complete.
 */
public final class LegacyPlantPlacementProofPass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/plant-placement-proof.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override public String id() { return "legacy-plant-placement-proof"; }

    @Override
    public void apply(ConversionContext context) throws Exception {
        JsonObject bindingsRoot = read(context.stagingDir().resolve(LegacyItemBlockBindingPass.OUTPUT));
        JsonObject plantsRoot = read(context.stagingDir().resolve(LegacyPlantBlockPass.OUTPUT));
        JsonObject soilRoot = read(context.stagingDir().resolve(LegacyPlantSoilProofPass.OUTPUT));
        if (bindingsRoot == null) return;

        boolean sourceAligned = hashMatches(bindingsRoot, context.sourceHash())
                && hashMatches(plantsRoot, context.sourceHash())
                && hashMatches(soilRoot, context.sourceHash());
        Map<String,LegacyPlantingItemBehaviorAnalyzer.Rule> behavior = new LinkedHashMap<>();
        var behaviorAnalysis = new LegacyPlantingItemBehaviorAnalyzer().analyze(context.sourceJar());
        for (var rule : behaviorAnalysis.rules()) behavior.put(key(rule.registryName(), rule.sourceClass()), rule);
        Map<String,JsonObject> plants = indexByModernId(plantsRoot, "rules");
        Map<String,JsonObject> soils = indexByModernId(soilRoot, "proofs");

        JsonArray output = new JsonArray();
        JsonArray bindings = bindingsRoot.getAsJsonArray("bindings");
        int inheritedItems = 0, targetComplete = 0, placementComplete = 0, seedFoodComplete = 0, runtimeComplete = 0;
        if (bindings != null) for (JsonElement element : bindings) {
            if (!element.isJsonObject()) continue;
            JsonObject binding = element.getAsJsonObject();
            String itemId = string(binding, "id");
            String registry = string(binding, "legacyRegistryName");
            String sourceClass = string(binding, "sourceClass");
            String itemFamily = string(binding, "family");
            JsonObject target = object(binding, "targetBlock");
            JsonObject soil = object(binding, "soilBlock");
            String targetId = string(target, "modernId");
            String soilId = string(soil, "modernId");
            LegacyPlantingItemBehaviorAnalyzer.Rule itemBehavior = behavior.get(key(registry, sourceClass));
            JsonObject plant = plants.get(targetId);
            JsonObject soilProof = soils.get(targetId);

            boolean topology = bool(binding, "topologyProofComplete") && bool(binding, "modernIdentityComplete")
                    && itemId != null && targetId != null;
            boolean inherited = itemBehavior != null && itemBehavior.inheritedVanillaPlacement()
                    && itemFamily != null && itemFamily.equals(itemBehavior.family().name().toLowerCase());
            boolean plantRuntime = soilProof != null && bool(soilProof, "plantRuntimeProofComplete")
                    && bool(soilProof, "survivalSoilProofComplete");
            String plantFamily = string(plant, "family");
            boolean familyMatch = ("seeds".equals(itemFamily) || "seed_food".equals(itemFamily)) && "crops".equals(plantFamily)
                    || "reed".equals(itemFamily) && "reed".equals(plantFamily);
            boolean targetCallbacks = !"reed".equals(itemFamily)
                    || plant != null && bool(plant, "inheritedVanillaLifecycleOnly");
            boolean targetProof = plant != null && soilProof != null && plantRuntime && familyMatch
                    && bool(soilProof, "placementTargetProofComplete") && targetCallbacks;
            boolean soilProofComplete = !"seeds".equals(itemFamily) && !"seed_food".equals(itemFamily)
                    || soilId != null && bool(soilProof, "placementSoilIdentityComplete")
                    && soilId.equals(string(soilProof, "placementSoilId"));

            boolean seedFoodRequired = "seed_food".equals(itemFamily);
            Integer nutrition = integer(binding, "nutrition");
            Float saturation = floating(binding, "saturationModifier");
            boolean seedFoodProperties = !seedFoodRequired || nutrition != null && nutrition >= 0
                    && saturation != null && Float.isFinite(saturation) && saturation >= 0F;
            boolean complete = sourceAligned && topology && inherited && targetProof && soilProofComplete && seedFoodProperties;
            boolean supportedFamily = "seeds".equals(itemFamily) || "seed_food".equals(itemFamily) || "reed".equals(itemFamily);
            boolean runtime = complete && supportedFamily;

            JsonObject value = new JsonObject();
            copy(binding, value, "id"); copy(binding, value, "legacyRegistryName");
            copy(binding, value, "sourceClass"); copy(binding, value, "family");
            if (targetId != null) value.addProperty("targetBlockId", targetId);
            if (soilId != null) value.addProperty("soilBlockId", soilId);
            if (seedFoodRequired && seedFoodProperties) {
                value.addProperty("nutrition", nutrition);
                value.addProperty("saturationModifier", saturation);
            }
            value.addProperty("topologyProofComplete", topology);
            value.addProperty("inheritedVanillaPlacement", inherited);
            JsonArray methods = new JsonArray();
            if (itemBehavior != null) itemBehavior.sourceInstanceMethods().forEach(methods::add);
            value.add("sourceInstanceMethods", methods);
            value.addProperty("targetPlantRuntimeProofComplete", plantRuntime);
            value.addProperty("targetPlacementCallbacksInherited", targetCallbacks);
            value.addProperty("targetProofComplete", targetProof);
            value.addProperty("soilPlacementProofComplete", soilProofComplete);
            value.addProperty("seedFoodPropertiesProofRequired", seedFoodRequired);
            value.addProperty("seedFoodPropertiesProofComplete", seedFoodProperties);
            value.addProperty("placementProofComplete", complete);
            if (complete) value.addProperty("placementAdapter", adapter(itemFamily));
            value.addProperty("runtimeComplete", runtime);
            JsonArray reasons = new JsonArray();
            if (!sourceAligned) reasons.add("source-proof-hash-mismatch");
            if (!topology) reasons.add("item-block-topology-incomplete");
            if (!inherited) reasons.add("source-item-placement-override-or-lineage-unproven");
            if (!familyMatch) reasons.add("item-target-plant-family-mismatch");
            if (!plantRuntime) reasons.add("target-plant-runtime-proof-incomplete");
            if (!targetCallbacks) reasons.add("item-reed-target-placement-callbacks-unproven");
            if (!soilProofComplete) reasons.add("seed-placement-soil-proof-incomplete");
            if (!seedFoodProperties) reasons.add("seed-food-properties-incomplete");
            value.add("reasons", reasons);
            output.add(value);
            if (inherited) inheritedItems++;
            if (targetProof) targetComplete++;
            if (complete) placementComplete++;
            if (seedFoodRequired && seedFoodProperties) seedFoodComplete++;
            if (runtime) runtimeComplete++;
        }

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 2);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("sourceProofsAligned", sourceAligned);
        root.add("rules", output);
        root.addProperty("classifiedItems", output.size());
        root.addProperty("inheritedVanillaPlacementItems", inheritedItems);
        root.addProperty("targetProofCompleteItems", targetComplete);
        root.addProperty("placementProofCompleteItems", placementComplete);
        root.addProperty("seedFoodPropertiesProofCompleteItems", seedFoodComplete);
        root.addProperty("runtimeCompleteItems", runtimeComplete);
        Path path = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(path.getParent());
        Files.writeString(path, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
        context.diagnostics().info("LFB-CONVERT-PLANT-PLACEMENT-0001", SupportLevel.RUNTIME_BRIDGE,
                "Intersected source-proven plant item placement evidence: items=" + output.size()
                        + ", inherited=" + inheritedItems + ", target=" + targetComplete
                        + ", placement-proof=" + placementComplete + ", runtime=" + runtimeComplete
                        + "; ItemReed requires inherited target placement callbacks before materialization.");
        behaviorAnalysis.diagnostics().forEach(message -> context.diagnostics().warning(
                "LFB-CONVERT-PLANT-PLACEMENT-0002", SupportLevel.MANUAL_REQUIRED, message));
    }

    private static String adapter(String family) {
        return switch (family) {
            case "seeds" -> "item_seeds_1_7_10";
            case "seed_food" -> "item_seed_food_1_7_10";
            case "reed" -> "item_reed_1_7_10";
            default -> throw new IllegalArgumentException("Unsupported planting item family: " + family);
        };
    }

    private static Map<String,JsonObject> indexByModernId(JsonObject root, String arrayName) {
        if (root == null) return Map.of();
        JsonArray values = root.getAsJsonArray(arrayName);
        if (values == null) return Map.of();
        Map<String,JsonObject> result = new LinkedHashMap<>();
        Set<String> ambiguous = new LinkedHashSet<>();
        for (JsonElement element : values) {
            if (!element.isJsonObject()) continue;
            JsonObject value = element.getAsJsonObject();
            String id = string(value, "modernId");
            if (id == null || ambiguous.contains(id)) continue;
            if (result.putIfAbsent(id, value) != null) { result.remove(id); ambiguous.add(id); }
        }
        return result;
    }

    private static JsonObject read(Path path) throws Exception {
        if (!Files.isRegularFile(path)) return null;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement value = JsonParser.parseReader(reader);
            return value.isJsonObject() ? value.getAsJsonObject() : null;
        }
    }
    private static boolean hashMatches(JsonObject root, String hash) { return root != null && hash.equals(string(root, "sourceSha256")); }
    private static boolean bool(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() && value.getAsBoolean();
    }
    private static String string(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
    }
    private static Integer integer(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsInt() : null;
    }
    private static Float floating(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsFloat() : null;
    }
    private static JsonObject object(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }
    private static void copy(JsonObject from, JsonObject to, String key) {
        JsonElement value = from == null ? null : from.get(key); if (value != null) to.add(key, value.deepCopy());
    }
    private static String key(String registry, String sourceClass) { return String.valueOf(registry) + "\u0000" + String.valueOf(sourceClass); }
}
