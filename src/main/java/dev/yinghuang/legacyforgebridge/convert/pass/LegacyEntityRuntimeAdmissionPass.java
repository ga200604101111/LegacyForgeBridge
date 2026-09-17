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

/** Joins watcher, behavior, construction, and narrowly proven constant-override IR into the first generated plain Entity runtime family. */
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
        Map<String,JsonObject> constantOverrideByKey = new LinkedHashMap<>();
        Path constantPath = context.stagingDir().resolve(LegacyEntityConstantOverridePass.OUTPUT);
        if (Files.isRegularFile(constantPath)) {
            JsonObject constants = read(constantPath);
            if (validInput(constants, context.sourceHash())) constantOverrideByKey.putAll(index(constants));
        }

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("postInitWatcherMutationGateWired", true);
        root.addProperty("constantBehaviorOverrideAdmissionWired", true);
        root.addProperty("typedConstantBehaviorOverrideAdmissionWired", true);
        root.addProperty("runtimeImplementationWired", false);

        JsonArray rules = new JsonArray();
        int admittedCount = 0, admittedConstantOverrides = 0;
        for (JsonElement element : array(runtime, "rules")) {
            if (!element.isJsonObject()) continue;
            JsonObject runtimeRule = element.getAsJsonObject();
            String registryName = string(runtimeRule, "legacyRegistryName", null);
            String sourceClass = string(runtimeRule, "sourceClass", null);
            String key = key(registryName, sourceClass);
            if (key == null) continue;

            JsonObject behaviorRule = behaviorByKey.get(key);
            JsonObject constructionRule = constructionByKey.get(key);
            JsonObject constantRule = constantOverrideByKey.get(key);
            JsonArray blockers = new JsonArray();

            if (!bool(runtimeRule, "synchedDataMappingComplete", false)) blockers.add("synched-data-mapping-incomplete");
            if (!bool(runtimeRule, "sourceWideDataWatcherCallClosureComplete", false)) blockers.add("source-wide-datawatcher-call-closure-incomplete");
            if (!bool(runtimeRule, "postInitSourceDataWatcherMutationFree", false)
                    || number(runtimeRule, "sourceOwnedDataWatcherWriteCount", -1) != 0)
                blockers.add("post-init-datawatcher-writes-require-runtime-sync");
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
                    if (LegacyEntityConstantOverridePass.supported(kind) && mappedConstantOverride(constantRule, callback, kind)) continue;
                    blockers.add(LegacyEntityConstantOverridePass.supported(kind)
                            ? "constant-override-proof-missing:" + kind : "unsupported-callback:" + kind);
                }
                for (JsonElement methodElement : array(behaviorRule, "sourceMethods")) {
                    if (!methodElement.isJsonObject()) { blockers.add("malformed-source-method"); continue; }
                    JsonObject method = methodElement.getAsJsonObject();
                    String kind = string(method, "callbackKind", null);
                    if ("ENTITY_INIT".equals(kind)) continue;
                    if (("READ_NBT".equals(kind) || "WRITE_NBT".equals(kind)) && bool(method, "trivialNoOp", false)) continue;
                    if (LegacyEntityConstantOverridePass.supported(kind) && mappedConstantOverride(constantRule, method, kind)) continue;
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
            copy(runtimeRule, rule, "id"); copy(runtimeRule, rule, "legacyRegistryName"); copy(runtimeRule, rule, "sourceClass");
            copy(runtimeRule, rule, "legacyNumericId"); copy(runtimeRule, rule, "trackingRange"); copy(runtimeRule, rule, "updateFrequency");
            copy(runtimeRule, rule, "velocityUpdates"); copy(runtimeRule, rule, "sourceOwnedDataWatcherReadCount");
            copy(runtimeRule, rule, "sourceOwnedDataWatcherWriteCount"); copy(runtimeRule, rule, "postInitSourceDataWatcherMutationFree");
            rule.addProperty("family", FAMILY_PLAIN_SYNCHED_DATA_ONLY);
            rule.addProperty("admitted", admitted);
            rule.addProperty("runtimeImplementationWired", false);
            rule.add("blockers", blockers);
            if (runtimeRule.has("synchedDataEntries")) rule.add("synchedDataEntries", runtimeRule.get("synchedDataEntries").deepCopy());
            JsonArray constantOverrides = constantRule == null ? new JsonArray() : array(constantRule, "constantOverrides").deepCopy();
            rule.add("constantBehaviorOverrides", constantOverrides);
            rule.addProperty("constantBehaviorOverrideCount", constantOverrides.size());
            if (constructionRule != null && bool(constructionRule, "sizeProofComplete", false)) {
                copy(constructionRule, rule, "width"); copy(constructionRule, rule, "height");
            }
            rules.add(rule);
            if (admitted) { admittedCount++; admittedConstantOverrides += constantOverrides.size(); }
        }
        root.add("rules", rules);
        root.addProperty("evaluatedRegistrations", rules.size());
        root.addProperty("admittedRegistrations", admittedCount);
        root.addProperty("admittedConstantBehaviorOverrides", admittedConstantOverrides);
        root.addProperty("blockedRegistrations", rules.size() - admittedCount);

        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        if (admittedCount > 0) context.diagnostics().info("LFB-CONVERT-ENTITY-ADMISSION-0001", SupportLevel.RUNTIME_BRIDGE,
                "Admitted " + admittedCount + " write-free plain Entity registration(s), including "
                        + admittedConstantOverrides + " exact typed constant base-behavior override(s); post-spawn watcher mutation remains outside this family.");
        if (admittedCount < rules.size()) context.diagnostics().warning("LFB-CONVERT-ENTITY-ADMISSION-0002", SupportLevel.RUNTIME_BRIDGE,
                "Blocked " + (rules.size() - admittedCount) + " entity registration(s) because behavior/construction/watcher/velocity/constant-override proof gates remain incomplete or unsupported.");
    }

    private static boolean mappedConstantOverride(JsonObject constantRule, JsonObject sourceMethod, String sourceKind) {
        if (constantRule == null || !LegacyEntityConstantOverridePass.supported(sourceKind)) return false;
        String owner = string(sourceMethod, "owner", null), method = string(sourceMethod, "method", null), descriptor = string(sourceMethod, "descriptor", null);
        if (owner == null || method == null || descriptor == null) return false;
        String targetMethod = LegacyEntityConstantOverridePass.targetMethod(sourceKind);
        String targetDescriptor = LegacyEntityConstantOverridePass.targetDescriptor(sourceKind);
        String mappingSemantics = LegacyEntityConstantOverridePass.mappingSemantics(sourceKind);
        for (JsonElement element : array(constantRule, "constantOverrides")) {
            if (!element.isJsonObject()) continue;
            JsonObject value = element.getAsJsonObject();
            if (sourceKind.equals(string(value, "sourceKind", null))
                    && owner.equals(string(value, "sourceOwner", null))
                    && method.equals(string(value, "sourceMethod", null))
                    && descriptor.equals(string(value, "sourceDescriptor", null))
                    && targetMethod.equals(string(value, "targetMethod", null))
                    && targetDescriptor.equals(string(value, "targetDescriptor", null))
                    && mappingSemantics.equals(string(value, "mappingSemantics", null))
                    && bool(value, "sourceConstantProofComplete", false)
                    && bool(value, "runtimeCodegenReady", false)
                    && constantValueValid(value, sourceKind)) return true;
        }
        return false;
    }

    private static boolean constantValueValid(JsonObject value, String sourceKind) {
        String expected = LegacyEntityConstantOverridePass.constantKind(sourceKind);
        String declared = string(value, "constantKind", null);
        if ("float".equals(expected)) {
            if (!"float".equals(declared) || !value.has("constantFloat") || !value.get("constantFloat").isJsonPrimitive()
                    || !value.get("constantFloat").getAsJsonPrimitive().isNumber()) return false;
            try { return Float.isFinite(value.get("constantFloat").getAsFloat()); }
            catch (RuntimeException invalid) { return false; }
        }
        if ("boolean".equals(expected)) {
            if (declared != null && !"boolean".equals(declared)) return false;
            return value.has("constantBoolean") && value.get("constantBoolean").isJsonPrimitive()
                    && value.get("constantBoolean").getAsJsonPrimitive().isBoolean();
        }
        return false;
    }

    private static boolean trivialNoOpMethod(JsonObject behaviorRule, JsonObject callback) {
        String owner = string(callback, "owner", null), method = string(callback, "method", null), descriptor = string(callback, "descriptor", null);
        if (owner == null || method == null || descriptor == null) return false;
        for (JsonElement methodElement : array(behaviorRule, "sourceMethods")) {
            if (!methodElement.isJsonObject()) continue;
            JsonObject source = methodElement.getAsJsonObject();
            if (owner.equals(string(source, "owner", null)) && method.equals(string(source, "method", null))
                    && descriptor.equals(string(source, "descriptor", null))) return bool(source, "trivialNoOp", false);
        }
        return false;
    }

    private static JsonObject read(Path path) throws Exception { return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject(); }
    private static boolean validInput(JsonObject root, String sourceHash) { return number(root, "schemaVersion", -1) == 1 && sourceHash.equals(string(root, "sourceSha256", "")); }
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
    private static JsonArray array(JsonObject root, String name) { JsonElement value = root.get(name); return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray(); }
    private static JsonArray deduplicate(JsonArray source) {
        JsonArray output = new JsonArray(); java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        for (JsonElement element : source) if (element.isJsonPrimitive() && seen.add(element.getAsString())) output.add(element.getAsString());
        return output;
    }
    private static boolean bool(JsonObject root, String name, boolean fallback) { JsonElement value = root.get(name); return value != null && value.isJsonPrimitive() ? value.getAsBoolean() : fallback; }
    private static int number(JsonObject root, String name, int fallback) { JsonElement value = root.get(name); return value != null && value.isJsonPrimitive() ? value.getAsInt() : fallback; }
    private static String string(JsonObject root, String name, String fallback) { JsonElement value = root.get(name); return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback; }
    private static String key(String registryName, String sourceClass) { return registryName == null || sourceClass == null ? null : registryName + '\u0000' + sourceClass; }
    private static void copy(JsonObject source, JsonObject target, String name) { JsonElement value = source.get(name); if (value != null) target.add(name, value.deepCopy()); }
}
