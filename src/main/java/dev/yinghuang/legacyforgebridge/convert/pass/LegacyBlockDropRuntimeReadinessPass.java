package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyEventAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Classifies a deliberately narrow block-drop subset that can later be mapped to a static modern
 * self-drop runtime without interpreting unresolved tool or explosion semantics.
 *
 * <p>This pass is proof-only. It does not generate loot tables or override modern block drops.
 * The exact 1.7.10 and 1.21.11 decay formulas are recorded separately from full explosion runtime
 * readiness: modern non-decay explosion interactions omit the explosion-radius loot parameter, and
 * source Forge ExplosionEvent handlers may alter/cancel the affected-block set.</p>
 */
public final class LegacyBlockDropRuntimeReadinessPass implements ConversionPass {
    public static final String OUTPUT_PATH = "legacyforgebridge/block-drop-runtime-readiness.json";
    public static final int INPUT_SCHEMA = 6;
    public static final String EXPLOSION_FORMULA_PROOF_VERSION =
            "mcp908-1.7.10-to-minecraft-1.21.11-survives-explosion";
    public static final String EXPLOSION_INTERACTION_BLOCKER =
            "modern-explosion-destroy-without-decay-context-proof-pending";

    private static final String EXPLOSION_EVENT = "net/minecraftforge/event/world/ExplosionEvent";
    private static final String EXPLOSION_START_EVENT =
            "net/minecraftforge/event/world/ExplosionEvent$Start";
    private static final String EXPLOSION_DETONATE_EVENT =
            "net/minecraftforge/event/world/ExplosionEvent$Detonate";
    private static final String FML_EVENT = "cpw/mods/fml/common/eventhandler/Event";
    private static final Set<String> EXPLOSION_EVENT_TYPES = Set.of(
            EXPLOSION_EVENT,
            EXPLOSION_START_EVENT,
            EXPLOSION_DETONATE_EVENT,
            FML_EVENT
    );
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public String id() {
        return "legacy-block-drop-runtime-readiness";
    }

    @Override
    public void apply(ConversionContext context) throws IOException {
        Path plansPath = context.stagingDir().resolve(LegacyBlockDropAnalysisPass.PLAN_PATH);
        if (!Files.isRegularFile(plansPath)) return;

        JsonObject source = JsonParser.parseString(Files.readString(plansPath, StandardCharsets.UTF_8)).getAsJsonObject();
        int schema = source.has("schemaVersion") ? source.get("schemaVersion").getAsInt() : -1;
        if (schema != INPUT_SCHEMA) {
            throw new IOException("Unexpected block-drop plan schema " + schema
                    + "; runtime readiness expects " + INPUT_SCHEMA);
        }

        LegacyEventAnalyzer.Analysis eventAnalysis = new LegacyEventAnalyzer().analyze(context.sourceJar());
        List<LegacyEventAnalyzer.Binding> explosionHandlers = explosionHandlers(eventAnalysis);
        boolean explosionEventProofComplete = explosionHandlers.isEmpty() && eventAnalysis.diagnostics().isEmpty();
        boolean decayFormulaProofComplete = "inverse_explosion_size_1_7_10".equals(
                string(source, "legacyExplosionChanceMode"));

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 2);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("sourcePlanSchemaVersion", INPUT_SCHEMA);
        root.addProperty("lootRuntimeGenerated", false);
        root.addProperty("explosionDecayFormulaProofVersion", EXPLOSION_FORMULA_PROOF_VERSION);
        root.addProperty("explosionDecayFormulaProofComplete", decayFormulaProofComplete);
        root.addProperty("modernExplosionDecayCondition", "minecraft:survives_explosion");
        root.addProperty("sourceExplosionEventFree", explosionEventProofComplete);
        root.addProperty("explosionEventHandlerCount", explosionHandlers.size());
        root.addProperty("explosionAffectedSetSourceProofComplete", explosionEventProofComplete);
        root.addProperty("explosionInteractionCoverageComplete", false);
        root.addProperty("explosionRuntimeMappingReady", false);
        root.addProperty("explosionRuntimeBlocker", EXPLOSION_INTERACTION_BLOCKER);
        root.add("explosionEventHandlers", eventHandlers(explosionHandlers));
        JsonArray eventDiagnostics = new JsonArray();
        eventAnalysis.diagnostics().forEach(eventDiagnostics::add);
        root.add("eventAnalysisDiagnostics", eventDiagnostics);

        JsonArray ready = new JsonArray();
        JsonArray blocked = new JsonArray();
        JsonArray plans = source.getAsJsonArray("plans");
        if (plans != null) {
            for (JsonElement element : plans) {
                JsonObject plan = element.getAsJsonObject();
                List<String> reasons = readinessReasons(plan);
                JsonObject result = identity(plan);
                result.addProperty("normalSilkStaticSelfDropReady", reasons.isEmpty());
                result.addProperty("explosionSourceProofComplete", bool(plan, "explosionDropProofComplete"));
                result.addProperty("sourceExplosionDestructionOverrideFree",
                        bool(plan, "sourceExplosionDestructionOverrideFree"));
                result.addProperty("explosionDecayFormulaProofComplete", decayFormulaProofComplete);
                result.addProperty("explosionAffectedSetSourceProofComplete", explosionEventProofComplete);
                result.addProperty("explosionInteractionCoverageComplete", false);
                result.addProperty("explosionRuntimeMappingReady", false);
                result.addProperty("lootRuntimeGenerated", false);
                JsonArray reasonArray = new JsonArray();
                reasons.forEach(reasonArray::add);
                result.add("reasons", reasonArray);
                if (reasons.isEmpty()) {
                    result.addProperty("dropKind", "SELF_BLOCK_ITEM");
                    result.addProperty("quantity", 1);
                    result.addProperty("legacyDamage", 0);
                    result.addProperty("metadataIndependent", true);
                    ready.add(result);
                } else {
                    blocked.add(result);
                }
            }
        }
        root.add("ready", ready);
        root.add("blocked", blocked);
        root.addProperty("normalSilkStaticSelfDropReadyPlans", ready.size());
        root.addProperty("blockedPlans", blocked.size());

        Path output = context.stagingDir().resolve(OUTPUT_PATH);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
        context.diagnostics().info(
                "LFB-CONVERT-BLOCK-DROP-READINESS-0001",
                SupportLevel.RUNTIME_BRIDGE,
                "Classified static self-drop runtime readiness without enabling drops: ready="
                        + ready.size() + ", blocked=" + blocked.size()
                        + ", explosionHandlers=" + explosionHandlers.size()
                        + ". 1.7.10/1.21.11 decay formulas are separately proven; full explosion runtime remains gated."
        );
    }

    private static List<LegacyEventAnalyzer.Binding> explosionHandlers(LegacyEventAnalyzer.Analysis analysis) {
        List<LegacyEventAnalyzer.Binding> result = new ArrayList<>();
        for (LegacyEventAnalyzer.Binding binding : analysis.bindings()) {
            if (!EXPLOSION_EVENT_TYPES.contains(binding.eventType())) continue;
            if (binding.bus() == LegacyEventAnalyzer.Bus.FML) continue;
            result.add(binding);
        }
        return List.copyOf(result);
    }

    private static JsonArray eventHandlers(List<LegacyEventAnalyzer.Binding> handlers) {
        JsonArray values = new JsonArray();
        for (LegacyEventAnalyzer.Binding binding : handlers) {
            JsonObject value = new JsonObject();
            value.addProperty("handlerClass", binding.handlerClass());
            value.addProperty("method", binding.method());
            value.addProperty("descriptor", binding.descriptor());
            value.addProperty("eventType", binding.eventType());
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

    private static List<String> readinessReasons(JsonObject plan) {
        List<String> reasons = new ArrayList<>();
        if (!bool(plan, "modernIdentityComplete")) reasons.add("modern block/item identity incomplete");
        if (!bool(plan, "sourceDropPathOverrideFree")) reasons.add("source ordinary drop path is not proven default-compatible");
        if (!bool(plan, "normalDropProofComplete")) reasons.add("final normal drop proof incomplete");
        if (!bool(plan, "harvestEligibilityProofComplete")) reasons.add("harvest eligibility proof incomplete");

        JsonObject item = object(plan, "item");
        if (item == null || !"SELF_BLOCK_ITEM".equals(string(item, "kind"))) {
            reasons.add("normal drop item is not the converted block item");
        }
        if (!plan.has("quantity") || plan.get("quantity").getAsInt() != 1) {
            reasons.add("normal drop quantity is not exactly one");
        }
        if (!allZeroDamage(plan.getAsJsonArray("itemDamageByBlockMeta"))) {
            reasons.add("normal drop depends on legacy block metadata/item damage");
        }

        if (!bool(plan, "silkTouchProofComplete")) {
            reasons.add("silk-touch proof incomplete");
        } else if (plan.has("silkTouchEligible") && plan.get("silkTouchEligible").getAsBoolean()) {
            JsonObject stack = object(plan, "silkTouchStack");
            if (!sameSelfStack(plan, stack)) {
                reasons.add("silk-touch stack differs from static self block item count=1 damage=0");
            }
        }
        return reasons;
    }

    private static boolean sameSelfStack(JsonObject plan, JsonObject stack) {
        if (stack == null) return false;
        if (!"SELF_BLOCK_ITEM".equals(string(stack, "kind"))) return false;
        if (!stack.has("quantity") || stack.get("quantity").getAsInt() != 1) return false;
        if (!stack.has("legacyDamage") || stack.get("legacyDamage").getAsInt() != 0) return false;
        String planId = string(plan, "id");
        String stackId = string(stack, "modernId");
        return planId != null && planId.equals(stackId);
    }

    private static boolean allZeroDamage(JsonArray values) {
        if (values == null || values.size() != 16) return false;
        for (JsonElement value : values) {
            if (!value.isJsonPrimitive() || value.getAsInt() != 0) return false;
        }
        return true;
    }

    private static JsonObject identity(JsonObject plan) {
        JsonObject value = new JsonObject();
        copyString(plan, value, "legacyRegistryName");
        copyString(plan, value, "legacyNamespace");
        copyString(plan, value, "sourceClass");
        copyString(plan, value, "id");
        return value;
    }

    private static void copyString(JsonObject source, JsonObject target, String key) {
        String value = string(source, key);
        if (value != null) target.addProperty(key, value);
    }

    private static JsonObject object(JsonObject value, String key) {
        JsonElement element = value.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static String string(JsonObject value, String key) {
        JsonElement element = value.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    private static boolean bool(JsonObject value, String key) {
        JsonElement element = value.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsBoolean();
    }
}
