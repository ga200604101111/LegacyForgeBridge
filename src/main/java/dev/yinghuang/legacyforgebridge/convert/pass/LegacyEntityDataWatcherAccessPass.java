package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyEntityDataWatcherAccessAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Adds a fail-closed direct source-lineage read/write inventory to proven entity watcher schemas. */
public final class LegacyEntityDataWatcherAccessPass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/entity-datawatcher-access.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override public String id() { return "legacy-entity-datawatcher-access-proof"; }

    @Override
    public void apply(ConversionContext context) throws Exception {
        Path definitionsPath = context.stagingDir().resolve(LegacyEntityDataWatcherPass.OUTPUT);
        if (!Files.isRegularFile(definitionsPath)) return;
        JsonObject definitions = JsonParser.parseString(Files.readString(definitionsPath, StandardCharsets.UTF_8)).getAsJsonObject();
        if (definitions.get("schemaVersion").getAsInt() != 1) return;

        LegacyEntityDataWatcherAccessAnalyzer.Analysis analysis = new LegacyEntityDataWatcherAccessAnalyzer().analyze(context.sourceJar());
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("runtimeImplementationWired", false);

        Map<String,JsonObject> definitionsByKey = new LinkedHashMap<>();
        JsonArray definitionRules = definitions.getAsJsonArray("rules");
        if (definitionRules != null) for (var element : definitionRules) if (element.isJsonObject()) {
            JsonObject rule = element.getAsJsonObject();
            definitionsByKey.put(key(rule.get("legacyRegistryName").getAsString(), rule.get("sourceClass").getAsString()), rule);
        }

        JsonArray rules = new JsonArray();
        for (LegacyEntityDataWatcherAccessAnalyzer.Rule rule : analysis.rules()) {
            JsonObject definition = definitionsByKey.get(key(rule.registryName(), rule.sourceClass()));
            if (definition == null) continue;
            JsonObject value = new JsonObject();
            value.addProperty("id", definition.get("id").getAsString());
            value.addProperty("legacyRegistryName", rule.registryName());
            value.addProperty("sourceClass", rule.sourceClass());
            value.addProperty("sourceLineageAccessSurfaceComplete", true);
            value.addProperty("reachableHelperClosureComplete", false);
            value.addProperty("runtimeImplementationWired", false);
            JsonArray accesses = new JsonArray();
            for (LegacyEntityDataWatcherAccessAnalyzer.Access access : rule.accesses()) {
                JsonObject item = new JsonObject();
                item.addProperty("index", access.index());
                item.addProperty("operation", access.operation());
                item.addProperty("valueKind", access.valueKind());
                item.addProperty("sourceOwner", access.sourceOwner());
                item.addProperty("sourceMethod", access.sourceMethod());
                item.addProperty("sourceDescriptor", access.sourceDescriptor());
                accesses.add(item);
            }
            value.add("accesses", accesses);
            value.addProperty("accessCount", accesses.size());
            rules.add(value);
        }
        root.add("rules", rules);

        JsonArray skipped = new JsonArray();
        for (LegacyEntityDataWatcherAccessAnalyzer.Skipped item : analysis.skipped()) {
            JsonObject value = new JsonObject();
            if (item.registryName() != null) value.addProperty("legacyRegistryName", item.registryName());
            if (item.sourceClass() != null) value.addProperty("sourceClass", item.sourceClass());
            value.addProperty("reason", item.reason());
            skipped.add(value);
            context.diagnostics().warning("LFB-CONVERT-ENTITY-ACCESS-0002", SupportLevel.RUNTIME_BRIDGE,
                    "Entity DataWatcher direct source-lineage access proof remains closed for "
                            + (item.registryName() == null ? "<unknown>" : item.registryName()) + ": " + item.reason());
        }
        root.add("skipped", skipped);
        root.addProperty("proofCompleteLineageAccessSurfaces", rules.size());
        root.addProperty("skippedLineageAccessSurfaces", skipped.size());

        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
        for (String diagnostic : analysis.diagnostics())
            context.diagnostics().warning("LFB-CONVERT-ENTITY-ACCESS-0003", SupportLevel.MANUAL_REQUIRED, diagnostic);
        if (!rules.isEmpty()) context.diagnostics().info("LFB-CONVERT-ENTITY-ACCESS-0001", SupportLevel.RUNTIME_BRIDGE,
                "Proved direct source-lineage primitive/string DataWatcher read/write surfaces for " + rules.size()
                        + " entity registration(s); reachable-helper closure and SynchedEntityData runtime generation remain intentionally closed.");
    }

    private static String key(String registryName, String sourceClass) { return registryName + '\u0000' + sourceClass; }
}
