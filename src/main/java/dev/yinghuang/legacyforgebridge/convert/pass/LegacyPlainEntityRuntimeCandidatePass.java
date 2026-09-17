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
import java.util.LinkedHashSet;
import java.util.Map;

/** Joins generated plain entity classes with source no-op presentation proof before registry wiring. */
public final class LegacyPlainEntityRuntimeCandidatePass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/plain-entity-runtime-candidates.json";
    public static final String PRESENTATION_ADAPTER_NOOP = "NOOP_RENDERER";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override public String id() { return "legacy-plain-entity-runtime-candidate"; }

    @Override
    public void apply(ConversionContext context) throws Exception {
        Path generatedPath = context.stagingDir().resolve(LegacyPlainEntityCodegenPass.OUTPUT);
        Path presentationPath = context.stagingDir().resolve(LegacyEntityPresentationPass.OUTPUT);
        if (!Files.isRegularFile(generatedPath) || !Files.isRegularFile(presentationPath)) return;
        JsonObject generated = read(generatedPath);
        JsonObject presentation = read(presentationPath);
        if (!valid(generated, context.sourceHash()) || !valid(presentation, context.sourceHash())) return;

        Map<String,JsonObject> presentationByKey = new LinkedHashMap<>();
        for (JsonElement element : array(presentation, "rules")) {
            if (!element.isJsonObject()) continue;
            JsonObject rule = element.getAsJsonObject();
            String key = key(string(rule, "id", null), string(rule, "sourceClass", null));
            if (key != null) presentationByKey.putIfAbsent(key, rule);
        }

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("modernNoOpRendererAdapterAvailable", true);
        root.addProperty("legacyWatcherBridgeRequired", true);
        root.addProperty("constantBehaviorOverrideCodegenRequired", true);
        root.addProperty("entityTypeRegistrationWired", false);
        root.addProperty("clientRendererRegistrationWired", false);
        root.addProperty("runtimeImplementationWired", false);
        JsonArray rules = new JsonArray();
        int readyCount = 0;

        for (JsonElement element : array(generated, "generatedClasses")) {
            if (!element.isJsonObject()) continue;
            JsonObject source = element.getAsJsonObject();
            String id = string(source, "id", null);
            String sourceClass = string(source, "sourceClass", null);
            String key = key(id, sourceClass);
            if (key == null) continue;
            JsonObject presentationRule = presentationByKey.get(key);
            LinkedHashSet<String> blockers = new LinkedHashSet<>();

            if (!bool(source, "classGenerated", false)) blockers.add("generated-entity-class-missing");
            if (!bool(source, "legacyWatcherBridgeWired", false)) blockers.add("legacy-watcher-bridge-missing");
            if (!bool(source, "legacyBaseHurtSemanticsMapped", false)) blockers.add("legacy-base-hurt-semantics-unmapped");
            if (!bool(source, "constantBehaviorOverrideCodegenComplete", false)) blockers.add("constant-behavior-override-codegen-incomplete");
            String generatedClass = string(source, "generatedClass", null);
            if (generatedClass == null || generatedClass.isBlank()) blockers.add("generated-entity-class-identity-missing");
            if (integer(source, "legacyNumericId", -1) < 0) blockers.add("legacy-mod-entity-type-id-missing");
            if (!(decimal(source, "width", -1F) > 0F) || !(decimal(source, "height", -1F) > 0F)) blockers.add("invalid-entity-dimensions");
            if (integer(source, "trackingRange", 0) <= 0) blockers.add("invalid-tracking-range");
            if (integer(source, "updateFrequency", 0) <= 0) blockers.add("invalid-update-frequency");
            if (!bool(source, "velocityUpdates", false)) blockers.add("legacy-velocity-updates-disabled");

            if (presentationRule == null) blockers.add("entity-presentation-proof-missing");
            else if (!bool(presentationRule, "sourceNoOpRendererProven", false)) {
                blockers.add("source-renderer-not-proven-noop");
                for (JsonElement blocker : array(presentationRule, "presentationBlockers"))
                    if (blocker.isJsonPrimitive()) blockers.add(blocker.getAsString());
            }

            boolean ready = blockers.isEmpty();
            JsonObject rule = new JsonObject();
            copy(source, rule, "id"); copy(source, rule, "legacyRegistryName"); copy(source, rule, "sourceClass");
            copy(source, rule, "legacyNumericId");
            copy(source, rule, "generatedClass"); copy(source, rule, "generatedInternalName");
            copy(source, rule, "trackingRange"); copy(source, rule, "updateFrequency"); copy(source, rule, "velocityUpdates");
            copy(source, rule, "width"); copy(source, rule, "height"); copy(source, rule, "synchedDataAccessorCount");
            copy(source, rule, "legacyWatcherBridgeWired");
            copy(source, rule, "constantBehaviorOverrideCodegenComplete");
            copy(source, rule, "constantBehaviorOverrideCount");
            if (source.has("constantBehaviorOverrides")) rule.add("constantBehaviorOverrides", source.get("constantBehaviorOverrides").deepCopy());
            if (source.has("synchedDataEntries")) rule.add("synchedDataEntries", source.get("synchedDataEntries").deepCopy());
            rule.addProperty("presentationAdapter", PRESENTATION_ADAPTER_NOOP);
            rule.addProperty("modernNoOpRendererAdapterAvailable", true);
            rule.addProperty("runtimeCandidateReady", ready);
            rule.addProperty("entityTypeRegistrationWired", false);
            rule.addProperty("clientRendererRegistrationWired", false);
            JsonArray blockerValues = new JsonArray();
            blockers.forEach(blockerValues::add);
            rule.add("blockers", blockerValues);
            if (presentationRule != null) {
                JsonArray registrations = array(presentationRule, "registrations");
                if (registrations.size() == 1 && registrations.get(0).isJsonObject()) {
                    JsonObject registration = registrations.get(0).getAsJsonObject();
                    copy(registration, rule, "rendererClass");
                    copy(registration, rule, "renderOwner");
                    copy(registration, rule, "renderMethod");
                    copy(registration, rule, "renderDescriptor");
                }
            }
            rules.add(rule);
            if (ready) readyCount++;
        }

        root.add("rules", rules);
        root.addProperty("evaluatedGeneratedClasses", rules.size());
        root.addProperty("runtimeCandidateReadyCount", readyCount);
        root.addProperty("blockedRuntimeCandidateCount", rules.size() - readyCount);

        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        if (readyCount > 0) context.diagnostics().info("LFB-CONVERT-ENTITY-CANDIDATE-0001", SupportLevel.RUNTIME_BRIDGE,
                "Prepared " + readyCount + " plain Entity runtime candidate(s) with isolated generated classes, watcher bridges, completed constant-override codegen, and proven source no-op renderer presentation.");
        if (readyCount < rules.size()) context.diagnostics().warning("LFB-CONVERT-ENTITY-CANDIDATE-0002", SupportLevel.RUNTIME_BRIDGE,
                "Blocked " + (rules.size() - readyCount) + " generated plain entity class(es) from runtime candidacy because class/network/dimension/constant-override/presentation proof remained incomplete.");
    }

    private static JsonObject read(Path path) throws Exception { return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject(); }
    private static boolean valid(JsonObject root, String hash) { return integer(root, "schemaVersion", -1) == 1 && hash.equals(string(root, "sourceSha256", "")); }
    private static JsonArray array(JsonObject root, String name) { JsonElement value = root.get(name); return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray(); }
    private static boolean bool(JsonObject root, String name, boolean fallback) { JsonElement value = root.get(name); return value != null && value.isJsonPrimitive() ? value.getAsBoolean() : fallback; }
    private static int integer(JsonObject root, String name, int fallback) { JsonElement value = root.get(name); return value != null && value.isJsonPrimitive() ? value.getAsInt() : fallback; }
    private static float decimal(JsonObject root, String name, float fallback) { JsonElement value = root.get(name); return value != null && value.isJsonPrimitive() ? value.getAsFloat() : fallback; }
    private static String string(JsonObject root, String name, String fallback) { JsonElement value = root.get(name); return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback; }
    private static String key(String id, String sourceClass) { return id == null || sourceClass == null ? null : id + '\u0000' + sourceClass; }
    private static void copy(JsonObject source, JsonObject target, String name) { JsonElement value = source.get(name); if (value != null) target.add(name, value.deepCopy()); }
}
