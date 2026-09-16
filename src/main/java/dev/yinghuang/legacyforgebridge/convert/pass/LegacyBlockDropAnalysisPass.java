package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.yinghuang.legacyforgebridge.convert.LegacyBlockDropItemCompiler;
import dev.yinghuang.legacyforgebridge.convert.LegacyBlockDropMetadataCompiler;
import dev.yinghuang.legacyforgebridge.convert.LegacyBlockDropPlanCompiler;
import dev.yinghuang.legacyforgebridge.convert.LegacyBlockDropQuantityCompiler;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Materializes independently proven legacy block-drop evidence without changing gameplay drops.
 *
 * <p>Item identity, quantity and item damage are deliberately kept as separate optional fields.
 * The companion normal-drop plan sidecar composes them only after the source hierarchy and
 * surrounding Forge harvest/drop path pass fail-closed proof gates. Even complete plans remain
 * non-executing until harvest eligibility, silk-touch stacked-item behavior and explosion chance
 * are separately proven.</p>
 *
 * <p>Vanilla 1.7 targets intentionally remain as {@code minecraft:<legacy registry id>} rather
 * than receiving an eager modern {@code id}. Their source metadata can participate in flattening,
 * so only a later proven stack materializer may translate them into a modern item identity.</p>
 */
public final class LegacyBlockDropAnalysisPass implements ConversionPass {
    public static final String ANALYSIS_PATH = "legacyforgebridge/block-drop-analysis.json";
    public static final String PLAN_PATH = "legacyforgebridge/block-drop-plans.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<String> RUNTIME_BLOCKERS = List.of(
            "harvest-eligibility-proof-pending",
            "silk-touch-stacked-item-proof-pending",
            "explosion-drop-chance-proof-pending"
    );

    @Override
    public String id() {
        return "legacy-block-drop-analysis";
    }

    @Override
    public void apply(ConversionContext context) throws IOException {
        LegacyBlockDropItemCompiler.Analysis items = new LegacyBlockDropItemCompiler().compile(context.sourceJar());
        LegacyBlockDropQuantityCompiler.Analysis quantities = new LegacyBlockDropQuantityCompiler().compile(context.sourceJar());
        LegacyBlockDropMetadataCompiler.Analysis metadata = new LegacyBlockDropMetadataCompiler().compile(context.sourceJar());
        LegacyBlockDropPlanCompiler.Analysis plans = new LegacyBlockDropPlanCompiler().compile(context.sourceJar());
        if (items.rules().isEmpty() && quantities.rules().isEmpty() && metadata.rules().isEmpty()
                && plans.plans().isEmpty() && plans.incomplete().isEmpty()) return;

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

        writePlanSidecar(context, plans);
    }

    private static void writePlanSidecar(
            ConversionContext context,
            LegacyBlockDropPlanCompiler.Analysis analysis
    ) throws IOException {
        if (analysis.plans().isEmpty() && analysis.incomplete().isEmpty()) return;

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());

        JsonArray plans = new JsonArray();
        for (LegacyBlockDropPlanCompiler.Plan plan : analysis.plans()) {
            JsonObject value = new JsonObject();
            value.addProperty("legacyRegistryName", plan.registryName());
            if (plan.legacyNamespace() != null && !plan.legacyNamespace().isBlank()) {
                value.addProperty("legacyNamespace", plan.legacyNamespace());
            }
            if (plan.implementationClass() != null) value.addProperty("sourceClass", plan.implementationClass());

            String blockId = modernIdentity(context, "blocks", plan.legacyNamespace(), plan.registryName());
            if (blockId != null) value.addProperty("id", blockId);

            JsonObject item = new JsonObject();
            item.addProperty("kind", plan.item().kind().name());
            String itemId = null;
            if (plan.item().kind() != LegacyBlockDropPlanCompiler.ItemKind.NONE) {
                item.addProperty("legacyRegistryName", plan.item().registryName());
                if (plan.item().legacyNamespace() != null && !plan.item().legacyNamespace().isBlank()) {
                    item.addProperty("legacyNamespace", plan.item().legacyNamespace());
                }
                itemId = plan.item().kind() == LegacyBlockDropPlanCompiler.ItemKind.SELF_BLOCK_ITEM
                        ? blockId
                        : modernIdentity(context, "items",
                        plan.item().legacyNamespace(), plan.item().registryName());
                if (itemId != null) item.addProperty("modernId", itemId);
            }
            value.add("item", item);
            value.addProperty("quantity", plan.quantity());

            JsonArray damages = new JsonArray();
            plan.itemDamageByBlockMeta().forEach(damages::add);
            value.add("itemDamageByBlockMeta", damages);

            JsonArray defaults = new JsonArray();
            plan.platformDefaults().forEach(defaults::add);
            value.add("platformDefaults", defaults);

            boolean itemIdentityComplete = plan.item().kind() == LegacyBlockDropPlanCompiler.ItemKind.NONE
                    || itemId != null;
            value.addProperty("modernIdentityComplete", blockId != null && itemIdentityComplete);
            value.addProperty("normalDropProofComplete", true);
            value.addProperty("sourceDropPathOverrideFree", true);
            value.addProperty("runtimeComplete", false);

            JsonArray blockers = new JsonArray();
            RUNTIME_BLOCKERS.forEach(blockers::add);
            value.add("runtimeBlockers", blockers);
            plans.add(value);
        }
        root.add("plans", plans);

        JsonArray incomplete = new JsonArray();
        for (LegacyBlockDropPlanCompiler.Incomplete plan : analysis.incomplete()) {
            JsonObject value = new JsonObject();
            value.addProperty("legacyRegistryName", plan.registryName());
            if (plan.legacyNamespace() != null && !plan.legacyNamespace().isBlank()) {
                value.addProperty("legacyNamespace", plan.legacyNamespace());
            }
            if (plan.implementationClass() != null) value.addProperty("sourceClass", plan.implementationClass());
            JsonArray reasons = new JsonArray();
            plan.reasons().forEach(reasons::add);
            value.add("reasons", reasons);
            value.addProperty("normalDropProofComplete", false);
            value.addProperty("runtimeComplete", false);
            incomplete.add(value);
        }
        root.add("incomplete", incomplete);

        JsonArray diagnostics = new JsonArray();
        analysis.diagnostics().forEach(diagnostics::add);
        root.add("diagnostics", diagnostics);
        root.addProperty("normalDropProofCompletePlans", plans.size());
        root.addProperty("runtimeCompletePlans", 0);
        root.addProperty("incompletePlans", incomplete.size());

        Path output = context.stagingDir().resolve(PLAN_PATH);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        context.diagnostics().info(
                "LFB-CONVERT-BLOCK-DROP-0004",
                SupportLevel.RUNTIME_BRIDGE,
                "Materialized complete normal-drop plans without enabling gameplay drops: complete="
                        + plans.size() + ", incomplete=" + incomplete.size()
                        + ". Harvest eligibility, silk-touch stacked-item semantics and explosion drop chance remain gated."
        );
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
        if (registryName == null || registryName.isBlank()) return null;
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
