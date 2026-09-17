package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyEntityBehaviorSurfaceAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Materializes a non-executing source-owned Entity callback/method inventory for runtime planning. */
public final class LegacyEntityBehaviorSurfacePass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/entity-behavior-surface.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override public String id() { return "legacy-entity-behavior-surface"; }

    @Override
    public void apply(ConversionContext context) throws Exception {
        Path definitionPath = context.stagingDir().resolve(LegacyEntityDataWatcherPass.OUTPUT);
        if (!Files.isRegularFile(definitionPath)) return;
        JsonObject definitions = JsonParser.parseString(Files.readString(definitionPath, StandardCharsets.UTF_8)).getAsJsonObject();
        if (!definitions.has("schemaVersion") || definitions.get("schemaVersion").getAsInt() != 1) return;
        if (!definitions.has("sourceSha256") || !context.sourceHash().equals(definitions.get("sourceSha256").getAsString())) return;

        Map<String,JsonObject> definitionByKey = new LinkedHashMap<>();
        JsonArray definitionRules = definitions.has("rules") && definitions.get("rules").isJsonArray()
                ? definitions.getAsJsonArray("rules") : new JsonArray();
        for (var element : definitionRules) if (element.isJsonObject()) {
            JsonObject rule = element.getAsJsonObject();
            if (!rule.has("legacyRegistryName") || !rule.has("sourceClass")) continue;
            definitionByKey.put(key(rule.get("legacyRegistryName").getAsString(), rule.get("sourceClass").getAsString()), rule);
        }

        LegacyEntityBehaviorSurfaceAnalyzer.Analysis analysis = new LegacyEntityBehaviorSurfaceAnalyzer().analyze(context.sourceJar());
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("behaviorRuntimeWired", false);

        JsonArray rules = new JsonArray();
        int totalCallbacks = 0;
        int totalMethods = 0;
        int totalUnclassified = 0;
        for (LegacyEntityBehaviorSurfaceAnalyzer.EntitySurface surface : analysis.entities()) {
            JsonObject definition = definitionByKey.get(key(surface.registryName(), surface.sourceClass()));
            if (definition == null) continue;
            JsonObject value = new JsonObject();
            if (definition.has("id")) value.addProperty("id", definition.get("id").getAsString());
            value.addProperty("legacyRegistryName", surface.registryName());
            value.addProperty("sourceClass", surface.sourceClass());
            if (surface.externalBaseClass() != null) value.addProperty("externalBaseClass", surface.externalBaseClass());
            value.addProperty("sourceOwnedBehaviorInventoryComplete", true);
            value.addProperty("runtimeBehaviorReady", false);
            value.addProperty("behaviorRuntimeWired", false);

            JsonArray lineage = new JsonArray();
            surface.sourceLineage().forEach(lineage::add);
            value.add("sourceLineage", lineage);

            JsonArray callbacks = new JsonArray();
            for (LegacyEntityBehaviorSurfaceAnalyzer.Callback callback : surface.callbacks()) {
                JsonObject item = new JsonObject();
                item.addProperty("kind", callback.kind().name());
                item.addProperty("owner", callback.owner());
                item.addProperty("method", callback.method());
                item.addProperty("descriptor", callback.descriptor());
                callbacks.add(item);
            }
            value.add("callbacks", callbacks);
            value.addProperty("callbackCount", callbacks.size());

            JsonArray methods = new JsonArray();
            int unclassified = 0;
            for (LegacyEntityBehaviorSurfaceAnalyzer.SourceMethod method : surface.sourceMethods()) {
                JsonObject item = new JsonObject();
                item.addProperty("owner", method.owner());
                item.addProperty("method", method.method());
                item.addProperty("descriptor", method.descriptor());
                item.addProperty("access", method.access());
                if (method.callbackKind() != null) item.addProperty("callbackKind", method.callbackKind().name());
                else unclassified++;
                methods.add(item);
            }
            value.add("sourceMethods", methods);
            value.addProperty("sourceMethodCount", methods.size());
            value.addProperty("unclassifiedSourceMethodCount", unclassified);
            rules.add(value);
            totalCallbacks += callbacks.size();
            totalMethods += methods.size();
            totalUnclassified += unclassified;
        }
        root.add("rules", rules);
        root.addProperty("entitySurfaceCount", rules.size());
        root.addProperty("callbackCount", totalCallbacks);
        root.addProperty("sourceMethodCount", totalMethods);
        root.addProperty("unclassifiedSourceMethodCount", totalUnclassified);

        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        for (String diagnostic : analysis.diagnostics())
            context.diagnostics().warning("LFB-CONVERT-ENTITY-BEHAVIOR-0002", SupportLevel.MANUAL_REQUIRED, diagnostic);
        if (!rules.isEmpty()) context.diagnostics().info("LFB-CONVERT-ENTITY-BEHAVIOR-0001", SupportLevel.RUNTIME_BRIDGE,
                "Inventoried source-owned behavior surfaces for " + rules.size() + " entity registration(s): "
                        + totalCallbacks + " classified callback(s), " + totalUnclassified
                        + " unclassified source instance method(s); behavior runtime remains intentionally unwired.");
    }

    private static String key(String registryName, String sourceClass) {
        return registryName + '\u0000' + sourceClass;
    }
}
