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
import java.util.Objects;

/**
 * Promotes source-complete variant-snowball candidates into a runtime-owned rule sidecar.
 *
 * The projectile EntityType registration is admitted only after joining the variant-snowball
 * family with a unique source-proven EntityRegistry.registerModEntity registration. Item launch,
 * projectile impact behavior and presentation remain deliberately closed.
 */
public final class LegacyVariantSnowballRuntimePass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/variant-snowball-runtime-rules.json";
    public static final int SCHEMA = 2;
    public static final float VANILLA_SNOWBALL_WIDTH = 0.25F;
    public static final float VANILLA_SNOWBALL_HEIGHT = 0.25F;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override public String id() { return "legacy-variant-snowball-runtime-rules"; }

    @Override
    public void apply(ConversionContext context) throws Exception {
        Path candidatePath = context.stagingDir().resolve(LegacyVariantSnowballRuntimeCandidatePass.OUTPUT);
        if (!Files.isRegularFile(candidatePath)) return;

        JsonObject candidates = read(candidatePath);
        JsonObject entityRegistrations = readIfPresent(
                context.stagingDir().resolve(LegacyEntityDataWatcherPass.OUTPUT));

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("runtimeRuleRegistryWired", true);
        root.addProperty("preRegistrationRuleLoadWired", true);
        root.addProperty("projectileEntityTypeRegistrationWired", true);
        root.addProperty("itemRuntimeWired", false);
        root.addProperty("projectileRuntimeWired", false);
        root.addProperty("projectileImpactRuntimeWired", false);
        root.addProperty("rendererRuntimeWired", false);
        root.addProperty("runtimeImplementationWired", false);
        JsonArray rules = new JsonArray();
        JsonArray skipped = new JsonArray();
        root.add("rules", rules);
        root.add("skipped", skipped);

        if (integer(candidates, "schemaVersion", -1) != LegacyVariantSnowballRuntimeCandidatePass.SCHEMA
                || !context.sourceHash().equals(string(candidates, "sourceSha256", ""))
                || bool(candidates, "runtimeImplementationWired")) {
            skip(skipped, null, null, null,
                    "runtime-candidate-sidecar-schema-source-or-runtime-claim-invalid");
            finish(context, root, rules, skipped);
            return;
        }

        boolean entitySidecarValid = entityRegistrations != null
                && integer(entityRegistrations, "schemaVersion", -1) == 1
                && context.sourceHash().equals(string(entityRegistrations, "sourceSha256", ""))
                && !bool(entityRegistrations, "runtimeImplementationWired");

        for (JsonElement element : array(candidates, "rules")) {
            if (!element.isJsonObject()) continue;
            JsonObject source = element.getAsJsonObject();
            String reason = blocker(source, context.metadata().fabricId());

            JsonObject registration = null;
            if (reason == null && !entitySidecarValid) {
                reason = "legacy-projectile-registration-sidecar-missing-or-stale";
            }
            if (reason == null) {
                registration = uniqueProjectileRegistration(
                        entityRegistrations, string(source, "sourceProjectileClass", null));
                if (registration == null) reason = "legacy-projectile-registration-not-uniquely-proven";
            }
            if (reason == null) reason = registrationBlocker(registration);

            if (reason != null) {
                skip(skipped,
                        string(source, "id", null),
                        string(source, "projectileId", null),
                        string(source, "sourceProjectileClass", null),
                        reason);
                continue;
            }

            int trackingRange = integer(registration, "trackingRange", 0);
            JsonObject rule = source.deepCopy();
            rule.addProperty("legacyProjectileRegistryName",
                    string(registration, "legacyRegistryName", null));
            rule.addProperty("legacyProjectileNumericId",
                    integer(registration, "legacyNumericId", -1));
            rule.addProperty("legacyTrackingRangeBlocks", trackingRange);
            rule.addProperty("modernClientTrackingRangeChunks", blocksToTrackingChunks(trackingRange));
            rule.addProperty("updateFrequency", integer(registration, "updateFrequency", 0));
            rule.addProperty("velocityUpdates", true);
            rule.addProperty("width", VANILLA_SNOWBALL_WIDTH);
            rule.addProperty("height", VANILLA_SNOWBALL_HEIGHT);
            rule.addProperty("mobCategory", "MISC");
            rule.addProperty("inheritedVanillaSnowballDimensions", true);
            rule.addProperty("legacyProjectileRegistrationProven", true);
            rule.addProperty("runtimeRuleReady", true);
            rule.addProperty("preRegistrationRuleLoadWired", true);
            rule.addProperty("projectileEntityTypeRegistrationWired", true);
            rule.addProperty("itemRuntimeWired", false);
            rule.addProperty("projectileRuntimeWired", false);
            rule.addProperty("projectileImpactRuntimeWired", false);
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

    private static String registrationBlocker(JsonObject registration) {
        if (registration == null) return "legacy-projectile-registration-not-uniquely-proven";
        if (bool(registration, "runtimeImplementationWired")) {
            return "legacy-projectile-registration-unexpected-runtime-claim";
        }
        if (!bool(registration, "sourceDataWatcherDefinitionComplete")) {
            return "legacy-projectile-registration-proof-incomplete";
        }
        String name = string(registration, "legacyRegistryName", null);
        if (name == null || name.isBlank()) return "legacy-projectile-registry-name-missing";
        if (integer(registration, "legacyNumericId", -1) < 0) {
            return "legacy-projectile-numeric-id-missing";
        }
        if (integer(registration, "trackingRange", 0) <= 0) {
            return "legacy-projectile-tracking-range-invalid";
        }
        if (integer(registration, "updateFrequency", 0) <= 0) {
            return "legacy-projectile-update-frequency-invalid";
        }
        if (!bool(registration, "velocityUpdates")) {
            return "legacy-projectile-velocity-updates-disabled";
        }
        if (!array(registration, "dataWatcherEntries").isEmpty()) {
            return "legacy-projectile-source-datawatcher-state-requires-runtime-sync";
        }
        return null;
    }

    private static JsonObject uniqueProjectileRegistration(JsonObject root, String sourceProjectileClass) {
        if (root == null || sourceProjectileClass == null) return null;
        JsonObject found = null;
        for (JsonElement element : array(root, "rules")) {
            if (!element.isJsonObject()) continue;
            JsonObject rule = element.getAsJsonObject();
            if (!Objects.equals(sourceProjectileClass, string(rule, "sourceClass", null))) continue;
            if (found != null) return null;
            found = rule;
        }
        return found;
    }

    static int blocksToTrackingChunks(int blocks) {
        if (blocks <= 0) throw new IllegalArgumentException("blocks");
        return blocks / 16 + (blocks % 16 == 0 ? 0 : 1);
    }

    private static void finish(ConversionContext context, JsonObject root,
                               JsonArray rules, JsonArray skipped) throws Exception {
        root.addProperty("runtimeRuleCount", rules.size());
        root.addProperty("projectileEntityTypeRuleCount", rules.size());
        root.addProperty("skippedRuntimeRuleCount", skipped.size());
        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        if (!rules.isEmpty()) {
            context.diagnostics().info(
                    "LFB-CONVERT-VARIANT-SNOWBALL-RUNTIME-0003",
                    SupportLevel.RUNTIME_BRIDGE,
                    "Installed " + rules.size()
                            + " source-complete variant-snowball runtime rule family/families with "
                            + "source-proven projectile EntityType registration metadata; item launch, "
                            + "impact gameplay and renderer registration remain fail-closed.");
        }
        if (!skipped.isEmpty()) {
            context.diagnostics().warning(
                    "LFB-CONVERT-VARIANT-SNOWBALL-RUNTIME-0004",
                    SupportLevel.RUNTIME_BRIDGE,
                    "Skipped " + skipped.size()
                            + " variant-snowball runtime candidate(s) while joining source projectile "
                            + "registration metadata.");
        }
    }

    private static void skip(JsonArray skipped, String id, String projectileId,
                             String sourceProjectileClass, String reason) {
        JsonObject value = new JsonObject();
        if (id != null) value.addProperty("id", id);
        if (projectileId != null) value.addProperty("projectileId", projectileId);
        if (sourceProjectileClass != null) value.addProperty("sourceProjectileClass", sourceProjectileClass);
        value.addProperty("reason", reason);
        skipped.add(value);
    }

    private static JsonObject readIfPresent(Path path) throws Exception {
        return Files.isRegularFile(path) ? read(path) : null;
    }

    private static JsonObject read(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
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
}
