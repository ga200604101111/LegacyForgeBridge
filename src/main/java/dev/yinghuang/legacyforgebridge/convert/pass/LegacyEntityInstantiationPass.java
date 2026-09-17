package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.yinghuang.legacyforgebridge.convert.LegacyEntityInstantiationAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Materializes source-wide registered-Entity construction and World spawn usage for migration planning. */
public final class LegacyEntityInstantiationPass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/entity-instantiation-surface.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override public String id() { return "legacy-entity-instantiation-surface"; }

    @Override
    public void apply(ConversionContext context) throws Exception {
        LegacyEntityInstantiationAnalyzer.Analysis analysis =
                new LegacyEntityInstantiationAnalyzer().analyze(context.sourceJar());
        if (analysis.registrations().isEmpty() && analysis.worldSpawns().isEmpty()) return;

        Map<String,JsonObject> byClass = new LinkedHashMap<>();
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("sourceInstantiationInventoryComplete", true);
        root.addProperty("spawnRewriteWired", false);

        JsonArray rules = new JsonArray();
        for (LegacyEntityInstantiationAnalyzer.Registration registration : analysis.registrations()) {
            JsonObject value = new JsonObject();
            if (registration.registryName() != null) value.addProperty("legacyRegistryName", registration.registryName());
            value.addProperty("sourceClass", registration.sourceClass());
            value.addProperty("directConstructionCount", 0);
            value.addProperty("provenWorldSpawnCount", 0);
            value.add("directConstructions", new JsonArray());
            value.add("provenWorldSpawns", new JsonArray());
            byClass.put(registration.sourceClass(), value);
            rules.add(value);
        }

        for (LegacyEntityInstantiationAnalyzer.Construction construction : analysis.constructions()) {
            JsonObject rule = byClass.get(construction.sourceClass());
            if (rule == null) continue;
            JsonObject value = new JsonObject();
            value.addProperty("constructorDescriptor", construction.constructorDescriptor());
            value.addProperty("sourceOwner", construction.sourceOwner());
            value.addProperty("sourceMethod", construction.sourceMethod());
            value.addProperty("sourceDescriptor", construction.sourceDescriptor());
            value.addProperty("instructionIndex", construction.instructionIndex());
            rule.getAsJsonArray("directConstructions").add(value);
            rule.addProperty("directConstructionCount", rule.get("directConstructionCount").getAsInt() + 1);
        }

        JsonArray unresolved = new JsonArray();
        for (LegacyEntityInstantiationAnalyzer.WorldSpawn spawn : analysis.worldSpawns()) {
            JsonObject value = new JsonObject();
            if (spawn.sourceClass() != null) value.addProperty("sourceClass", spawn.sourceClass());
            value.addProperty("sourceClassProven", spawn.sourceClassProven());
            value.addProperty("sourceOwner", spawn.sourceOwner());
            value.addProperty("sourceMethod", spawn.sourceMethod());
            value.addProperty("sourceDescriptor", spawn.sourceDescriptor());
            value.addProperty("instructionIndex", spawn.instructionIndex());
            if (spawn.sourceClassProven()) {
                JsonObject rule = byClass.get(spawn.sourceClass());
                if (rule != null) {
                    rule.getAsJsonArray("provenWorldSpawns").add(value);
                    rule.addProperty("provenWorldSpawnCount", rule.get("provenWorldSpawnCount").getAsInt() + 1);
                }
            } else unresolved.add(value);
        }

        Path runtimePath = context.stagingDir().resolve(LegacyPlainEntityRuntimePass.OUTPUT);
        Map<String,String> runtimeIds = new LinkedHashMap<>();
        if (Files.isRegularFile(runtimePath)) {
            com.google.gson.JsonObject runtime = com.google.gson.JsonParser.parseString(
                    Files.readString(runtimePath, StandardCharsets.UTF_8)).getAsJsonObject();
            if (runtime.has("schemaVersion") && runtime.get("schemaVersion").getAsInt() == 1
                    && runtime.has("sourceSha256") && context.sourceHash().equals(runtime.get("sourceSha256").getAsString())) {
                JsonArray runtimeRules = runtime.has("rules") && runtime.get("rules").isJsonArray()
                        ? runtime.getAsJsonArray("rules") : new JsonArray();
                for (var element : runtimeRules) if (element.isJsonObject()) {
                    JsonObject rule = element.getAsJsonObject();
                    if (rule.has("sourceClass") && rule.has("id"))
                        runtimeIds.put(rule.get("sourceClass").getAsString(), rule.get("id").getAsString());
                }
            }
        }
        for (Map.Entry<String,JsonObject> entry : byClass.entrySet()) {
            String runtimeId = runtimeIds.get(entry.getKey());
            entry.getValue().addProperty("modernRuntimeReplacementAvailable", runtimeId != null);
            if (runtimeId != null) entry.getValue().addProperty("modernRuntimeId", runtimeId);
            boolean needsRewrite = entry.getValue().get("directConstructionCount").getAsInt() > 0
                    || entry.getValue().get("provenWorldSpawnCount").getAsInt() > 0;
            entry.getValue().addProperty("sourceInstantiationRewriteRequired", needsRewrite);
            entry.getValue().addProperty("sourceInstantiationRewriteWired", false);
        }

        root.add("rules", rules);
        root.add("unresolvedWorldSpawns", unresolved);
        root.addProperty("registeredEntityCount", rules.size());
        root.addProperty("directConstructionCount", analysis.constructions().size());
        root.addProperty("worldSpawnCallCount", analysis.worldSpawns().size());
        root.addProperty("unresolvedWorldSpawnArgumentCount", unresolved.size());
        root.addProperty("runtimeReplacementEntityCount", runtimeIds.size());
        root.addProperty("spawnMigrationComplete", analysis.constructions().isEmpty()
                && analysis.worldSpawns().isEmpty());

        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        for (String diagnostic : analysis.diagnostics())
            context.diagnostics().warning("LFB-CONVERT-ENTITY-INSTANTIATE-0002", SupportLevel.MANUAL_REQUIRED, diagnostic);
        if (!rules.isEmpty()) context.diagnostics().info("LFB-CONVERT-ENTITY-INSTANTIATE-0001", SupportLevel.RUNTIME_BRIDGE,
                "Inventoried registered Entity construction/spawn surface: registrations=" + rules.size()
                        + ", directConstructions=" + analysis.constructions().size()
                        + ", worldSpawnCalls=" + analysis.worldSpawns().size()
                        + ", unresolvedSpawnArguments=" + unresolved.size()
                        + ". Spawn-site rewriting remains intentionally unwired.");
    }
}
