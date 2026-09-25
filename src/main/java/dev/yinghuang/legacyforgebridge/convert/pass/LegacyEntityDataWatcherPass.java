package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.yinghuang.legacyforgebridge.convert.LegacyEntityDataWatcherAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Materializes proof-complete legacy EntityRegistry registration metadata and source-owned
 * DataWatcher defaults for the later EntityType/SynchedEntityData runtime stage.
 */
public final class LegacyEntityDataWatcherPass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/entity-datawatcher-rules.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override public String id() { return "legacy-entity-datawatcher-proof"; }

    @Override
    public void apply(ConversionContext context) throws Exception {
        LegacyEntityDataWatcherAnalyzer.Analysis analysis = new LegacyEntityDataWatcherAnalyzer().analyze(context.sourceJar());
        if (analysis.rules().isEmpty() && analysis.skipped().isEmpty()) return;

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("namespace", context.metadata().fabricId());
        root.addProperty("runtimeImplementationWired", false);

        JsonArray rules = new JsonArray();
        Set<String> modernIds = new HashSet<>();
        int collisions = 0;
        for (LegacyEntityDataWatcherAnalyzer.Rule rule : analysis.rules()) {
            String id = context.metadata().fabricId() + ":" + modernPath(rule.registryName());
            if (!modernIds.add(id)) {
                collisions++;
                context.diagnostics().warning("LFB-CONVERT-ENTITY-0004", SupportLevel.MANUAL_REQUIRED,
                        "Multiple legacy entity names normalize to modern entity id " + id + "; runtime admission remains closed.");
                continue;
            }
            JsonObject value = new JsonObject();
            value.addProperty("id", id);
            value.addProperty("legacyRegistryName", rule.registryName());
            value.addProperty("sourceClass", rule.sourceClass());
            value.addProperty("legacyNumericId", rule.numericId());
            value.addProperty("trackingRange", rule.trackingRange());
            value.addProperty("updateFrequency", rule.updateFrequency());
            value.addProperty("velocityUpdates", rule.velocityUpdates());
            value.addProperty("sourceDataWatcherDefinitionComplete", true);
            value.addProperty("runtimeImplementationWired", false);
            JsonArray entries = new JsonArray();
            for (LegacyEntityDataWatcherAnalyzer.Entry entry : rule.entries()) {
                JsonObject watched = new JsonObject();
                watched.addProperty("index", entry.index());
                watched.addProperty("valueKind", entry.valueKind());
                if (entry.defaultValue() instanceof Number number) watched.addProperty("defaultValue", number);
                else if (entry.defaultValue() instanceof Boolean bool) watched.addProperty("defaultValue", bool);
                else watched.addProperty("defaultValue", String.valueOf(entry.defaultValue()));
                watched.addProperty("declaredBy", entry.declaredBy());
                entries.add(watched);
            }
            value.add("dataWatcherEntries", entries);
            rules.add(value);
        }
        root.add("rules", rules);

        JsonArray skipped = new JsonArray();
        for (LegacyEntityDataWatcherAnalyzer.Skipped item : analysis.skipped()) {
            JsonObject value = new JsonObject();
            if (item.registryName() != null) value.addProperty("legacyRegistryName", item.registryName());
            if (item.sourceClass() != null) value.addProperty("sourceClass", item.sourceClass());
            value.addProperty("reason", item.reason());
            skipped.add(value);
            context.diagnostics().warning("LFB-CONVERT-ENTITY-0002", SupportLevel.RUNTIME_BRIDGE,
                    "Entity/DataWatcher proof remains closed for " + (item.registryName() == null ? "<unknown>" : item.registryName())
                            + ": " + item.reason());
        }
        root.add("skipped", skipped);
        root.addProperty("proofCompleteRegistrations", rules.size());
        root.addProperty("skippedRegistrations", skipped.size() + collisions);

        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        for (String diagnostic : analysis.diagnostics())
            context.diagnostics().warning("LFB-CONVERT-ENTITY-0003", SupportLevel.MANUAL_REQUIRED, diagnostic);
        if (!rules.isEmpty()) context.diagnostics().info("LFB-CONVERT-ENTITY-0001", SupportLevel.RUNTIME_BRIDGE,
                "Proved legacy entity registration/DataWatcher schemas for " + rules.size()
                        + " registration(s). EntityType behavior/render/runtime generation is intentionally not enabled by this proof-only stage.");
    }

    private static String modernPath(String legacyName) {
        String path = legacyName == null ? "" : legacyName.trim().toLowerCase(Locale.ROOT)
                .replace('\\', '/')
                .replaceAll("[^a-z0-9/._-]", "_")
                .replaceAll("_+", "_");
        while (path.startsWith("/")) path = path.substring(1);
        return path.isBlank() ? "legacy_entity" : path;
    }
}
