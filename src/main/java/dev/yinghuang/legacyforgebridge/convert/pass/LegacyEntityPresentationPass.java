package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyEntityPresentationAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Materializes legacy entity renderer registrations and a strict source no-op presentation proof. */
public final class LegacyEntityPresentationPass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/entity-presentation-surface.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override public String id() { return "legacy-entity-presentation-surface"; }

    @Override
    public void apply(ConversionContext context) throws Exception {
        Path definitionsPath = context.stagingDir().resolve(LegacyEntityDataWatcherPass.OUTPUT);
        if (!Files.isRegularFile(definitionsPath)) return;
        JsonObject definitions = JsonParser.parseString(Files.readString(definitionsPath, StandardCharsets.UTF_8)).getAsJsonObject();
        if (!definitions.has("schemaVersion") || definitions.get("schemaVersion").getAsInt() != 1) return;
        if (!definitions.has("sourceSha256") || !context.sourceHash().equals(definitions.get("sourceSha256").getAsString())) return;

        LegacyEntityPresentationAnalyzer.Analysis analysis = new LegacyEntityPresentationAnalyzer().analyze(context.sourceJar());
        Map<String,List<LegacyEntityPresentationAnalyzer.Registration>> byEntity = new LinkedHashMap<>();
        for (var registration : analysis.registrations())
            byEntity.computeIfAbsent(registration.entityClass(), ignored -> new ArrayList<>()).add(registration);

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("presentationRuntimeWired", false);
        JsonArray rules = new JsonArray();
        int noOpProofs = 0;

        for (JsonElement element : array(definitions, "rules")) {
            if (!element.isJsonObject()) continue;
            JsonObject definition = element.getAsJsonObject();
            String sourceClass = string(definition, "sourceClass", null);
            if (sourceClass == null) continue;
            List<LegacyEntityPresentationAnalyzer.Registration> registrations = byEntity.getOrDefault(sourceClass, List.of());

            JsonObject rule = new JsonObject();
            copy(definition, rule, "id"); copy(definition, rule, "legacyRegistryName"); copy(definition, rule, "sourceClass");
            rule.addProperty("sourceRendererRegistrationCount", registrations.size());
            rule.addProperty("sourceRendererRegistrationFound", !registrations.isEmpty());
            boolean noOp = registrations.size() == 1
                    && registrations.getFirst().rendererClassPresent()
                    && registrations.getFirst().noOpRenderProven();
            rule.addProperty("sourceNoOpRendererProven", noOp);
            rule.addProperty("presentationRuntimeWired", false);
            rule.addProperty("presentationRuntimeReady", false);

            JsonArray registrationValues = new JsonArray();
            for (var registration : registrations) {
                JsonObject value = new JsonObject();
                value.addProperty("rendererClass", registration.rendererClass());
                value.addProperty("sourceOwner", registration.sourceOwner());
                value.addProperty("sourceMethod", registration.sourceMethod());
                value.addProperty("sourceDescriptor", registration.sourceDescriptor());
                value.addProperty("rendererClassPresent", registration.rendererClassPresent());
                value.addProperty("noOpRenderProven", registration.noOpRenderProven());
                if (registration.renderOwner() != null) value.addProperty("renderOwner", registration.renderOwner());
                if (registration.renderMethod() != null) value.addProperty("renderMethod", registration.renderMethod());
                if (registration.renderDescriptor() != null) value.addProperty("renderDescriptor", registration.renderDescriptor());
                registrationValues.add(value);
            }
            rule.add("registrations", registrationValues);

            JsonArray blockers = new JsonArray();
            if (registrations.isEmpty()) blockers.add("source-entity-renderer-registration-missing");
            else if (registrations.size() != 1) blockers.add("ambiguous-source-entity-renderer-registration");
            else {
                var registration = registrations.getFirst();
                if (!registration.rendererClassPresent()) blockers.add("source-renderer-class-missing");
                else if (!registration.noOpRenderProven()) blockers.add("source-renderer-not-proven-noop");
                else blockers.add("modern-noop-renderer-runtime-not-materialized");
            }
            rule.add("presentationBlockers", blockers);
            rules.add(rule);
            if (noOp) noOpProofs++;
        }

        root.add("rules", rules);
        root.addProperty("evaluatedRegistrations", rules.size());
        root.addProperty("sourceNoOpRendererProofs", noOpProofs);
        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        for (String diagnostic : analysis.diagnostics())
            context.diagnostics().warning("LFB-CONVERT-ENTITY-PRESENTATION-0002", SupportLevel.MANUAL_REQUIRED, diagnostic);
        if (!rules.isEmpty()) context.diagnostics().info("LFB-CONVERT-ENTITY-PRESENTATION-0001", SupportLevel.RUNTIME_BRIDGE,
                "Inventoried legacy entity renderer registrations for " + rules.size() + " entity registration(s), with "
                        + noOpProofs + " source renderer(s) proven as exact no-op presentation; modern client renderer runtime remains unwired.");
    }

    private static JsonArray array(JsonObject root, String name) {
        JsonElement value = root.get(name);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }
    private static String string(JsonObject root, String name, String fallback) {
        JsonElement value = root.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }
    private static void copy(JsonObject source, JsonObject target, String name) {
        JsonElement value = source.get(name);
        if (value != null) target.add(name, value.deepCopy());
    }
}
