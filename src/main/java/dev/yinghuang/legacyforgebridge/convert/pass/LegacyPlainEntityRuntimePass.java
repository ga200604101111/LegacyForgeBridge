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

/** Promotes only proof-complete plain Entity runtime candidates into executable runtime rules. */
public final class LegacyPlainEntityRuntimePass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/plain-entity-runtime-rules.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override public String id() { return "legacy-plain-entity-runtime"; }

    @Override
    public void apply(ConversionContext context) throws Exception {
        Path candidatePath = context.stagingDir().resolve(LegacyPlainEntityRuntimeCandidatePass.OUTPUT);
        if (!Files.isRegularFile(candidatePath)) return;
        JsonObject candidates = JsonParser.parseString(Files.readString(candidatePath, StandardCharsets.UTF_8)).getAsJsonObject();
        if (integer(candidates, "schemaVersion", -1) != 1
                || !context.sourceHash().equals(string(candidates, "sourceSha256", ""))) return;

        String legacyModId = context.metadata().primary().modId();
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("entityTypeRegistrationWired", true);
        root.addProperty("clientRendererRegistrationWired", true);
        root.addProperty("legacyWatcherBridgeWired", true);
        root.addProperty("constantBehaviorOverrideCodegenWired", true);
        root.addProperty("remoteEntitySpawnRuntimeWired", true);
        root.addProperty("runtimeImplementationWired", true);
        JsonArray rules = new JsonArray();
        JsonArray skipped = new JsonArray();

        for (JsonElement element : array(candidates, "rules")) {
            if (!element.isJsonObject()) continue;
            JsonObject source = element.getAsJsonObject();
            if (!bool(source, "runtimeCandidateReady", false)) continue;

            String id = string(source, "id", null);
            String generatedClass = string(source, "generatedClass", null);
            String presentationAdapter = string(source, "presentationAdapter", null);
            int legacyModEntityTypeId = integer(source, "legacyNumericId", -1);
            int legacyTrackingRangeBlocks = integer(source, "trackingRange", 0);
            int updateFrequency = integer(source, "updateFrequency", 0);
            float width = decimal(source, "width", -1F);
            float height = decimal(source, "height", -1F);
            boolean velocityUpdates = bool(source, "velocityUpdates", false);
            boolean legacyWatcherBridgeWired = bool(source, "legacyWatcherBridgeWired", false);
            boolean constantOverrideCodegenComplete = bool(source, "constantBehaviorOverrideCodegenComplete", false);

            String reason = null;
            if (id == null || id.isBlank()) reason = "runtime candidate id missing";
            else if (generatedClass == null || generatedClass.isBlank()) reason = "generated entity class identity missing";
            else if (legacyModId == null || legacyModId.isBlank()) reason = "legacy mod id missing";
            else if (legacyModEntityTypeId < 0) reason = "legacy mod entity type id missing";
            else if (!legacyWatcherBridgeWired) reason = "legacy watcher bridge missing";
            else if (!constantOverrideCodegenComplete) reason = "constant behavior override codegen incomplete";
            else if (!LegacyPlainEntityRuntimeCandidatePass.PRESENTATION_ADAPTER_NOOP.equals(presentationAdapter))
                reason = "unsupported presentation adapter " + presentationAdapter;
            else if (!(width > 0F) || !(height > 0F) || !Float.isFinite(width) || !Float.isFinite(height))
                reason = "invalid runtime entity dimensions";
            else if (legacyTrackingRangeBlocks <= 0) reason = "invalid legacy tracking range";
            else if (updateFrequency <= 0) reason = "invalid entity update frequency";
            else if (!velocityUpdates) reason = "legacy velocityUpdates=false remains outside the admitted runtime";

            if (reason != null) {
                JsonObject skip = new JsonObject();
                copy(source, skip, "id"); copy(source, skip, "sourceClass"); skip.addProperty("reason", reason); skipped.add(skip);
                continue;
            }

            JsonObject rule = new JsonObject();
            copy(source, rule, "id"); copy(source, rule, "legacyRegistryName"); copy(source, rule, "sourceClass");
            copy(source, rule, "legacyNumericId"); copy(source, rule, "generatedClass"); copy(source, rule, "generatedInternalName");
            copy(source, rule, "width"); copy(source, rule, "height"); copy(source, rule, "updateFrequency"); copy(source, rule, "velocityUpdates");
            copy(source, rule, "synchedDataAccessorCount"); copy(source, rule, "rendererClass");
            copy(source, rule, "constantBehaviorOverrideCodegenComplete"); copy(source, rule, "constantBehaviorOverrideCount");
            if (source.has("constantBehaviorOverrides")) rule.add("constantBehaviorOverrides", source.get("constantBehaviorOverrides").deepCopy());
            if (source.has("synchedDataEntries")) rule.add("synchedDataEntries", source.get("synchedDataEntries").deepCopy());
            rule.addProperty("legacyModId", legacyModId); rule.addProperty("legacyModEntityTypeId", legacyModEntityTypeId);
            rule.addProperty("legacyTrackingRangeBlocks", legacyTrackingRangeBlocks);
            rule.addProperty("modernClientTrackingRangeChunks", blocksToTrackingChunks(legacyTrackingRangeBlocks));
            rule.addProperty("presentationAdapter", presentationAdapter); rule.addProperty("mobCategory", "MISC");
            rule.addProperty("entityTypeRegistrationWired", true); rule.addProperty("clientRendererRegistrationWired", true);
            rule.addProperty("legacyWatcherBridgeWired", true); rule.addProperty("constantBehaviorOverrideCodegenWired", true);
            rule.addProperty("remoteEntitySpawnRuntimeComplete", true); rule.addProperty("runtimeImplementationWired", true); rule.addProperty("runtimeComplete", true);
            rules.add(rule);
        }

        root.add("rules", rules); root.add("skipped", skipped);
        root.addProperty("runtimeRuleCount", rules.size()); root.addProperty("remoteEntitySpawnRuntimeCompleteRules", rules.size());
        root.addProperty("skippedRuntimeRuleCount", skipped.size());

        Path output = context.stagingDir().resolve(OUTPUT); Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        if (!rules.isEmpty()) context.diagnostics().info("LFB-CONVERT-ENTITY-RUNTIME-0001", SupportLevel.ADAPTED,
                "Materialized " + rules.size() + " executable plain Entity runtime rule(s): EntityType/no-op renderer registration, typed watcher mapping, constant base-behavior overrides, and FML remote spawn identity are enabled.");
        if (!skipped.isEmpty()) context.diagnostics().warning("LFB-CONVERT-ENTITY-RUNTIME-0002", SupportLevel.RUNTIME_BRIDGE,
                "Skipped " + skipped.size() + " malformed plain Entity runtime candidate(s) while preserving fail-closed registration.");
    }

    static int blocksToTrackingChunks(int blocks) { if (blocks <= 0) throw new IllegalArgumentException("blocks"); return blocks / 16 + (blocks % 16 == 0 ? 0 : 1); }
    private static JsonArray array(JsonObject root, String name) { JsonElement value = root.get(name); return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray(); }
    private static boolean bool(JsonObject root, String name, boolean fallback) { JsonElement value = root.get(name); return value != null && value.isJsonPrimitive() ? value.getAsBoolean() : fallback; }
    private static int integer(JsonObject root, String name, int fallback) { JsonElement value = root.get(name); return value != null && value.isJsonPrimitive() ? value.getAsInt() : fallback; }
    private static float decimal(JsonObject root, String name, float fallback) { JsonElement value = root.get(name); return value != null && value.isJsonPrimitive() ? value.getAsFloat() : fallback; }
    private static String string(JsonObject root, String name, String fallback) { JsonElement value = root.get(name); return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback; }
    private static void copy(JsonObject source, JsonObject target, String name) { JsonElement value = source.get(name); if (value != null) target.add(name, value.deepCopy()); }
}
