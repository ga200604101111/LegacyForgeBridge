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

/**
 * Promotes source-complete variant-snowball candidates into a runtime-owned rule sidecar.
 *
 * This pass intentionally does not claim projectile or item gameplay implementation. It only
 * establishes the fail-closed configuration boundary that GeneratedModSupport loads before any
 * generated item registration occurs.
 */
public final class LegacyVariantSnowballRuntimePass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/variant-snowball-runtime-rules.json";
    public static final int SCHEMA = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override public String id() { return "legacy-variant-snowball-runtime-rules"; }

    @Override
    public void apply(ConversionContext context) throws Exception {
        Path candidatePath = context.stagingDir().resolve(LegacyVariantSnowballRuntimeCandidatePass.OUTPUT);
        if (!Files.isRegularFile(candidatePath)) return;

        JsonObject candidates = JsonParser.parseString(
                Files.readString(candidatePath, StandardCharsets.UTF_8)).getAsJsonObject();

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("runtimeRuleRegistryWired", true);
        root.addProperty("preRegistrationRuleLoadWired", true);
        root.addProperty("itemRuntimeWired", false);
        root.addProperty("projectileRuntimeWired", false);
        root.addProperty("rendererRuntimeWired", false);
        root.addProperty("runtimeImplementationWired", false);
        JsonArray rules = new JsonArray();
        JsonArray skipped = new JsonArray();
        root.add("rules", rules);
        root.add("skipped", skipped);

        if (integer(candidates, "schemaVersion", -1) != LegacyVariantSnowballRuntimeCandidatePass.SCHEMA
                || !context.sourceHash().equals(string(candidates, "sourceSha256", ""))
                || bool(candidates, "runtimeImplementationWired")) {
            JsonObject skip = new JsonObject();
            skip.addProperty("reason", "runtime-candidate-sidecar-schema-source-or-runtime-claim-invalid");
            skipped.add(skip);
            finish(context, root, rules, skipped);
            return;
        }

        for (JsonElement element : array(candidates, "rules")) {
            if (!element.isJsonObject()) continue;
            JsonObject source = element.getAsJsonObject();
            String reason = blocker(source, context.metadata().fabricId());
            if (reason != null) {
                JsonObject skip = new JsonObject();
                copy(source, skip, "id");
                copy(source, skip, "projectileId");
                copy(source, skip, "legacyRegistryName");
                copy(source, skip, "sourceItemClass");
                skip.addProperty("reason", reason);
                skipped.add(skip);
                continue;
            }

            JsonObject rule = source.deepCopy();
            rule.addProperty("runtimeRuleReady", true);
            rule.addProperty("preRegistrationRuleLoadWired", true);
            rule.addProperty("itemRuntimeWired", false);
            rule.addProperty("projectileRuntimeWired", false);
            rule.addProperty("rendererRuntimeWired", false);
            rule.addProperty("runtimeImplementationWired", false);
            rules.add(rule);
        }

        finish(context, root, rules, skipped);
    }

    private static String blocker(JsonObject rule, String modId) {
        if (!bool(rule, "runtimeCandidateReady")
                || !bool(rule, "sourceSemanticsComplete")
                || !bool(rule, "itemUseSemanticsComplete")
                || !bool(rule, "impactSemanticsComplete")) {
            return "source-complete-runtime-candidate-required";
        }
        if (bool(rule, "runtimeImplementationWired")) return "candidate-unexpectedly-claims-runtime";
        if (!"VARIANT_SNOWBALL".equals(string(rule, "adapter", null))
                || !"THROWN_ITEM".equals(string(rule, "rendererAdapter", null))) {
            return "runtime-adapter-contract-mismatch";
        }

        String id = string(rule, "id", null);
        String projectileId = string(rule, "projectileId", null);
        if (!validId(id) || !validId(projectileId)) return "runtime-identities-invalid";
        if (!namespace(id).equals(modId) || !namespace(projectileId).equals(modId)) {
            return "runtime-identities-outside-generated-mod-namespace";
        }

        if (!"random.bow".equals(string(rule, "launchSound", null))
                || !exactFloat(rule, "launchVolume", 0.5F)
                || !exactFloat(rule, "launchPitchNumerator", 0.4F)
                || !exactFloat(rule, "launchPitchRandomScale", 0.4F)
                || !exactFloat(rule, "launchPitchBase", 0.8F)
                || !bool(rule, "consumeOutsideCreative")
                || !bool(rule, "serverAuthoritativeLaunch")) {
            return "normalized-launch-contract-incomplete";
        }

        JsonArray variants = array(rule, "variants");
        if (variants.isEmpty() || integer(rule, "variantCount", -1) != variants.size()) {
            return "normalized-variant-set-incomplete";
        }
        return null;
    }

    private static void finish(ConversionContext context, JsonObject root,
                               JsonArray rules, JsonArray skipped) throws Exception {
        root.addProperty("runtimeRuleCount", rules.size());
        root.addProperty("skippedRuntimeRuleCount", skipped.size());
        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        if (!rules.isEmpty()) {
            context.diagnostics().info(
                    "LFB-CONVERT-VARIANT-SNOWBALL-RUNTIME-0003",
                    SupportLevel.RUNTIME_BRIDGE,
                    "Installed " + rules.size()
                            + " source-complete variant-snowball rule family/families into the "
                            + "pre-registration runtime sidecar; item/projectile gameplay remains fail-closed.");
        }
        if (!skipped.isEmpty()) {
            context.diagnostics().warning(
                    "LFB-CONVERT-VARIANT-SNOWBALL-RUNTIME-0004",
                    SupportLevel.RUNTIME_BRIDGE,
                    "Skipped " + skipped.size()
                            + " variant-snowball runtime candidate(s) while materializing the "
                            + "pre-registration rule boundary.");
        }
    }

    private static JsonArray array(JsonObject root, String name) {
        JsonElement value = root == null ? null : root.get(name);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    private static boolean bool(JsonObject root, String name) {
        JsonElement value = root == null ? null : root.get(name);
        return value != null && value.isJsonPrimitive() && value.getAsBoolean();
    }

    private static int integer(JsonObject root, String name, int fallback) {
        JsonElement value = root == null ? null : root.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsInt() : fallback;
    }

    private static String string(JsonObject root, String name, String fallback) {
        JsonElement value = root == null ? null : root.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    private static boolean exactFloat(JsonObject root, String name, float expected) {
        JsonElement value = root == null ? null : root.get(name);
        return value != null && value.isJsonPrimitive()
                && Float.compare(value.getAsFloat(), expected) == 0;
    }

    private static boolean validId(String id) {
        if (id == null) return false;
        int colon = id.indexOf(':');
        return colon > 0 && colon == id.lastIndexOf(':') && colon < id.length() - 1
                && id.substring(0, colon).matches("[a-z0-9_.-]+")
                && id.substring(colon + 1).matches("[a-z0-9/._-]+");
    }

    private static String namespace(String id) {
        return id.substring(0, id.indexOf(':'));
    }

    private static void copy(JsonObject source, JsonObject target, String name) {
        JsonElement value = source.get(name);
        if (value != null) target.add(name, value.deepCopy());
    }
}
