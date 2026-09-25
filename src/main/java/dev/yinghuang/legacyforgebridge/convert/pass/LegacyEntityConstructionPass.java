package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyEntityConstructionAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Materializes source Entity constructor/size proof without generating a modern EntityType. */
public final class LegacyEntityConstructionPass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/entity-construction-surface.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override public String id() { return "legacy-entity-construction-surface"; }

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

        LegacyEntityConstructionAnalyzer.Analysis analysis = new LegacyEntityConstructionAnalyzer().analyze(context.sourceJar());
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("runtimeImplementationWired", false);

        JsonArray rules = new JsonArray();
        int sizeProofComplete = 0;
        for (LegacyEntityConstructionAnalyzer.Rule rule : analysis.rules()) {
            JsonObject definition = definitionByKey.get(key(rule.registryName(), rule.sourceClass()));
            if (definition == null) continue;
            JsonObject value = new JsonObject();
            if (definition.has("id")) value.addProperty("id", definition.get("id").getAsString());
            value.addProperty("legacyRegistryName", rule.registryName());
            value.addProperty("sourceClass", rule.sourceClass());
            if (rule.externalBaseClass() != null) value.addProperty("externalBaseClass", rule.externalBaseClass());
            value.addProperty("worldConstructorPresent", rule.worldConstructorPresent());
            value.addProperty("constructorChainComplete", rule.constructorChainComplete());
            value.addProperty("constructorControlFlowSimple", rule.constructorControlFlowSimple());
            value.addProperty("sourceSetSizeOverridePresent", rule.sourceSetSizeOverridePresent());
            value.addProperty("sizeProofComplete", rule.sizeProofComplete());
            value.addProperty("runtimeConstructionReady", false);
            value.addProperty("runtimeImplementationWired", false);
            if (rule.sizeProofComplete()) {
                value.addProperty("width", rule.width());
                value.addProperty("height", rule.height());
                sizeProofComplete++;
            } else if (rule.sizeProofReason() != null) {
                value.addProperty("sizeProofReason", rule.sizeProofReason());
            }

            JsonArray chain = new JsonArray();
            for (LegacyEntityConstructionAnalyzer.ConstructorSite site : rule.constructorChain()) {
                JsonObject item = new JsonObject();
                item.addProperty("owner", site.owner());
                item.addProperty("descriptor", site.descriptor());
                item.addProperty("simpleControlFlow", site.simpleControlFlow());
                chain.add(item);
            }
            value.add("constructorChain", chain);

            JsonArray effects = new JsonArray();
            for (LegacyEntityConstructionAnalyzer.Effect effect : rule.effects()) {
                JsonObject item = new JsonObject();
                item.addProperty("owner", effect.owner());
                item.addProperty("constructorDescriptor", effect.constructorDescriptor());
                item.addProperty("instructionIndex", effect.instructionIndex());
                item.addProperty("kind", effect.kind());
                if (effect.targetOwner() != null) item.addProperty("targetOwner", effect.targetOwner());
                if (effect.member() != null) item.addProperty("member", effect.member());
                if (effect.descriptor() != null) item.addProperty("descriptor", effect.descriptor());
                effects.add(item);
            }
            value.add("effects", effects);
            value.addProperty("constructorEffectCount", effects.size());
            value.addProperty("unmappedConstructorEffectCount", effects.asList().stream()
                    .map(element -> element.getAsJsonObject().get("kind").getAsString())
                    .filter(kind -> kind.equals("this-field-write") || kind.equals("method-call") || kind.equals("unproven-set-size"))
                    .count());
            rules.add(value);
        }
        root.add("rules", rules);
        root.addProperty("entityConstructionCount", rules.size());
        root.addProperty("sizeProofCompleteCount", sizeProofComplete);
        root.addProperty("sizeProofIncompleteCount", rules.size() - sizeProofComplete);

        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        for (String diagnostic : analysis.diagnostics())
            context.diagnostics().warning("LFB-CONVERT-ENTITY-CONSTRUCTION-0002", SupportLevel.MANUAL_REQUIRED, diagnostic);
        if (!rules.isEmpty()) context.diagnostics().info("LFB-CONVERT-ENTITY-CONSTRUCTION-0001", SupportLevel.RUNTIME_BRIDGE,
                "Inventoried source construction for " + rules.size() + " entity registration(s); constant vanilla-Entity size proof complete for "
                        + sizeProofComplete + ", while runtime construction remains intentionally unwired.");
    }

    private static String key(String registryName, String sourceClass) {
        return registryName + '\u0000' + sourceClass;
    }
}
