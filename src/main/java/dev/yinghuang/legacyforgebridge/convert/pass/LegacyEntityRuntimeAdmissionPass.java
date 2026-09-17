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

/**
 * Joins watcher, behavior, and construction proof IR into a deliberately narrow first runtime
 * admission family. Admission is evidence only; generated EntityType runtime is a later pass.
 */
public final class LegacyEntityRuntimeAdmissionPass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/entity-runtime-admission.json";
    public static final String FAMILY_PLAIN_SYNCHED_DATA_ONLY = "PLAIN_ENTITY_SYNCHED_DATA_ONLY";
    private static final String VANILLA_ENTITY = "net/minecraft/entity/Entity";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override public String id() { return "legacy-entity-runtime-admission"; }

    @Override
    public void apply(ConversionContext context) throws Exception {
        Path runtimePath = context.stagingDir().resolve(LegacyEntityRuntimePlanPass.OUTPUT);
        Path behaviorPath = context.stagingDir().resolve(LegacyEntityBehaviorSurfacePass.OUTPUT);
        Path constructionPath = context.stagingDir().resolve(LegacyEntityConstructionPass.OUTPUT);
        if (!Files.isRegularFile(runtimePath) || !Files.isRegularFile(behaviorPath) || !Files.isRegularFile(constructionPath)) return;

        JsonObject runtime = read(runtimePath);
        JsonObject behavior = read(behaviorPath);
        JsonObject construction = read(constructionPath);
        if (!validInput(runtime, context.sourceHash()) || !validInput(behavior, context.sourceHash())
                || !validInput(construction, context.sourceHash())) return;

        Map<String,JsonObject> behaviorByKey = index(behavior);
        Map<String,JsonObject> constructionByKey = index(construction);
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("runtimeImplementationWired", false);

        JsonArray rules = new JsonArray();
        int admittedCount = 0;
        for (JsonElement element : array(runtime, "rules")) {
            if (!element.isJsonObject()) continue;
            JsonObject runtimeRule = element.getAsJsonObject();
            String registryName = string(runtimeRule, "legacyRegistryName", null);
            String sourceClass = string(runtimeRule, "sourceClass", null);
            String key = key(registryName, sourceClass);
            if (key == null) continue;

            JsonObject behaviorRule = behaviorByKey.get(key);
            JsonObject constructionRule = constructionByKey.get(key);
            JsonArray blockers = new JsonArray();

            if (!bool(runtimeRule, "synchedDataMappingComplete", false)) blockers.add("synched-data-mapping-incomplete");
            if (!bool(runtimeRule, "sourceWideDataWatcherCallClosureComplete", false)) blockers.add("source-wide-datawatcher-call-closure-incomplete");
            // 1.21.11 EntityType exposes trackDeltas/alwaysUpdateVelocity, but the currently admitted
            // builder surface has no proven way to represent the legacy registerModEntity false case.
            // Keep the first runtime family exact by admitting only legacy velocityUpdates=true.
            if (!bool(runtimeRule, "velocityUpdates", false)) blockers.add("legacy-velocity-updates-disabled");

            if (behaviorRule == null) blockers.add("behavior-surface-missing");
            else {
                if (!bool(behaviorRule, "sourceOwnedBehaviorInventoryComplete", false)) blockers.add("behavior-surface-incomplete");
                if (!VANILLA_ENTITY.equals(string(behaviorRule, "externalBaseClass", null))) blockers.add("unsupported-external-entity-base");
                if (number(behaviorRule, "unclassifiedSourceMethodCount", -1) != 0) blockers.add("unclassified-source-instance-methods");
                for (JsonElement callbackElement : array(behaviorRule, "callbacks")) {
                    if (!callbackElement.isJsonObject()) { blockers.add("malformed-behavior-callback"); continue; }
                    JsonObject callback = callbackElement.getAsJsonObject();
                    String kind = string(callback, "kind", "");
                    if ("ENTITY_INIT".equals(kind)) continue;
                    if (("READ_NBT".equals(kind) || "WRITE_NBT".equals(kind)) && trivialNoOpMethod(behaviorRule, callback)) continue;
                    blockers.add("unsupported-callback:" + kind);
                }
                for (JsonElement methodElement : array(behaviorRule, "sourceMethods")) {
                    if (!methodElement.isJsonObject()) { blockers.add("malformed-source-method"); continue; }
                    JsonObject method = methodElement.getAsJsonObject();
                    String kind = string(method, "callbackKind", null);
                    if ("ENTITY_INIT".equals(kind)) continue;
                    if (("READ_NBT".equals(kind) || "WRITE_NBT".equals(kind)) && bool(method, "trivialNoOp", false)) continue;
                    blockers.add("unsupported-source-method:" + string(method, "owner", "?") + "."
                            + string(method, "method", "?") + string(method, "descriptor", ""));
                }
            }

            if (constructionRule == null) blockers.add("construction-surface-missing");
            else {
                if (!VANILLA_ENTITY.equals(string(constructionRule, "externalBaseClass", null))) blockers.add("unsupported-construction-base");
                if (!bool(constructionRule, "worldConstructorPresent", false)) blockers.add("world-constructor-missing");
                if (!bool(constructionRule, "constructorChainComplete", false)) blockers.add("constructor-chain-incomplete");
                if (!bool(constructionRule, "constructorControlFlowSimple", false)) blockers.add("constructor-control-flow-complex");
                if (bool(constructionRule, "sourceSetSizeOverridePresent", false)) blockers.add("source-setsize-override");
                if (!bool(constructionRule, "sizeProofComplete", false)) blockers.add("entity-dimensions-unproven");
                if (number(constructionRule, "unmappedConstructorEffectCount", -1) != 0) blockers.add("unmapped-constructor-effects");
            }

            blockers = deduplicate(blockers);
            boolean admitted = blockers.size() == 0;
            JsonObject rule = new JsonObject();
            copy(runtimeRule, rule, "id");
            copy(runtimeRule, rule, "legacyRegistryName");
            copy(runtimeRule, rule, "sourceClass");
            copy(runtimeRule, rule, "legacyNumericId");
            copy(runtimeRule, rule, "trackingRange");
            copy(runtimeRule, rule, "updateFrequency");
            copy(runtimeRule, rule, "velocityUpdates");
            rule.addProperty("family", FAMILY_PLAIN_SYNCHED_DATA_ONLY);
            rule.addProperty("admitted", admitted);
            rule.addProperty("runtimeImplementationWired", false);
            rule.add("blockers", blockers);
            if (runtimeRule.has("synchedDataEntries")) rule.add("synchedDataEntries", runtimeRule.get("synchedDataEntries").deepCopy());
            if (constructionRule != null && bool(constructionRule, "sizeProofComplete", false)) {
                copy(constructionRule, rule, "width");
                copy(constructionRule, rule, "height");
            }
            rules.add(rule);
            if (admitted) admittedCount++;
        }
        root.add("rules", rules);
        root.addProperty("evaluatedRegistrations", rules.size());
        root.addProperty("admittedRegistrations", admittedCount);
        root.addProperty("blockedRegistrations", rules.size() - admittedCount);

        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        if (admittedCount > 0) context.diagnostics().info("LFB-CONVERT-ENTITY-ADMISSION-0001", SupportLevel.RUNTIME_BRIDGE,
                "Admitted " + admittedCount + " plain Entity synchronized-data-only registration(s) to the runtime candidate family; runtime code generation remains unwired.");
        if (admittedCount < rules.size()) context.diagnostics().warning("LFB-CONVERT-ENTITY-ADMISSION-0002", SupportLevel.RUNTIME_BRIDGE,
                "Blocked " + (rules.size() - admittedCount) + " entity registration(s) from the first runtime family because behavior/construction/watcher/velocity proof gates remain incomplete or unsupported.");
    }

    private static boolean trivialNoOpMethod(JsonObject behaviorRule, JsonObject callback) {
        String owner = string(callback, "owner", null);
        String method = string(callback, "method", null);
        String descriptor = string(callback, "descriptor", null);
        if (owner == null || method == null || descriptor == null) return false;
        for (JsonElement methodElement : array(behaviorRule, "sourceMethods")) {
            if (!methodElement.isJsonObject()) continue;
            JsonObject source = methodElement.getAsJsonObject();
            if (owner.equals(string(source, "owner", null))
                    && method.equals(string(source, "method", null))
                    && descriptor.equals(string(source, "descriptor", null)))
                return bool(source, "trivialNoOp", false);
        }
        return false;
    }

    private static JsonObject read(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static boolean validInput(JsonObject root, String sourceHash) {
        return number(root, "schemaVersion", -1) == 1 && sourceHash.equals(string(root, "sourceSha256", ""));
    }

    private static Map<String,JsonObject> index(JsonObject root) {
        Map<String,JsonObject> output = new LinkedHashMap<>();
        for (JsonElement element : array(root, "rules")) {
            if (!element.isJsonObject()) continue;
            JsonObject rule = element.getAsJsonObject();
            String key = key(string(rule, "legacyRegistryName", null), string(rule, "sourceClass", null));
            if (key != null) output.putIfAbsent(key, rule);
        }
        return output;
    }

    private static JsonArray array(JsonObject root, String name) {
        JsonElement value = root.get(name);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    private static JsonArray deduplicate(JsonArray source) {
        JsonArray output = new JsonArray();
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        for (JsonElement element : source) if (element.isJsonPrimitive() && seen.add(element.getAsString())) output.add(element.getAsString());
        return output;
    }

    private static boolean bool(JsonObject root, String name, boolean fallback) {
        JsonElement value = root.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsBoolean() : fallback;
    }

    private static int number(JsonObject root, String name, int fallback) {
        JsonElement value = root.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsInt() : fallback;
    }

    private static String string(JsonObject root, String name, String fallback) {
        JsonElement value = root.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    private static String key(String registryName, String sourceClass) {
        return registryName == null || sourceClass == null ? null : registryName + '\u0000' + sourceClass;
    }

    private static void copy(JsonObject source, JsonObject target, String name) {
        JsonElement value = source.get(name);
        if (value != null) target.add(name, value.deepCopy());
    }
}
