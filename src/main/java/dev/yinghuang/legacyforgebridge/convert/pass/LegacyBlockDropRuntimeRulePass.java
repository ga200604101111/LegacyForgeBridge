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
 * Compiles proof-complete static block-drop readiness into a minimal runtime-rule sidecar.
 *
 * <p>This pass deliberately does not wire gameplay behavior. It selects only entries whose normal
 * and silk results are the same metadata-independent self BlockItem and whose per-affected-block
 * explosion source/formula/event/destruction proof is complete. A later runtime slice consumes the
 * rules and resolves the modern non-decay interaction by overriding the converted Block's explosion
 * hook directly.</p>
 */
public final class LegacyBlockDropRuntimeRulePass implements ConversionPass {
    public static final String OUTPUT_PATH = "legacyforgebridge/block-drop-runtime-rules.json";
    public static final int READINESS_SCHEMA = 2;
    public static final String MODE = "STATIC_SELF_DROP_LEGACY_EXPLOSION_OVERRIDE";
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
        root.addProperty("runtimeImplementationWired", false);

        JsonArray rules = new JsonArray();
        JsonArray ready = readiness.getAsJsonArray("ready");
        if (ready != null) {
            for (JsonElement element : ready) {
                if (!element.isJsonObject()) continue;
                JsonObject entry = element.getAsJsonObject();
                if (!eligible(entry)) continue;
                String id = string(entry, "id");
                if (id == null) continue;

                JsonObject rule = new JsonObject();
                copyString(entry, rule, "legacyRegistryName");
                copyString(entry, rule, "legacyNamespace");
                copyString(entry, rule, "sourceClass");
                rule.addProperty("id", id);
                rule.addProperty("mode", MODE);
                rule.addProperty("dropKind", "SELF_BLOCK_ITEM");
                rule.addProperty("quantity", 1);
                rule.addProperty("legacyDamage", 0);
                rule.addProperty("metadataIndependent", true);
                rule.addProperty("legacyExplosionChanceMode", "inverse_explosion_radius");
                rule.addProperty("normalSilkStaticSelfDropProofComplete", true);
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
                "Materialized proof-gated block-drop runtime rules without wiring gameplay behavior: rules="
                        + rules.size() + "."
        );
    }

    private static boolean eligible(JsonObject entry) {
        return bool(entry, "normalSilkStaticSelfDropReady")
                && bool(entry, "explosionSourceProofComplete")
                && bool(entry, "sourceExplosionDestructionOverrideFree")
                && bool(entry, "explosionDecayFormulaProofComplete")
                && bool(entry, "explosionAffectedSetSourceProofComplete")
                && "SELF_BLOCK_ITEM".equals(string(entry, "dropKind"))
                && integer(entry, "quantity", -1) == 1
                && integer(entry, "legacyDamage", -1) == 0
                && bool(entry, "metadataIndependent");
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
