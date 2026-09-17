package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyEntityConstantOverrideAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Proves narrowly one-to-one legacy Entity constant boolean override mappings. */
public final class LegacyEntityConstantOverridePass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/entity-constant-overrides.json";
    public static final String SOURCE_KIND_CAN_PUSH = "CAN_PUSH";
    public static final String SOURCE_KIND_RENDER_DISTANCE = "RENDER_DISTANCE";
    public static final String TARGET_METHOD_IS_PUSHABLE = "isPushable";
    public static final String TARGET_METHOD_SHOULD_RENDER_AT_SQR_DISTANCE = "shouldRenderAtSqrDistance";
    public static final String TARGET_DESCRIPTOR_BOOLEAN = "()Z";
    public static final String TARGET_DESCRIPTOR_RENDER_DISTANCE = "(D)Z";
    public static final String MAPPING_PUSHABILITY_BOOLEAN_IDENTITY = "PUSHABILITY_BOOLEAN_IDENTITY";
    public static final String MAPPING_RENDER_DISTANCE_CONSTANT_BOOLEAN_IDENTITY = "RENDER_DISTANCE_CONSTANT_BOOLEAN_IDENTITY";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override public String id() { return "legacy-entity-constant-overrides"; }

    @Override
    public void apply(ConversionContext context) throws Exception {
        Path behaviorPath = context.stagingDir().resolve(LegacyEntityBehaviorSurfacePass.OUTPUT);
        if (!Files.isRegularFile(behaviorPath)) return;
        JsonObject behavior = JsonParser.parseString(Files.readString(behaviorPath, StandardCharsets.UTF_8)).getAsJsonObject();
        if (integer(behavior, "schemaVersion", -1) != 1
                || !context.sourceHash().equals(string(behavior, "sourceSha256", ""))) return;

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("constantOverrideProofWired", true);
        root.addProperty("runtimeCodegenWired", false);
        JsonArray supported = new JsonArray();
        supported.add(SOURCE_KIND_CAN_PUSH);
        supported.add(SOURCE_KIND_RENDER_DISTANCE);
        root.add("supportedOverrideKinds", supported);
        JsonArray rules = new JsonArray();
        int proven = 0, blocked = 0;
        LegacyEntityConstantOverrideAnalyzer analyzer = new LegacyEntityConstantOverrideAnalyzer();

        for (JsonElement element : array(behavior, "rules")) {
            if (!element.isJsonObject()) continue;
            JsonObject behaviorRule = element.getAsJsonObject();
            JsonArray mapped = new JsonArray(), rejected = new JsonArray();
            for (JsonElement callbackElement : array(behaviorRule, "callbacks")) {
                if (!callbackElement.isJsonObject()) continue;
                JsonObject callback = callbackElement.getAsJsonObject();
                String sourceKind = string(callback, "kind", "");
                if (!supported(sourceKind)) continue;
                String owner = string(callback, "owner", null), method = string(callback, "method", null), descriptor = string(callback, "descriptor", null);
                var proof = analyzer.proveBoolean(context.sourceJar(), owner, method, descriptor);
                JsonObject value = new JsonObject();
                value.addProperty("sourceKind", sourceKind);
                if (owner != null) value.addProperty("sourceOwner", owner);
                if (method != null) value.addProperty("sourceMethod", method);
                if (descriptor != null) value.addProperty("sourceDescriptor", descriptor);
                value.addProperty("targetOwner", "net/minecraft/world/entity/Entity");
                value.addProperty("targetMethod", targetMethod(sourceKind));
                value.addProperty("targetDescriptor", targetDescriptor(sourceKind));
                value.addProperty("mappingSemantics", mappingSemantics(sourceKind));
                value.addProperty("sourceConstantProofComplete", proof.proven());
                value.addProperty("proofReason", proof.reason());
                if (proof.proven()) {
                    value.addProperty("constantBoolean", proof.value());
                    value.addProperty("runtimeCodegenReady", true);
                    mapped.add(value); proven++;
                } else {
                    value.addProperty("runtimeCodegenReady", false);
                    rejected.add(value); blocked++;
                }
            }
            if (mapped.isEmpty() && rejected.isEmpty()) continue;
            JsonObject rule = new JsonObject();
            copy(behaviorRule, rule, "id"); copy(behaviorRule, rule, "legacyRegistryName"); copy(behaviorRule, rule, "sourceClass");
            rule.add("constantOverrides", mapped);
            rule.add("blockedOverrides", rejected);
            rule.addProperty("constantOverrideProofComplete", rejected.isEmpty());
            rule.addProperty("provenConstantOverrideCount", mapped.size());
            rule.addProperty("blockedConstantOverrideCount", rejected.size());
            rules.add(rule);
        }

        root.add("rules", rules);
        root.addProperty("entityRulesWithConstantOverrides", rules.size());
        root.addProperty("provenConstantOverrideCount", proven);
        root.addProperty("blockedConstantOverrideCount", blocked);
        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        if (proven > 0) context.diagnostics().info("LFB-CONVERT-ENTITY-CONST-0001", SupportLevel.RUNTIME_BRIDGE,
                "Proved " + proven + " exact constant legacy Entity boolean override(s) for one-to-one modern codegen (pushability/render distance)." );
        if (blocked > 0) context.diagnostics().warning("LFB-CONVERT-ENTITY-CONST-0002", SupportLevel.RUNTIME_BRIDGE,
                "Blocked " + blocked + " supported legacy constant Entity override(s) because their bytecode was not an exact ICONST_0/1; IRETURN body.");
    }

    public static boolean supported(String sourceKind) {
        return SOURCE_KIND_CAN_PUSH.equals(sourceKind) || SOURCE_KIND_RENDER_DISTANCE.equals(sourceKind);
    }
    public static String targetMethod(String sourceKind) {
        return SOURCE_KIND_CAN_PUSH.equals(sourceKind) ? TARGET_METHOD_IS_PUSHABLE
                : SOURCE_KIND_RENDER_DISTANCE.equals(sourceKind) ? TARGET_METHOD_SHOULD_RENDER_AT_SQR_DISTANCE : null;
    }
    public static String targetDescriptor(String sourceKind) {
        return SOURCE_KIND_CAN_PUSH.equals(sourceKind) ? TARGET_DESCRIPTOR_BOOLEAN
                : SOURCE_KIND_RENDER_DISTANCE.equals(sourceKind) ? TARGET_DESCRIPTOR_RENDER_DISTANCE : null;
    }
    public static String mappingSemantics(String sourceKind) {
        return SOURCE_KIND_CAN_PUSH.equals(sourceKind) ? MAPPING_PUSHABILITY_BOOLEAN_IDENTITY
                : SOURCE_KIND_RENDER_DISTANCE.equals(sourceKind) ? MAPPING_RENDER_DISTANCE_CONSTANT_BOOLEAN_IDENTITY : null;
    }
    private static JsonArray array(JsonObject root, String name) {
        JsonElement value = root.get(name); return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }
    private static int integer(JsonObject root, String name, int fallback) {
        JsonElement value = root.get(name); return value != null && value.isJsonPrimitive() ? value.getAsInt() : fallback;
    }
    private static String string(JsonObject root, String name, String fallback) {
        JsonElement value = root.get(name); return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }
    private static void copy(JsonObject source, JsonObject target, String name) {
        JsonElement value = source.get(name); if (value != null) target.add(name, value.deepCopy());
    }
}
