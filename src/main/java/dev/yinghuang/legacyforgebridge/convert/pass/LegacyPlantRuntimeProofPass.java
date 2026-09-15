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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Intersects independent plant topology, lifecycle and presentation evidence before any gameplay
 * adapter is allowed to exist. This pass proves eligibility only; it never enables runtime itself.
 */
public final class LegacyPlantRuntimeProofPass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/plant-runtime-proof.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override public String id() { return "legacy-plant-runtime-proof"; }

    @Override
    public void apply(ConversionContext context) throws Exception {
        Path familyPath = context.stagingDir().resolve(LegacyPlantBlockPass.OUTPUT);
        Path lifecyclePath = context.stagingDir().resolve(LegacyPlantLifecyclePass.OUTPUT);
        Path presentationPath = context.stagingDir().resolve(LegacyPlantPresentationPass.OUTPUT);
        if (!Files.isRegularFile(familyPath) && !Files.isRegularFile(lifecyclePath) && !Files.isRegularFile(presentationPath)) return;

        JsonObject familyRoot = readObject(familyPath);
        JsonObject lifecycleRoot = readObject(lifecyclePath);
        JsonObject presentationRoot = readObject(presentationPath);
        boolean sourceAligned = sourceMatches(familyRoot, context.sourceHash())
                && sourceMatches(lifecycleRoot, context.sourceHash())
                && sourceMatches(presentationRoot, context.sourceHash());

        Map<String,JsonObject> families = index(familyRoot, "rules");
        Map<String,JsonObject> lifecycles = index(lifecycleRoot, "proofs");
        Map<String,JsonObject> presentations = index(presentationRoot, "rules");
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(families.keySet());
        keys.addAll(lifecycles.keySet());
        keys.addAll(presentations.keySet());

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 2);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("sourceProofsAligned", sourceAligned);
        root.addProperty("forgePlantableContractRequired", true);
        JsonArray proofs = new JsonArray();
        int identityComplete = 0, lifecycleComplete = 0, presentationComplete = 0, runtimeProofComplete = 0;

        for (String key : keys) {
            JsonObject family = families.get(key);
            JsonObject lifecycle = lifecycles.get(key);
            JsonObject presentation = presentations.get(key);
            JsonArray reasons = new JsonArray();

            String registryName = firstString(family, lifecycle, presentation, "legacyRegistryName");
            String sourceClass = firstString(family, lifecycle, presentation, "sourceClass");
            String familyName = firstString(family, lifecycle, presentation, "family");
            String modernId = consistentModernId(family, lifecycle, presentation);

            boolean familyConsistent = family != null && lifecycle != null && presentation != null
                    && sameString(family, lifecycle, "family") && sameString(family, presentation, "family");
            boolean identity = modernId != null
                    && bool(family, "modernIdentityComplete")
                    && bool(lifecycle, "modernIdentityComplete")
                    && bool(presentation, "modernIdentityComplete");
            boolean lifecycleOk = familyConsistent && lifecycleComplete(lifecycle, familyName);
            boolean presentationOk = familyConsistent && bool(presentation, "presentationComplete")
                    && bool(presentation, "cutoutRuntimeComplete");

            if (!sourceAligned) reasons.add("source-proof-hash-mismatch");
            if (!familyConsistent) reasons.add("plant-family-proof-missing-or-inconsistent");
            if (!identity) reasons.add("modern-identity-incomplete-or-inconsistent");
            if (!lifecycleOk) reasons.add("lifecycle-proof-incomplete");
            if (!presentationOk) reasons.add("presentation-proof-incomplete");

            boolean complete = sourceAligned && familyConsistent && identity && lifecycleOk && presentationOk;
            JsonObject value = new JsonObject();
            if (registryName != null) value.addProperty("legacyRegistryName", registryName);
            if (sourceClass != null) value.addProperty("sourceClass", sourceClass);
            if (familyName != null) value.addProperty("family", familyName);
            if (modernId != null) value.addProperty("modernId", modernId);
            value.addProperty("familyProofComplete", familyConsistent);
            value.addProperty("modernIdentityComplete", identity);
            value.addProperty("forgePlantableContractComplete", lifecycle != null && bool(lifecycle, "forgePlantableContractInherited"));
            value.addProperty("lifecycleProofComplete", lifecycleOk);
            value.addProperty("presentationProofComplete", presentationOk);
            value.addProperty("runtimeProofComplete", complete);
            if (complete) value.addProperty("runtimeAdapter", adapter(familyName));
            value.addProperty("runtimeComplete", false);
            value.add("reasons", reasons);
            proofs.add(value);

            if (identity) identityComplete++;
            if (lifecycleOk) lifecycleComplete++;
            if (presentationOk) presentationComplete++;
            if (complete) runtimeProofComplete++;
        }

        root.add("proofs", proofs);
        root.addProperty("classifiedBlocks", proofs.size());
        root.addProperty("modernIdentityCompleteBlocks", identityComplete);
        root.addProperty("lifecycleProofCompleteBlocks", lifecycleComplete);
        root.addProperty("presentationProofCompleteBlocks", presentationComplete);
        root.addProperty("runtimeProofCompleteBlocks", runtimeProofComplete);
        root.addProperty("runtimeCompleteBlocks", 0);
        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        context.diagnostics().info("LFB-CONVERT-PLANT-RUNTIME-0001", SupportLevel.RUNTIME_BRIDGE,
                "Intersected Forge 1.7 plant runtime evidence: blocks=" + proofs.size()
                        + ", identity=" + identityComplete
                        + ", lifecycle=" + lifecycleComplete
                        + ", presentation=" + presentationComplete
                        + ", runtime-proof=" + runtimeProofComplete
                        + "; gameplay adapters remain disabled until materialization.");
        if (!sourceAligned) context.diagnostics().warning("LFB-CONVERT-PLANT-RUNTIME-0002", SupportLevel.MANUAL_REQUIRED,
                "Plant proof sidecars do not all match the current source SHA; runtime proof was failed closed.");
    }

    private static boolean lifecycleComplete(JsonObject proof, String family) {
        if (proof == null || family == null) return false;
        if (!bool(proof, "forgePlantableContractInherited")
                || !bool(proof, "survivalInheritedVanilla")
                || !bool(proof, "dropsInheritedVanilla")) return false;
        String age = string(proof, "ageModel");
        String survival = string(proof, "survivalModel");
        return switch (family) {
            case "crops" -> bool(proof, "growthInheritedVanilla")
                    && bool(proof, "bonemealInheritedVanilla")
                    && "legacy_meta_0_7".equals(age)
                    && "forge_crops_plains".equals(survival);
            case "reed" -> bool(proof, "growthInheritedVanilla")
                    && "legacy_meta_timer_0_15".equals(age)
                    && "forge_reed_beach".equals(survival);
            case "bush" -> "none".equals(age) && "forge_bush_plains".equals(survival);
            default -> false;
        };
    }

    private static String adapter(String family) {
        return switch (family) {
            case "crops" -> "legacy_crops_1_7_10";
            case "reed" -> "legacy_reed_1_7_10";
            case "bush" -> "legacy_bush_1_7_10";
            default -> throw new IllegalArgumentException("Unsupported plant family: " + family);
        };
    }

    private static JsonObject readObject(Path path) throws Exception {
        if (!Files.isRegularFile(path)) return null;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        }
    }

    private static boolean sourceMatches(JsonObject root, String sourceHash) {
        return root != null && root.has("sourceSha256") && sourceHash.equals(root.get("sourceSha256").getAsString());
    }

    private static Map<String,JsonObject> index(JsonObject root, String arrayName) {
        if (root == null) return Map.of();
        JsonArray values = root.getAsJsonArray(arrayName);
        if (values == null) return Map.of();
        Map<String,JsonObject> result = new LinkedHashMap<>();
        Set<String> ambiguous = new LinkedHashSet<>();
        for (JsonElement element : values) {
            if (!element.isJsonObject()) continue;
            JsonObject value = element.getAsJsonObject();
            String registry = string(value, "legacyRegistryName");
            String sourceClass = string(value, "sourceClass");
            if (registry == null || sourceClass == null) continue;
            String key = key(registry, sourceClass);
            if (ambiguous.contains(key)) continue;
            if (result.putIfAbsent(key, value) != null) {
                result.remove(key);
                ambiguous.add(key);
            }
        }
        return result;
    }

    private static String consistentModernId(JsonObject... values) {
        String result = null;
        for (JsonObject value : values) {
            String candidate = string(value, "modernId");
            if (candidate == null) return null;
            if (result == null) result = candidate;
            else if (!result.equals(candidate)) return null;
        }
        return result;
    }

    private static boolean sameString(JsonObject left, JsonObject right, String key) {
        String a = string(left, key), b = string(right, key);
        return a != null && a.equals(b);
    }

    private static String firstString(JsonObject a, JsonObject b, JsonObject c, String key) {
        String value = string(a, key);
        if (value != null) return value;
        value = string(b, key);
        return value != null ? value : string(c, key);
    }

    private static boolean bool(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() && value.getAsBoolean();
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
    }

    private static String key(String registryName, String sourceClass) {
        return registryName + "\u0000" + sourceClass;
    }
}
