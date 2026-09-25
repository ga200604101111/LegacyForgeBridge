package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.yinghuang.legacyforgebridge.convert.LegacyBlockDropItemCompiler;
import dev.yinghuang.legacyforgebridge.convert.LegacyBlockDropMetadataCompiler;
import dev.yinghuang.legacyforgebridge.convert.LegacyBlockDropPlanCompiler;
import dev.yinghuang.legacyforgebridge.convert.LegacyBlockDropQuantityCompiler;
import dev.yinghuang.legacyforgebridge.convert.LegacyBlockExplosionAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyBlockHarvestEligibilityAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyBlockSilkTouchAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyEventAnalyzer;
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
 * <p>Normal drops, harvest eligibility, Forge {@code HarvestDropsEvent} effects, silk-touch
 * selection/stack construction and per-affected-block explosion semantics remain separate proof
 * surfaces. They are composed only when every dependency is source-proven; no modern loot/runtime
 * override is installed here.</p>
 *
 * <p>Harvest eligibility is split into source and platform proof. The source proof rejects custom
 * Block-side harvest callbacks, {@code setHarvestLevel} mutations and registered
 * {@code PlayerEvent.HarvestCheck} handlers. Material/tool/player equivalence is intentionally kept
 * as a later platform-runtime boundary instead of being guessed from incomplete source evidence.</p>
 */
public final class LegacyBlockDropAnalysisPass implements ConversionPass {
    public static final String ANALYSIS_PATH = "legacyforgebridge/block-drop-analysis.json";
    public static final String PLAN_PATH = "legacyforgebridge/block-drop-plans.json";
    private static final String HARVEST_DROPS_EVENT =
            "net/minecraftforge/event/world/BlockEvent$HarvestDropsEvent";
    private static final String HARVEST_CHECK_EVENT =
            "net/minecraftforge/event/entity/player/PlayerEvent$HarvestCheck";
    private static final String HARVEST_EVENT_RUNTIME_BLOCKER = "harvest-drops-event-runtime-pending";
    private static final String HARVEST_EVENT_ABSENCE_BLOCKER = "harvest-drops-event-absence-unproven";
    private static final String HARVEST_CHECK_RUNTIME_BLOCKER = "harvest-check-event-runtime-pending";
    private static final String HARVEST_CHECK_ABSENCE_BLOCKER = "harvest-check-event-absence-unproven";
    private static final String HARVEST_SOURCE_PROOF_BLOCKER = "harvest-eligibility-source-proof-missing";
    private static final String HARVEST_SOURCE_CUSTOMIZATION_BLOCKER =
            "harvest-eligibility-source-customization-runtime-pending";
    private static final String EXPLOSION_CAN_DROP_BLOCKER = "explosion-can-drop-callback-runtime-pending";
    private static final String EXPLOSION_SOURCE_PROOF_BLOCKER = "explosion-source-proof-missing";
    private static final String EXPLOSION_DESTRUCTION_BLOCKER =
            "explosion-destruction-callback-runtime-pending";
    private static final String SILK_SOURCE_PROOF_BLOCKER = "silk-touch-source-proof-missing";
    private static final String SILK_ELIGIBILITY_BLOCKER = "silk-touch-eligibility-runtime-pending";
    private static final String SILK_STACK_BLOCKER = "silk-touch-stacked-item-runtime-pending";
    private static final String EXPLOSION_CHANCE_MODE = "inverse_explosion_size_1_7_10";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<String> RUNTIME_BLOCKERS = List.of("harvest-eligibility-proof-pending");

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
        LegacyBlockHarvestEligibilityAnalyzer.Analysis harvestEligibility =
                new LegacyBlockHarvestEligibilityAnalyzer().analyze(context.sourceJar());
        LegacyBlockExplosionAnalyzer.Analysis explosions = new LegacyBlockExplosionAnalyzer().analyze(context.sourceJar());
        LegacyBlockSilkTouchAnalyzer.Analysis silkTouch = new LegacyBlockSilkTouchAnalyzer().analyze(context.sourceJar());
        LegacyEventAnalyzer.Analysis events = new LegacyEventAnalyzer().analyze(context.sourceJar());
        EventGate harvestEvents = eventGate(events, HARVEST_DROPS_EVENT);
        EventGate harvestChecks = eventGate(events, HARVEST_CHECK_EVENT);
        if (items.rules().isEmpty() && quantities.rules().isEmpty() && metadata.rules().isEmpty()
                && plans.plans().isEmpty() && plans.incomplete().isEmpty()) return;

        Map<BlockKey, JsonObject> blocks = new LinkedHashMap<>();
        for (LegacyBlockDropItemCompiler.Rule rule : items.rules()) {
            JsonObject block = block(blocks, context,
                    new BlockKey(rule.legacyNamespace(), rule.registryName()), rule.implementationClass());
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
                    String category = rule.target().kind() == LegacyBlockDropItemCompiler.TargetKind.ITEM
                            ? "items" : "blocks";
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
            JsonObject block = block(blocks, context,
                    new BlockKey(rule.legacyNamespace(), rule.registryName()), rule.implementationClass());
            JsonObject quantity = new JsonObject();
            quantity.addProperty("value", rule.quantity());
            addSource(quantity, rule.sourceOwner(), rule.sourceMethod(), rule.sourceDescriptor());
            block.add("quantity", quantity);
        }

        for (LegacyBlockDropMetadataCompiler.Rule rule : metadata.rules()) {
            JsonObject block = block(blocks, context,
                    new BlockKey(rule.legacyNamespace(), rule.registryName()), rule.implementationClass());
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

        writePlanSidecar(context, plans, harvestEligibility, explosions, silkTouch, harvestEvents, harvestChecks);
    }

    private static EventGate eventGate(LegacyEventAnalyzer.Analysis analysis, String eventType) {
        List<LegacyEventAnalyzer.Binding> handlers = analysis.forEvent(eventType);
        List<String> diagnostics = analysis.diagnostics();
        return new EventGate(handlers, diagnostics, handlers.isEmpty() && diagnostics.isEmpty());
    }

    private static void writePlanSidecar(
            ConversionContext context,
            LegacyBlockDropPlanCompiler.Analysis analysis,
            LegacyBlockHarvestEligibilityAnalyzer.Analysis harvestEligibility,
            LegacyBlockExplosionAnalyzer.Analysis explosions,
            LegacyBlockSilkTouchAnalyzer.Analysis silkTouch,
            EventGate harvestEvents,
            EventGate harvestChecks
    ) throws IOException {
        if (analysis.plans().isEmpty() && analysis.incomplete().isEmpty()) return;

        Map<BlockKey, LegacyBlockHarvestEligibilityAnalyzer.Proof> harvestProofs = new LinkedHashMap<>();
        for (LegacyBlockHarvestEligibilityAnalyzer.Proof proof : harvestEligibility.proofs()) {
            harvestProofs.put(new BlockKey(proof.legacyNamespace(), proof.registryName()), proof);
        }
        Map<BlockKey, LegacyBlockExplosionAnalyzer.Proof> explosionProofs = new LinkedHashMap<>();
        for (LegacyBlockExplosionAnalyzer.Proof proof : explosions.proofs()) {
            explosionProofs.put(new BlockKey(proof.legacyNamespace(), proof.registryName()), proof);
        }
        Map<BlockKey, LegacyBlockSilkTouchAnalyzer.Proof> silkProofs = new LinkedHashMap<>();
        for (LegacyBlockSilkTouchAnalyzer.Proof proof : silkTouch.proofs()) {
            silkProofs.put(new BlockKey(proof.legacyNamespace(), proof.registryName()), proof);
        }

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 5);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("sourceHarvestDropsEventFree", harvestEvents.absenceProven());
        root.addProperty("harvestDropsEventHandlerCount", harvestEvents.handlers().size());
        root.addProperty("sourceHarvestCheckEventFree", harvestChecks.absenceProven());
        root.addProperty("harvestCheckEventHandlerCount", harvestChecks.handlers().size());
        root.addProperty("legacyExplosionChanceMode", EXPLOSION_CHANCE_MODE);
        root.addProperty("legacyExplosionFortune", 0);

        root.add("harvestDropsEventHandlers", eventHandlers(harvestEvents.handlers()));
        root.add("harvestCheckEventHandlers", eventHandlers(harvestChecks.handlers()));

        JsonArray eventDiagnostics = new JsonArray();
        harvestEvents.diagnostics().forEach(eventDiagnostics::add);
        root.add("eventAnalysisDiagnostics", eventDiagnostics);
        JsonArray harvestEligibilityDiagnostics = new JsonArray();
        harvestEligibility.diagnostics().forEach(harvestEligibilityDiagnostics::add);
        root.add("harvestEligibilityAnalysisDiagnostics", harvestEligibilityDiagnostics);
        JsonArray explosionDiagnostics = new JsonArray();
        explosions.diagnostics().forEach(explosionDiagnostics::add);
        root.add("explosionAnalysisDiagnostics", explosionDiagnostics);
        JsonArray silkDiagnostics = new JsonArray();
        silkTouch.diagnostics().forEach(silkDiagnostics::add);
        root.add("silkTouchAnalysisDiagnostics", silkDiagnostics);

        JsonArray plans = new JsonArray();
        int normalDropProofComplete = 0;
        int sourceHarvestEligibilityProofComplete = 0;
        int explosionDropProofComplete = 0;
        int sourceExplosionDestructionOverrideFree = 0;
        int silkTouchProofComplete = 0;
        for (LegacyBlockDropPlanCompiler.Plan plan : analysis.plans()) {
            BlockKey key = new BlockKey(plan.legacyNamespace(), plan.registryName());
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
            boolean finalNormalDropProof = harvestEvents.absenceProven();
            if (finalNormalDropProof) normalDropProofComplete++;
            value.addProperty("modernIdentityComplete", blockId != null && itemIdentityComplete);
            value.addProperty("preHarvestEventDropProofComplete", true);
            value.addProperty("forgeHarvestEventProofComplete", finalNormalDropProof);
            value.addProperty("normalDropProofComplete", finalNormalDropProof);
            value.addProperty("sourceDropPathOverrideFree", true);

            LegacyBlockHarvestEligibilityAnalyzer.Proof harvest = harvestProofs.get(key);
            boolean sourceHarvestCustomizationFree = harvest != null && harvest.sourceCustomizationFree();
            boolean sourceHarvestProof = sourceHarvestCustomizationFree && harvestChecks.absenceProven();
            if (sourceHarvestProof) sourceHarvestEligibilityProofComplete++;
            value.addProperty("sourceHarvestEligibilityCustomizationFree", sourceHarvestCustomizationFree);
            value.addProperty("sourceHarvestCheckEventFree", harvestChecks.absenceProven());
            value.addProperty("sourceHarvestEligibilityProofComplete", sourceHarvestProof);
            value.addProperty("harvestEligibilityProofComplete", false);
            JsonArray harvestReasons = new JsonArray();
            if (harvest != null) harvest.reasons().forEach(harvestReasons::add);
            value.add("harvestEligibilityReasons", harvestReasons);

            LegacyBlockExplosionAnalyzer.Proof explosion = explosionProofs.get(key);
            boolean sourceExplosionDropEligibility = explosion != null && explosion.dropEligibilityProofComplete();
            boolean sourceExplosionDestruction = explosion != null && explosion.sourceDestructionOverrideFree();
            boolean explosionDropComplete = sourceExplosionDropEligibility && finalNormalDropProof;
            if (explosionDropComplete) explosionDropProofComplete++;
            if (sourceExplosionDestruction) sourceExplosionDestructionOverrideFree++;
            value.addProperty("legacyExplosionChanceMode", EXPLOSION_CHANCE_MODE);
            value.addProperty("legacyExplosionFortune", 0);
            value.addProperty("sourceExplosionDropEligibilityProofComplete", sourceExplosionDropEligibility);
            value.addProperty("sourceExplosionDestructionOverrideFree", sourceExplosionDestruction);
            value.addProperty("explosionDropProofComplete", explosionDropComplete);
            if (explosion != null) {
                JsonArray dropReasons = new JsonArray();
                explosion.dropEligibilityReasons().forEach(dropReasons::add);
                value.add("explosionDropEligibilityReasons", dropReasons);
                JsonArray destructionReasons = new JsonArray();
                explosion.destructionReasons().forEach(destructionReasons::add);
                value.add("explosionDestructionReasons", destructionReasons);
            }

            LegacyBlockSilkTouchAnalyzer.Proof silk = silkProofs.get(key);
            boolean silkEligibilityComplete = silk != null && silk.eligibilityProofComplete();
            Boolean silkEligible = silk == null ? null : silk.silkEligible();
            boolean silkStackComplete = silk != null && silk.stackedItemProofComplete();
            boolean silkComplete = silkEligibilityComplete && Boolean.FALSE.equals(silkEligible)
                    || silkEligibilityComplete && Boolean.TRUE.equals(silkEligible)
                    && silkStackComplete && harvestEvents.absenceProven();
            if (silkComplete) silkTouchProofComplete++;
            value.addProperty("silkTouchEligibilityProofComplete", silkEligibilityComplete);
            if (silkEligible != null) value.addProperty("silkTouchEligible", silkEligible);
            value.addProperty("silkTouchStackProofComplete", silkStackComplete);
            value.addProperty("silkTouchProofComplete", silkComplete);
            if (silk != null) {
                JsonArray silkEligibilityReasons = new JsonArray();
                silk.eligibilityReasons().forEach(silkEligibilityReasons::add);
                value.add("silkTouchEligibilityReasons", silkEligibilityReasons);
                JsonArray silkStackReasons = new JsonArray();
                silk.stackedItemReasons().forEach(silkStackReasons::add);
                value.add("silkTouchStackReasons", silkStackReasons);
                if (silkStackComplete) {
                    JsonObject silkStack = new JsonObject();
                    silkStack.addProperty("kind", "SELF_BLOCK_ITEM");
                    silkStack.addProperty("quantity", 1);
                    silkStack.addProperty("legacyDamage", silk.stackedLegacyDamage());
                    if (blockId != null) silkStack.addProperty("modernId", blockId);
                    value.add("silkTouchStack", silkStack);
                }
            }
            value.addProperty("runtimeComplete", false);

            JsonArray blockers = new JsonArray();
            RUNTIME_BLOCKERS.forEach(blockers::add);
            if (harvest == null) {
                blockers.add(HARVEST_SOURCE_PROOF_BLOCKER);
            } else if (!harvest.sourceCustomizationFree()) {
                blockers.add(HARVEST_SOURCE_CUSTOMIZATION_BLOCKER);
            }
            if (!harvestChecks.absenceProven()) {
                blockers.add(harvestChecks.handlers().isEmpty()
                        ? HARVEST_CHECK_ABSENCE_BLOCKER : HARVEST_CHECK_RUNTIME_BLOCKER);
            }
            if (!harvestEvents.absenceProven()) {
                blockers.add(harvestEvents.handlers().isEmpty()
                        ? HARVEST_EVENT_ABSENCE_BLOCKER : HARVEST_EVENT_RUNTIME_BLOCKER);
            }
            if (explosion == null) {
                blockers.add(EXPLOSION_SOURCE_PROOF_BLOCKER);
            } else {
                if (!explosion.dropEligibilityProofComplete()) blockers.add(EXPLOSION_CAN_DROP_BLOCKER);
                if (!explosion.sourceDestructionOverrideFree()) blockers.add(EXPLOSION_DESTRUCTION_BLOCKER);
            }
            if (silk == null) {
                blockers.add(SILK_SOURCE_PROOF_BLOCKER);
            } else if (!silk.eligibilityProofComplete()) {
                blockers.add(SILK_ELIGIBILITY_BLOCKER);
            } else if (Boolean.TRUE.equals(silk.silkEligible()) && !silk.stackedItemProofComplete()) {
                blockers.add(SILK_STACK_BLOCKER);
            }
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
            value.addProperty("preHarvestEventDropProofComplete", false);
            value.addProperty("forgeHarvestEventProofComplete", false);
            value.addProperty("normalDropProofComplete", false);
            value.addProperty("sourceHarvestEligibilityProofComplete", false);
            value.addProperty("harvestEligibilityProofComplete", false);
            value.addProperty("explosionDropProofComplete", false);
            value.addProperty("silkTouchProofComplete", false);
            value.addProperty("runtimeComplete", false);
            incomplete.add(value);
        }
        root.add("incomplete", incomplete);

        JsonArray diagnostics = new JsonArray();
        analysis.diagnostics().forEach(diagnostics::add);
        root.add("diagnostics", diagnostics);
        root.addProperty("preHarvestEventDropProofCompletePlans", plans.size());
        root.addProperty("normalDropProofCompletePlans", normalDropProofComplete);
        root.addProperty("sourceHarvestEligibilityProofCompletePlans", sourceHarvestEligibilityProofComplete);
        root.addProperty("harvestEligibilityProofCompletePlans", 0);
        root.addProperty("explosionDropProofCompletePlans", explosionDropProofComplete);
        root.addProperty("sourceExplosionDestructionOverrideFreePlans", sourceExplosionDestructionOverrideFree);
        root.addProperty("silkTouchProofCompletePlans", silkTouchProofComplete);
        root.addProperty("runtimeCompletePlans", 0);
        root.addProperty("incompletePlans", incomplete.size());

        Path output = context.stagingDir().resolve(PLAN_PATH);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        context.diagnostics().info("LFB-CONVERT-BLOCK-DROP-0004", SupportLevel.RUNTIME_BRIDGE,
                "Materialized Forge block-drop plans without enabling gameplay drops: preEventComplete="
                        + plans.size() + ", finalNormalComplete=" + normalDropProofComplete
                        + ", sourceHarvestEligibilityComplete=" + sourceHarvestEligibilityProofComplete
                        + ", silkComplete=" + silkTouchProofComplete
                        + ", explosionDropComplete=" + explosionDropProofComplete
                        + ", incomplete=" + incomplete.size()
                        + ". Material/tool/player harvest equivalence remains gated.");
        if (!harvestEvents.handlers().isEmpty()) {
            context.diagnostics().info("LFB-CONVERT-BLOCK-DROP-0005", SupportLevel.RUNTIME_BRIDGE,
                    "Source registers HarvestDropsEvent handlers=" + harvestEvents.handlers().size()
                            + "; final normal/silk/explosion drop runtime remains gated until those event effects are migrated.");
        } else if (!harvestEvents.diagnostics().isEmpty()) {
            context.diagnostics().info("LFB-CONVERT-BLOCK-DROP-0006", SupportLevel.RUNTIME_BRIDGE,
                    "HarvestDropsEvent absence could not be proven because event analysis reported "
                            + harvestEvents.diagnostics().size() + " unresolved diagnostic(s).");
        }
        if (!harvestChecks.handlers().isEmpty()) {
            context.diagnostics().info("LFB-CONVERT-BLOCK-DROP-0007", SupportLevel.RUNTIME_BRIDGE,
                    "Source registers PlayerEvent.HarvestCheck handlers=" + harvestChecks.handlers().size()
                            + "; harvest eligibility runtime remains gated until those event effects are migrated.");
        } else if (!harvestChecks.diagnostics().isEmpty()) {
            context.diagnostics().info("LFB-CONVERT-BLOCK-DROP-0008", SupportLevel.RUNTIME_BRIDGE,
                    "PlayerEvent.HarvestCheck absence could not be proven because event analysis reported "
                            + harvestChecks.diagnostics().size() + " unresolved diagnostic(s).");
        }
    }

    private static JsonArray eventHandlers(List<LegacyEventAnalyzer.Binding> handlers) {
        JsonArray values = new JsonArray();
        for (LegacyEventAnalyzer.Binding binding : handlers) {
            JsonObject value = new JsonObject();
            value.addProperty("handlerClass", binding.handlerClass());
            value.addProperty("method", binding.method());
            value.addProperty("descriptor", binding.descriptor());
            value.addProperty("bus", binding.bus().name());
            value.addProperty("side", binding.side().name());
            value.addProperty("priority", binding.priority());
            value.addProperty("receiveCanceled", binding.receiveCanceled());
            value.addProperty("registrationOwner", binding.registrationOwner());
            value.addProperty("registrationMethod", binding.registrationMethod());
            value.addProperty("registrationDescriptor", binding.registrationDescriptor());
            values.add(value);
        }
        return values;
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

    private record EventGate(List<LegacyEventAnalyzer.Binding> handlers, List<String> diagnostics, boolean absenceProven) {
        private EventGate {
            handlers = List.copyOf(handlers);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private record BlockKey(String legacyNamespace, String registryName) { }
}
