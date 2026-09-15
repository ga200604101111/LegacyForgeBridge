package dev.longyu.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.longyu.legacyforgebridge.convert.LegacyBlockDropItemCompiler;
import dev.longyu.legacyforgebridge.convert.LegacyBlockDropMetadataCompiler;
import dev.longyu.legacyforgebridge.convert.LegacyBlockDropQuantityCompiler;
import dev.longyu.legacyforgebridge.convert.api.ConversionContext;
import dev.longyu.legacyforgebridge.convert.api.ConversionPass;
import dev.longyu.legacyforgebridge.convert.api.SupportLevel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;

/**
 * Materializes independently proven legacy block-drop evidence without changing gameplay drops.
 *
 * <p>Item identity, quantity and item damage are deliberately kept as separate optional fields.
 * A later runtime materializer may override modern drops only after it has enough evidence for the
 * complete legacy result. Missing evidence therefore remains visibly missing instead of being
 * replaced with guessed defaults.</p>
 *
 * <p>Vanilla 1.7 targets intentionally remain as {@code minecraft:<legacy registry id>} rather
 * than receiving an eager modern {@code id}. Their source metadata can participate in flattening,
 * so only the complete drop plan may pass them through the DFU-backed stack materializer.</p>
 */
public final class LegacyBlockDropAnalysisPass implements ConversionPass {
    public static final String ANALYSIS_PATH = "legacyforgebridge/block-drop-analysis.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public String id() {
        return "legacy-block-drop-analysis";
    }

    @Override
    public void apply(ConversionContext context) throws IOException {
        LegacyBlockDropItemCompiler.Analysis items = new LegacyBlockDropItemCompiler().compile(context.sourceJar());
        LegacyBlockDropQuantityCompiler.Analysis quantities = new LegacyBlockDropQuantityCompiler().compile(context.sourceJar());
        LegacyBlockDropMetadataCompiler.Analysis metadata = new LegacyBlockDropMetadataCompiler().compile(context.sourceJar());
        if (items.rules().isEmpty() && quantities.rules().isEmpty() && metadata.rules().isEmpty()) return;

        Map<BlockKey, JsonObject> blocks = new LinkedHashMap<>();
        for (LegacyBlockDropItemCompiler.Rule rule : items.rules()) {
            JsonObject block = block(blocks, context, new BlockKey(rule.legacyNamespace(), rule.registryName()), rule.implementationClass());
            JsonObject item = new JsonObject();
            item.addProperty("kind", rule.target().kind().name());
            if (rule.target().kind() != LegacyBlockDropItemCompiler.TargetKind.NONE) {
                item.addProperty("legacyRegistryName", rule.target().registryName());
                if (rule.target().legacyNamespace() != null && !rule.target().legacyNamespace().isBlank()) {
                    item.addProperty("legacyNamespace", rule.target().legacyNamespace());
                }
                item.addProperty("sourceFieldOwner", rule.target().sourceFieldOwner());
                item.addProperty("sourceFieldName", rule.target().sourceFieldName());
                item.addProperty("sourceFieldDescriptor", rule.target().sourceFieldDescriptor());

                if (!"minecraft".equals(rule.target().legacyNamespace())) {
                    String category = rule.target().kind() == LegacyBlockDropItemCompiler.TargetKind.ITEM ? "items" : "blocks";
                    String modernTarget = modernIdentity(context, category,
                            rule.target().legacyNamespace(), rule.target().registryName());
                    if (modernTarget != null) {
                        item.addProperty("id", modernTarget);
                    } else {
                        context.diagnostics().warning("LFB-CONVERT-BLOCK-DROP-0002", SupportLevel.MANUAL_REQUIRED,
                                "Drop target evidence exists but no modern registry identity was proven for "
                                        + legacyId(context, rule.target().legacyNamespace(), rule.target().registryName()) + ".");
                    }
                }
            }
            addSource(item, rule.sourceOwner(), rule.sourceMethod(), rule.sourceDescriptor());
            block.add("item", item);
        }

        for (LegacyBlockDropQuantityCompiler.Rule rule : quantities.rules()) {
            JsonObject block = block(blocks, context, new BlockKey(rule.legacyNamespace(), rule.registryName()), rule.implementationClass());
            JsonObject quantity = new JsonObject();
            quantity.addProperty("value", rule.quantity());
            addSource(quantity, rule.sourceOwner(), rule.sourceMethod(), rule.sourceDescriptor());
            block.add("quantity", quantity);
        }

        for (LegacyBlockDropMetadataCompiler.Rule rule : metadata.rules()) {
            JsonObject block = block(blocks, context, new BlockKey(rule.legacyNamespace(), rule.registryName()), rule.implementationClass());
            JsonObject damage = new JsonObject();
            JsonArray values = new JsonArray();
            rule.itemDamageByBlockMeta().forEach(values::add);
            damage.add("byBlockMeta", values);
            addSource(damage, rule.sourceOwner(), rule.sourceMethod(), rule.sourceDescriptor());
            block.add("damage", damage);
        }

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        JsonArray blockArray = new JsonArray();
        blocks.values().forEach(blockArray::add);
        root.add("blocks", blockArray);

        LinkedHashSet<String> diagnostics = new LinkedHashSet<>();
        diagnostics.addAll(items.diagnostics());
        diagnostics.addAll(quantities.diagnostics());
        diagnostics.addAll(metadata.diagnostics());
        JsonArray diagnosticArray = new JsonArray();
        diagnostics.forEach(diagnosticArray::add);
        root.add("diagnostics", diagnosticArray);

        Path output = context.stagingDir().resolve(ANALYSIS_PATH);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
        context.diagnostics().info("LFB-CONVERT-BLOCK-DROP-0001", SupportLevel.ADAPTED,
                "Materialized non-executing block-drop evidence without changing gameplay drops: blocks="
                        + blocks.size() + ", itemRules=" + items.rules().size()
                        + ", quantityRules=" + quantities.rules().size()
                        + ", damageRules=" + metadata.rules().size() + ".");
    }

    private static JsonObject block(Map<BlockKey, JsonObject> blocks, ConversionContext context,
                                    BlockKey key, String implementationClass) {
        return blocks.computeIfAbsent(key, ignored -> {
            JsonObject block = new JsonObject();
            block.addProperty("legacyRegistryName", key.registryName());
            if (key.legacyNamespace() != null && !key.legacyNamespace().isBlank()) {
                block.addProperty("legacyNamespace", key.legacyNamespace());
            }
            block.addProperty("sourceClass", implementationClass);
            String modern = modernIdentity(context, "blocks", key.legacyNamespace(), key.registryName());
            if (modern != null) {
                block.addProperty("id", modern);
            } else {
                context.diagnostics().warning("LFB-CONVERT-BLOCK-DROP-0003", SupportLevel.MANUAL_REQUIRED,
                        "Drop evidence exists but no modern block identity was proven for "
                                + legacyId(context, key.legacyNamespace(), key.registryName()) + ".");
            }
            return block;
        });
    }

    private static String modernIdentity(ConversionContext context, String category,
                                         String legacyNamespace, String registryName) {
        Map<String, String> identities = context.registryIdentities().getOrDefault(category, Map.of());
        String legacyId = legacyId(context, legacyNamespace, registryName);
        String value = identities.get(legacyId);
        if (value == null) value = identities.get(legacyId.toLowerCase(Locale.ROOT));
        return value;
    }

    private static String legacyId(ConversionContext context, String legacyNamespace, String registryName) {
        String namespace = legacyNamespace;
        if (namespace == null || namespace.isBlank()) namespace = context.metadata().primary().modId();
        return namespace + ":" + registryName;
    }

    private static void addSource(JsonObject value, String owner, String method, String descriptor) {
        value.addProperty("sourceOwner", owner);
        value.addProperty("sourceMethod", method);
        value.addProperty("sourceDescriptor", descriptor);
    }

    private record BlockKey(String legacyNamespace, String registryName) { }
}
