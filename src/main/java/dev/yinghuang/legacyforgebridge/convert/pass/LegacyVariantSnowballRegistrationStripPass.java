package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyEntityRegistrationStripper;
import dev.yinghuang.legacyforgebridge.convert.LegacyLifecycleAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Removes only the exact legacy registerModEntity call for a runtime-complete variant-snowball
 * projectile. Item GameRegistry registration is deliberately outside this pass.
 */
public final class LegacyVariantSnowballRegistrationStripPass implements ConversionPass {
    public static final String OUTPUT =
            "legacyforgebridge/variant-snowball-registration-strip.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private record RuntimeRule(
            String id,
            String sourceProjectileClass,
            String registryName,
            int numericId,
            int trackingRange,
            int updateFrequency,
            boolean velocityUpdates) { }

    @Override
    public String id() {
        return "legacy-variant-snowball-projectile-registration-strip";
    }

    @Override
    public void apply(ConversionContext context) throws Exception {
        Path runtimePath =
                context.stagingDir().resolve(LegacyVariantSnowballRuntimePass.OUTPUT);
        if (!Files.isRegularFile(runtimePath)) return;

        JsonObject runtime = JsonParser.parseString(
                Files.readString(runtimePath, StandardCharsets.UTF_8)).getAsJsonObject();
        if (integer(runtime, "schemaVersion", -1) != LegacyVariantSnowballRuntimePass.SCHEMA
                || !context.sourceHash().equals(string(runtime, "sourceSha256", ""))
                || !bool(runtime, "runtimeImplementationWired", false)) {
            return;
        }

        java.util.ArrayList<RuntimeRule> runtimeRules = new java.util.ArrayList<>();
        for (JsonElement element : array(runtime, "rules")) {
            if (!element.isJsonObject()) continue;
            JsonObject rule = element.getAsJsonObject();
            if (!bool(rule, "runtimeImplementationWired", false)
                    || !bool(rule, "projectileEntityTypeRegistrationWired", false)
                    || !bool(rule, "projectileRuntimeWired", false)
                    || !bool(rule, "projectileImpactRuntimeWired", false)) continue;
            RuntimeRule parsed = runtimeRule(rule);
            if (parsed != null) runtimeRules.add(parsed);
        }
        if (runtimeRules.isEmpty()) return;

        LegacyLifecycleAnalyzer.Analysis lifecycle =
                new LegacyLifecycleAnalyzer().analyze(context.sourceJar());
        LegacyEntityRegistrationStripper stripper = new LegacyEntityRegistrationStripper();

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("projectileRegistrationStripWired", true);
        root.addProperty("itemRegistrationStripWired", false);
        root.addProperty("sourceClassDeletionWired", false);
        JsonArray rules = new JsonArray();
        int strippedRules = 0;
        int strippedSites = 0;

        for (RuntimeRule runtimeRule : runtimeRules) {
            List<LegacyLifecycleAnalyzer.Registration> matches =
                    lifecycle.of(LegacyLifecycleAnalyzer.Kind.ENTITY).stream()
                            .filter(registration -> matches(registration, runtimeRule))
                            .toList();

            JsonObject value = new JsonObject();
            value.addProperty("id", runtimeRule.id());
            value.addProperty("sourceProjectileClass", runtimeRule.sourceProjectileClass());
            value.addProperty("legacyProjectileRegistryName", runtimeRule.registryName());
            value.addProperty("legacyProjectileNumericId", runtimeRule.numericId());
            JsonArray blockers = new JsonArray();

            int sites = 0;
            String sourceOwner = null;
            String sourceMethod = null;
            String sourceDescriptor = null;

            if (matches.size() != 1) {
                blockers.add(matches.isEmpty()
                        ? "exact-projectile-lifecycle-registration-proof-missing"
                        : "ambiguous-projectile-lifecycle-registration-proof:" + matches.size());
            } else {
                LegacyLifecycleAnalyzer.Registration registration = matches.getFirst();
                sourceOwner = registration.sourceOwner();
                sourceMethod = registration.sourceMethod();
                sourceDescriptor = registration.sourceDescriptor();
                Path classPath = context.stagingDir().resolve(sourceOwner + ".class");

                if (!Files.isRegularFile(classPath)) {
                    blockers.add("projectile-registration-source-class-missing");
                } else {
                    var target = new LegacyEntityRegistrationStripper.Target(
                            sourceMethod,
                            sourceDescriptor,
                            runtimeRule.sourceProjectileClass(),
                            runtimeRule.registryName(),
                            runtimeRule.numericId(),
                            runtimeRule.trackingRange(),
                            runtimeRule.updateFrequency(),
                            runtimeRule.velocityUpdates());
                    var result = stripper.strip(Files.readAllBytes(classPath), target);
                    sites = result.strippedSites();
                    for (String blocker : result.blockers()) blockers.add(blocker);
                    if (sites == 1 && blockers.isEmpty()) {
                        Files.write(classPath, result.bytes());
                    }
                }
            }

            if (sourceOwner != null) value.addProperty("sourceOwner", sourceOwner);
            if (sourceMethod != null) value.addProperty("sourceMethod", sourceMethod);
            if (sourceDescriptor != null) value.addProperty("sourceDescriptor", sourceDescriptor);
            value.addProperty(
                    "projectileRegistrationStripComplete", sites == 1 && blockers.isEmpty());
            value.addProperty("strippedProjectileRegistrationSites", sites);
            value.add("blockers", blockers);
            rules.add(value);

            if (sites == 1 && blockers.isEmpty()) {
                strippedRules++;
                strippedSites += sites;
            }
        }

        root.add("rules", rules);
        root.addProperty("evaluatedRuntimeRules", runtimeRules.size());
        root.addProperty("projectileRegistrationStripCompleteRules", strippedRules);
        root.addProperty("strippedProjectileRegistrationSites", strippedSites);
        root.addProperty(
                "blockedProjectileRegistrationStripRules",
                runtimeRules.size() - strippedRules);

        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        if (strippedRules > 0) {
            context.diagnostics().info(
                    "LFB-CONVERT-VARIANT-SNOWBALL-REGSTRIP-0001",
                    SupportLevel.ADAPTED,
                    "Removed " + strippedSites
                            + " exact legacy registerModEntity callsite(s) for "
                            + strippedRules
                            + " runtime-complete variant-snowball projectile rule(s).");
        }
        if (strippedRules < runtimeRules.size()) {
            context.diagnostics().warning(
                    "LFB-CONVERT-VARIANT-SNOWBALL-REGSTRIP-0002",
                    SupportLevel.RUNTIME_BRIDGE,
                    "Kept " + (runtimeRules.size() - strippedRules)
                            + " variant-snowball projectile registration(s) because exact lifecycle "
                            + "identity or a pure contiguous registerModEntity stack slice was not proven.");
        }
        for (String diagnostic : lifecycle.diagnostics()) {
            context.diagnostics().warning(
                    "LFB-CONVERT-VARIANT-SNOWBALL-REGSTRIP-0003",
                    SupportLevel.MANUAL_REQUIRED,
                    diagnostic);
        }
    }

    private static RuntimeRule runtimeRule(JsonObject rule) {
        String id = string(rule, "id", null);
        String sourceProjectile = string(rule, "sourceProjectileClass", null);
        String name = string(rule, "legacyProjectileRegistryName", null);
        int numeric = integer(rule, "legacyProjectileNumericId", -1);
        int tracking = integer(rule, "legacyTrackingRangeBlocks", -1);
        int frequency = integer(rule, "updateFrequency", -1);
        if (id == null || sourceProjectile == null || name == null
                || numeric < 0 || tracking <= 0 || frequency <= 0) {
            return null;
        }
        return new RuntimeRule(
                id, sourceProjectile, name, numeric, tracking, frequency,
                bool(rule, "velocityUpdates", false));
    }

    private static boolean matches(
            LegacyLifecycleAnalyzer.Registration registration,
            RuntimeRule rule) {
        List<LegacyLifecycleAnalyzer.Value> args = registration.arguments();
        if (args.size() < 7) return false;
        if (!(args.get(0) instanceof LegacyLifecycleAnalyzer.TypeValue type)
                || !rule.sourceProjectileClass().equals(type.internalName())) return false;
        if (!(args.get(1) instanceof LegacyLifecycleAnalyzer.TextValue name)
                || !rule.registryName().equals(name.value())) return false;
        if (!number(args.get(2), rule.numericId())
                || !number(args.get(4), rule.trackingRange())
                || !number(args.get(5), rule.updateFrequency())) return false;
        return booleanLike(args.get(6), rule.velocityUpdates());
    }

    private static boolean number(LegacyLifecycleAnalyzer.Value value, int expected) {
        return value instanceof LegacyLifecycleAnalyzer.NumberValue number
                && number.value().intValue() == expected;
    }

    private static boolean booleanLike(
            LegacyLifecycleAnalyzer.Value value, boolean expected) {
        if (value instanceof LegacyLifecycleAnalyzer.BooleanValue bool) {
            return bool.value() == expected;
        }
        return value instanceof LegacyLifecycleAnalyzer.NumberValue number
                && (number.value().intValue() != 0) == expected;
    }

    private static JsonArray array(JsonObject root, String name) {
        JsonElement value = root.get(name);
        return value != null && value.isJsonArray()
                ? value.getAsJsonArray() : new JsonArray();
    }

    private static boolean bool(JsonObject root, String name, boolean fallback) {
        JsonElement value = root.get(name);
        return value != null && value.isJsonPrimitive()
                ? value.getAsBoolean() : fallback;
    }

    private static int integer(JsonObject root, String name, int fallback) {
        JsonElement value = root.get(name);
        return value != null && value.isJsonPrimitive()
                ? value.getAsInt() : fallback;
    }

    private static String string(JsonObject root, String name, String fallback) {
        JsonElement value = root.get(name);
        return value != null && value.isJsonPrimitive()
                ? value.getAsString() : fallback;
    }
}
