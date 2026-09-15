package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyItemBlockBindingAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Emits source-proven seed/reed block topology while keeping placement runtime disabled. */
public final class LegacyItemBlockBindingPass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/item-block-bindings.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override public String id() { return "legacy-item-block-bindings"; }

    @Override
    public void apply(ConversionContext context) throws Exception {
        var analysis = new LegacyItemBlockBindingAnalyzer().analyze(context.sourceJar());
        if (analysis.rules().isEmpty() && analysis.skipped().isEmpty()) return;
        ContentIndex content = contentIndex(context.stagingDir());
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        JsonArray bindings = new JsonArray();
        int modernComplete = 0;
        int unmappedItems = 0;
        for (var rule : analysis.rules()) {
            String itemId = content.itemIds().get(key(rule.registryName(), rule.sourceClass()));
            if (itemId == null) { unmappedItems++; continue; }
            JsonObject value = new JsonObject();
            value.addProperty("id", itemId);
            value.addProperty("legacyRegistryName", rule.registryName());
            value.addProperty("sourceClass", rule.sourceClass());
            value.addProperty("family", rule.family().name().toLowerCase());
            value.add("targetBlock", reference(rule.targetBlock(), content));
            if (rule.soilBlock() != null) value.add("soilBlock", reference(rule.soilBlock(), content));
            if (rule.nutrition() != null) value.addProperty("nutrition", rule.nutrition());
            if (rule.saturationModifier() != null) value.addProperty("saturationModifier", rule.saturationModifier());
            boolean complete = modernId(rule.targetBlock(), content) != null
                    && (rule.soilBlock() == null || modernId(rule.soilBlock(), content) != null);
            value.addProperty("topologyProofComplete", true);
            value.addProperty("modernIdentityComplete", complete);
            value.addProperty("runtimeComplete", false);
            if (complete) modernComplete++;
            bindings.add(value);
        }
        root.add("bindings", bindings);
        JsonArray skipped = new JsonArray();
        for (var item : analysis.skipped()) {
            JsonObject value = new JsonObject();
            value.addProperty("legacyRegistryName", item.registryName());
            if (item.sourceClass() != null) value.addProperty("sourceClass", item.sourceClass());
            value.addProperty("reason", item.reason());
            skipped.add(value);
        }
        root.add("skipped", skipped);
        root.addProperty("topologyProofCompleteBindings", bindings.size());
        root.addProperty("modernIdentityCompleteBindings", modernComplete);
        root.addProperty("runtimeCompleteBindings", 0);
        root.addProperty("skippedBindings", skipped.size());
        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
        analysis.diagnostics().forEach(message -> context.diagnostics().warning(
                "LFB-CONVERT-ITEM-BLOCK-0003", SupportLevel.MANUAL_REQUIRED, message));
        if (unmappedItems > 0) context.diagnostics().warning("LFB-CONVERT-ITEM-BLOCK-0004", SupportLevel.MANUAL_REQUIRED,
                "Source-proven item/block bindings without generated item identity: " + unmappedItems + ".");
        if (!bindings.isEmpty()) context.diagnostics().info("LFB-CONVERT-ITEM-BLOCK-0001", SupportLevel.RUNTIME_BRIDGE,
                "Proven legacy seed/reed item-to-block topology: " + bindings.size()
                        + "; modern block identities complete=" + modernComplete + "; placement runtime remains gated.");
        if (modernComplete < bindings.size()) context.diagnostics().info("LFB-CONVERT-ITEM-BLOCK-0002", SupportLevel.RUNTIME_BRIDGE,
                "Some proven seed/reed bindings still reference external or unmapped legacy block fields; no modern id was guessed.");
    }

    private static JsonObject reference(LegacyItemBlockBindingAnalyzer.BlockReference reference, ContentIndex content) {
        JsonObject value = new JsonObject();
        value.addProperty("registered", reference.registered());
        if (reference.registryName() != null) value.addProperty("legacyRegistryName", reference.registryName());
        if (reference.legacyNamespace() != null) value.addProperty("legacyNamespace", reference.legacyNamespace());
        if (reference.implementationClass() != null) value.addProperty("sourceClass", reference.implementationClass());
        value.addProperty("sourceFieldOwner", reference.sourceFieldOwner());
        value.addProperty("sourceFieldName", reference.sourceFieldName());
        value.addProperty("sourceFieldDescriptor", reference.sourceFieldDescriptor());
        String modern = modernId(reference, content);
        if (modern != null) value.addProperty("modernId", modern);
        return value;
    }

    private static String modernId(LegacyItemBlockBindingAnalyzer.BlockReference reference, ContentIndex content) {
        if (reference == null || !reference.registered()) return null;
        if (reference.implementationClass() != null) {
            String value = content.blockIdsByClass().get(reference.implementationClass());
            if (value != null) return value;
        }
        return reference.registryName() == null ? null : content.blockIdsByLegacyName().get(reference.registryName());
    }

    private static ContentIndex contentIndex(Path staging) throws Exception {
        Path path = staging.resolve("legacyforgebridge/converted-content.json");
        if (!Files.isRegularFile(path)) return new ContentIndex(Map.of(), Map.of(), Map.of());
        Map<String,String> items = new LinkedHashMap<>();
        Map<String,String> blocksByClass = new LinkedHashMap<>();
        Map<String,String> blocksByName = new LinkedHashMap<>();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject content = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray itemValues = content.getAsJsonArray("items");
            if (itemValues != null) for (var element : itemValues) if (element.isJsonObject()) {
                JsonObject item = element.getAsJsonObject();
                if (item.has("legacyRegistryName") && item.has("sourceClass") && item.has("id"))
                    items.put(key(item.get("legacyRegistryName").getAsString(), item.get("sourceClass").getAsString()), item.get("id").getAsString());
            }
            JsonArray blockValues = content.getAsJsonArray("blocks");
            if (blockValues != null) for (var element : blockValues) if (element.isJsonObject()) {
                JsonObject block = element.getAsJsonObject();
                if (!block.has("id")) continue;
                String id = block.get("id").getAsString();
                if (block.has("sourceClass")) blocksByClass.put(block.get("sourceClass").getAsString(), id);
                if (block.has("legacyRegistryName")) blocksByName.put(block.get("legacyRegistryName").getAsString(), id);
            }
        }
        return new ContentIndex(Map.copyOf(items), Map.copyOf(blocksByClass), Map.copyOf(blocksByName));
    }

    private static String key(String registryName, String sourceClass) { return registryName + "\u0000" + sourceClass; }
    private record ContentIndex(Map<String,String> itemIds, Map<String,String> blockIdsByClass,
                                Map<String,String> blockIdsByLegacyName) { }
}
