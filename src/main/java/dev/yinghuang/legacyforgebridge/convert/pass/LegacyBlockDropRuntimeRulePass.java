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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Compiles proof-complete block-drop readiness into the runtime-rule sidecar consumed by
 * {@code ConvertedLegacyBlock}.
 *
 * <p>Metadata-independent damage-zero self drops retain the original static rule. A second narrow
 * rule admits source-proven sixteen-entry legacy damage tables only when silk touch is already
 * proven disabled. Both modes still require complete per-affected-block explosion
 * source/formula/event/destruction proof. Runtime revalidates every shape and fails closed for all
 * other entries.</p>
 */
public final class LegacyBlockDropRuntimeRulePass implements ConversionPass {
    public static final String OUTPUT_PATH = "legacyforgebridge/block-drop-runtime-rules.json";
    public static final int READINESS_SCHEMA = 2;
    public static final String MODE = "STATIC_SELF_DROP_LEGACY_EXPLOSION_OVERRIDE";
    public static final String METADATA_MODE = "METADATA_SELF_DROP_LEGACY_EXPLOSION_OVERRIDE";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public String id() {
        return "legacy-block-drop-runtime-rules";
    }

    @Override
    public void apply(ConversionContext context) throws IOException {
        Path readinessPath = context.stagingDir().resolve(LegacyBlockDropRuntimeReadinessPass.OUTPUT_PATH);
        if (!Files.isRegularFile(readinessPath)) return;

        JsonObject readiness = JsonParser.parseString(
                Files.readString(readinessPath, StandardCharsets.UTF_8)).getAsJsonObject();
        int schema = readiness.has("schemaVersion") ? readiness.get("schemaVersion").getAsInt() : -1;
        if (schema != READINESS_SCHEMA) {
            throw new IOException("Unexpected block-drop readiness schema " + schema
                    + "; runtime rule materializer expects " + READINESS_SCHEMA);
        }

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("sourceReadinessSchemaVersion", READINESS_SCHEMA);
        root.addProperty("runtimeImplementationWired", true);

        JsonArray rules = new JsonArray();
        JsonArray ready = readiness.getAsJsonArray("ready");
        if (ready != null) {
            for (JsonElement element : ready) {
                if (!element.isJsonObject()) continue;
                JsonObject entry = element.getAsJsonObject();
                if (!eligible(entry)) continue;
                String id = string(entry, "id");
                if (id == null) continue;

                boolean metadataIndependent = bool(entry, "metadataIndependent");
                JsonObject rule = new JsonObject();
                copyString(entry, rule, "legacyRegistryName");
                copyString(entry, rule, "legacyNamespace");
                copyString(entry, rule, "sourceClass");
                rule.addProperty("id", id);
                rule.addProperty("mode", metadataIndependent ? MODE : METADATA_MODE);
                rule.addProperty("dropKind", "SELF_BLOCK_ITEM");
                rule.addProperty("quantity", 1);
                rule.addProperty("metadataIndependent", metadataIndependent);
                if (metadataIndependent) {
                    rule.addProperty("legacyDamage", 0);
                } else {
                    rule.add("legacyDamageByBlockMeta", entry.getAsJsonArray("legacyDamageByBlockMeta").deepCopy());
                }
                rule.addProperty("legacyExplosionChanceMode", "inverse_explosion_radius");
                rule.addProperty("normalSilkSelfDropProofComplete", true);
                rule.addProperty("normalSilkStaticSelfDropProofComplete", metadataIndependent);
                rule.addProperty("explosionSourceProofComplete", true);
                rule.addProperty("sourceExplosionDestructionOverrideFree", true);
                rule.addProperty("explosionDecayFormulaProofComplete", true);
                rule.addProperty("explosionAffectedSetSourceProofComplete", true);
                rules.add(rule);
            }
        }
        root.add("rules", rules);
        root.addProperty("runtimeRuleCount", rules.size());

        Path output = context.stagingDir().resolve(OUTPUT_PATH);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
        context.diagnostics().info(
                "LFB-CONVERT-BLOCK-DROP-RULES-0001",
                SupportLevel.RUNTIME_BRIDGE,
                "Materialized proof-gated block-drop runtime rules for ConvertedLegacyBlock: rules="
                        + rules.size() + "."
        );
    }

    private static boolean eligible(JsonObject entry) {
        if (!(bool(entry, "normalSilkSelfDropReady") || bool(entry, "normalSilkStaticSelfDropReady"))
                || !bool(entry, "explosionSourceProofComplete")
                || !bool(entry, "sourceExplosionDestructionOverrideFree")
                || !bool(entry, "explosionDecayFormulaProofComplete")
                || !bool(entry, "explosionAffectedSetSourceProofComplete")
                || !"SELF_BLOCK_ITEM".equals(string(entry, "dropKind"))
                || integer(entry, "quantity", -1) != 1) {
            return false;
        }
        if (bool(entry, "metadataIndependent")) {
            return integer(entry, "legacyDamage", -1) == 0;
        }
        return validLegacyDamageTable(entry.getAsJsonArray("legacyDamageByBlockMeta"));
    }

    private static boolean validLegacyDamageTable(JsonArray values) {
        if (values == null || values.size() != 16) return false;
        for (JsonElement value : values) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return false;
            try {
                int damage = value.getAsJsonPrimitive().getAsBigDecimal().intValueExact();
                if (damage < 0 || damage > 15) return false;
            } catch (ArithmeticException exception) {
                return false;
            }
        }
        return true;
    }

    private static void copyString(JsonObject source, JsonObject target, String key) {
        String value = string(source, key);
        if (value != null) target.addProperty(key, value);
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private static boolean bool(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsBoolean();
    }

    private static int integer(JsonObject object, String key, int fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsInt() : fallback;
    }
}
