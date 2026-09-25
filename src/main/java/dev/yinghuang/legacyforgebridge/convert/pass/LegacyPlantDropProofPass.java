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

/**
 * Materializes the exact Forge/Minecraft 1.7.10 unsupported-removal drop semantics for plant
 * families whose source lineage did not override any drop hook. The result is proof only; runtime
 * selection remains gated elsewhere.
 */
public final class LegacyPlantDropProofPass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/plant-drop-proof.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override public String id() { return "legacy-plant-drop-proof"; }

    @Override
    public void apply(ConversionContext context) throws Exception {
        Path lifecyclePath = context.stagingDir().resolve(LegacyPlantLifecyclePass.OUTPUT);
        if (!Files.isRegularFile(lifecyclePath)) return;
        JsonObject lifecycleRoot = readObject(lifecyclePath);
        if (lifecycleRoot == null) return;
        boolean sourceAligned = context.sourceHash().equals(string(lifecycleRoot, "sourceSha256"));
        JsonArray lifecycleProofs = lifecycleRoot.getAsJsonArray("proofs");
        if (lifecycleProofs == null) return;

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("sourceLifecycleAligned", sourceAligned);
        JsonArray rules = new JsonArray();
        int completeCount = 0;

        for (JsonElement element : lifecycleProofs) {
            if (!element.isJsonObject()) continue;
            JsonObject lifecycle = element.getAsJsonObject();
            JsonObject value = new JsonObject();
            copyString(lifecycle, value, "legacyRegistryName");
            copyString(lifecycle, value, "sourceClass");
            copyString(lifecycle, value, "family");
            copyString(lifecycle, value, "modernId");

            String family = string(lifecycle, "family");
            String modernId = string(lifecycle, "modernId");
            boolean identity = bool(lifecycle, "modernIdentityComplete") && modernId != null;
            boolean inherited = bool(lifecycle, "dropsInheritedVanilla");
            JsonArray reasons = new JsonArray();
            if (!sourceAligned) reasons.add("source-lifecycle-hash-mismatch");
            if (!identity) reasons.add("modern-identity-incomplete");
            if (!inherited) reasons.add("source-drop-hooks-present");

            JsonObject drop = inherited && identity ? exactDrop(family, modernId) : null;
            if (drop == null && inherited && identity) reasons.add("unsupported-plant-family");
            boolean complete = sourceAligned && identity && inherited && drop != null;
            value.addProperty("modernIdentityComplete", identity);
            value.addProperty("dropsInheritedVanilla", inherited);
            value.addProperty("unsupportedRemovalDropProofComplete", complete);
            if (drop != null) value.add("unsupportedRemovalDrop", drop);
            value.addProperty("runtimeComplete", false);
            value.add("reasons", reasons);
            rules.add(value);
            if (complete) completeCount++;
        }

        root.add("rules", rules);
        root.addProperty("classifiedBlocks", rules.size());
        root.addProperty("unsupportedRemovalDropProofCompleteBlocks", completeCount);
        root.addProperty("runtimeCompleteBlocks", 0);
        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
        context.diagnostics().info("LFB-CONVERT-PLANT-DROP-0001", SupportLevel.RUNTIME_BRIDGE,
                "Materialized exact inherited Forge 1.7 plant unsupported-removal drop proof: blocks="
                        + rules.size() + ", complete=" + completeCount + "; runtime remains gated.");
        if (!sourceAligned) context.diagnostics().warning("LFB-CONVERT-PLANT-DROP-0002", SupportLevel.MANUAL_REQUIRED,
                "Plant lifecycle proof does not match the current source SHA; plant drop proof failed closed.");
    }

    private static JsonObject exactDrop(String family, String modernId) {
        if (family == null) return null;
        JsonObject drop = new JsonObject();
        drop.addProperty("quantity", 1);
        drop.addProperty("legacyDamage", 0);
        switch (family) {
            case "bush" -> {
                drop.addProperty("kind", "self_block_1_7_10");
                drop.addProperty("itemId", modernId);
            }
            case "reed" -> {
                drop.addProperty("kind", "fixed_item_1_7_10");
                drop.addProperty("itemId", "minecraft:sugar_cane");
            }
            case "crops" -> {
                drop.addProperty("kind", "vanilla_crops_1_7_10");
                drop.addProperty("immatureItemId", "minecraft:wheat_seeds");
                drop.addProperty("matureItemId", "minecraft:wheat");
                drop.addProperty("matureMetadata", 7);
                drop.addProperty("bonusSeedItemId", "minecraft:wheat_seeds");
                drop.addProperty("bonusSeedTrials", 3);
                drop.addProperty("bonusRandomBound", 15);
                drop.addProperty("fortune", 0);
            }
            default -> { return null; }
        }
        return drop;
    }

    private static JsonObject readObject(Path path) throws Exception {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        }
    }

    private static void copyString(JsonObject from, JsonObject to, String key) {
        String value = string(from, key);
        if (value != null) to.addProperty(key, value);
    }

    private static boolean bool(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() && value.getAsBoolean();
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
    }
}
