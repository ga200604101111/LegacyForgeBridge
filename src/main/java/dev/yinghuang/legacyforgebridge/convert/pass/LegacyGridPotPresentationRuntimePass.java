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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Promotes source-proven GridPot presentation into a client runtime registration plan. */
public final class LegacyGridPotPresentationRuntimePass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/grid-pot-presentation-runtime.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override public String id() { return "legacy-grid-pot-presentation-runtime"; }

    @Override
    public void apply(ConversionContext context) throws Exception {
        Path proofPath = context.stagingDir().resolve(LegacyGridPotPresentationProofPass.OUTPUT);
        Path corePath = context.stagingDir().resolve(LegacyGridPotBlockPass.OUTPUT);
        if (!Files.isRegularFile(proofPath) || !Files.isRegularFile(corePath)) return;

        JsonObject proofRoot = JsonParser.parseString(Files.readString(proofPath, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject coreRoot = JsonParser.parseString(Files.readString(corePath, StandardCharsets.UTF_8)).getAsJsonObject();
        if (integer(proofRoot, "schemaVersion", 0) != 1 || integer(coreRoot, "schemaVersion", 0) != 1) return;

        Map<String,JsonObject> proofBySource = new LinkedHashMap<>();
        JsonArray proofs = proofRoot.getAsJsonArray("rules");
        if (proofs != null) for (JsonElement element : proofs) {
            if (!element.isJsonObject()) continue;
            JsonObject proof = element.getAsJsonObject();
            if (!bool(proof, "storedContentPresentationProven")) continue;
            String source = string(proof, "sourceBlockClass");
            if (source == null) continue;
            JsonObject previous = proofBySource.putIfAbsent(source, proof);
            if (previous != null && !previous.equals(proof)) proofBySource.remove(source);
        }

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("adaptation", "MODERN_ITEM_MODEL_RENDER_STATE");
        root.addProperty("storedContentPresentationRuntimeWired", true);
        JsonArray rules = new JsonArray();
        JsonArray skipped = new JsonArray();
        JsonArray coreRules = coreRoot.getAsJsonArray("rules");
        if (coreRules != null) for (JsonElement element : coreRules) {
            if (!element.isJsonObject()) continue;
            JsonObject core = element.getAsJsonObject();
            String source = string(core, "sourceBlockClass");
            JsonObject proof = source == null ? null : proofBySource.get(source);
            if (!bool(core, "coreRuntimeComplete") || proof == null) continue;
            String id = string(core, "id");
            if (id == null || integer(core, "cells", 0) != 9 || integer(core, "gridWidth", 0) != 3) {
                addSkipped(skipped, id, source, "GridPot core rule does not satisfy the bounded 3x3 presentation runtime family.");
                continue;
            }
            JsonArray offsets = proof.getAsJsonArray("gridOffsets");
            if (offsets == null || offsets.size() != 3 || !finite(proof, "contentTranslateY") || !finite(proof, "crossedScale")) {
                addSkipped(skipped, id, source, "GridPot presentation proof has malformed transforms.");
                continue;
            }
            JsonObject value = new JsonObject();
            value.addProperty("id", id);
            value.addProperty("sourceBlockClass", source);
            value.addProperty("sourceRendererClass", string(proof, "sourceRendererClass"));
            value.add("gridOffsets", offsets.deepCopy());
            value.addProperty("contentTranslateY", proof.get("contentTranslateY").getAsFloat());
            value.addProperty("sourceContentScale", proof.get("crossedScale").getAsFloat());
            value.addProperty("itemDisplayContext", "NONE");
            value.addProperty("boundingBoxCentered", true);
            value.addProperty("boundingBoxBottomAligned", true);
            value.addProperty("storedContentPresentationProven", true);
            value.addProperty("storedContentPresentationRuntimeWired", true);
            value.addProperty("exactLegacyGeometry", false);
            rules.add(value);
        }
        root.add("rules", rules);
        root.add("skipped", skipped);
        root.addProperty("runtimeRules", rules.size());
        root.addProperty("skippedRules", skipped.size());

        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
        if (!rules.isEmpty()) context.diagnostics().info("LFB-CONVERT-GRIDPOT-PRESENT-RUNTIME-0001", SupportLevel.ADAPTED,
                "Admitted modern item-model GridPot stored-content presentation for " + rules.size()
                        + " block(s); legacy RenderBlocks geometry remains explicitly non-exact.");
    }

    private static void addSkipped(JsonArray skipped, String id, String source, String reason) {
        JsonObject value = new JsonObject();
        if (id != null) value.addProperty("id", id);
        if (source != null) value.addProperty("sourceBlockClass", source);
        value.addProperty("reason", reason);
        skipped.add(value);
    }
    private static boolean finite(JsonObject value, String key) {
        JsonElement element = value.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) return false;
        float number = element.getAsFloat();
        return Float.isFinite(number);
    }
    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key); return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }
    private static int integer(JsonObject object, String key, int fallback) {
        JsonElement value = object.get(key); return value != null && value.isJsonPrimitive() ? value.getAsInt() : fallback;
    }
    private static boolean bool(JsonObject object, String key) {
        JsonElement value = object.get(key); return value != null && value.isJsonPrimitive() && value.getAsBoolean();
    }
}
