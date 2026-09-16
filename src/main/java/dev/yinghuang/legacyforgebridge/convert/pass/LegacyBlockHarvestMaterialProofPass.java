package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyBlockHarvestEligibilityAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyBlockMaterialProvenanceAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyMaterialHarvestRules1710;
import dev.yinghuang.legacyforgebridge.convert.LegacySourceMaterialHarvestAnalyzer;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Composes exact Minecraft/Forge 1.7.10 Material facts into the block-drop harvest proof.
 *
 * <p>This pass consumes the schema-v5 block-drop sidecar emitted immediately before it. It can
 * complete harvest eligibility only through Forge's first branch:
 * {@code block.getMaterial().isToolNotRequired() == true}. Held-tool classes/levels and the player
 * fallback remain deliberately uncompiled. Vanilla Material fields use the pinned MCP908 table;
 * stable source-owned direct Material singletons may use a separate source proof of the inherited
 * default no-tool state. Gameplay drop execution remains independently gated.</p>
 */
public final class LegacyBlockHarvestMaterialProofPass implements ConversionPass {
    public static final int INPUT_SCHEMA = 5;
    public static final int OUTPUT_SCHEMA = 6;
    public static final String RULE_VERSION = "minecraft-1.7.10-mcp908-forge-1.7.10-source-material-v2";
    public static final String GAMEPLAY_RUNTIME_BLOCKER = "block-drop-gameplay-runtime-pending";
    public static final String TOOL_PLAYER_ROUTE_BLOCKER = "harvest-tool-player-route-pending";
    public static final String MATERIAL_PROVENANCE_BLOCKER = "harvest-material-provenance-pending";
    public static final String MATERIAL_RULE_BLOCKER = "harvest-material-rule-unproven";
    public static final String MATERIAL_FAST_PATH_SOURCE_BLOCKER =
            "harvest-material-fast-path-source-runtime-pending";

    private static final Set<String> SUPERSEDED_BY_MATERIAL_FAST_PATH = Set.of(
            "harvest-eligibility-proof-pending",
            "harvest-eligibility-source-proof-missing",
            "harvest-eligibility-source-customization-runtime-pending",
            "harvest-check-event-runtime-pending",
            "harvest-check-event-absence-unproven"
    );
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public String id() {
        return "legacy-block-harvest-material-proof";
    }

    @Override
    public void apply(ConversionContext context) throws IOException {
        Path sidecar = context.stagingDir().resolve(LegacyBlockDropAnalysisPass.PLAN_PATH);
        if (!Files.isRegularFile(sidecar)) return;

        JsonObject root = JsonParser.parseString(Files.readString(sidecar, StandardCharsets.UTF_8)).getAsJsonObject();
        int schema = root.has("schemaVersion") ? root.get("schemaVersion").getAsInt() : -1;
        if (schema != INPUT_SCHEMA) {
            throw new IOException("Unexpected block-drop plan schema " + schema
                    + "; harvest Material proof expects " + INPUT_SCHEMA);
        }

        LegacyBlockHarvestEligibilityAnalyzer.Analysis harvestAnalysis =
                new LegacyBlockHarvestEligibilityAnalyzer().analyze(context.sourceJar());
        LegacyBlockMaterialProvenanceAnalyzer.Analysis materialAnalysis =
                new LegacyBlockMaterialProvenanceAnalyzer().analyze(context.sourceJar());
        LegacySourceMaterialHarvestAnalyzer.Analysis sourceMaterialAnalysis =
                new LegacySourceMaterialHarvestAnalyzer().analyze(context.sourceJar());

        Map<BlockKey, LegacyBlockHarvestEligibilityAnalyzer.Proof> harvestProofs = new LinkedHashMap<>();
        for (LegacyBlockHarvestEligibilityAnalyzer.Proof proof : harvestAnalysis.proofs()) {
            harvestProofs.put(new BlockKey(proof.legacyNamespace(), proof.registryName()), proof);
        }
        Map<BlockKey, LegacyBlockMaterialProvenanceAnalyzer.Proof> materialProofs = new LinkedHashMap<>();
        for (LegacyBlockMaterialProvenanceAnalyzer.Proof proof : materialAnalysis.proofs()) {
            materialProofs.put(new BlockKey(proof.legacyNamespace(), proof.registryName()), proof);
        }
        Map<LegacyBlockMaterialProvenanceAnalyzer.MaterialRef, LegacySourceMaterialHarvestAnalyzer.Proof>
                sourceMaterialProofs = new LinkedHashMap<>();
        for (LegacySourceMaterialHarvestAnalyzer.Proof proof : sourceMaterialAnalysis.proofs()) {
            sourceMaterialProofs.put(proof.material(), proof);
        }

        int completed = 0;
        JsonArray plans = root.getAsJsonArray("plans");
        if (plans != null) {
            for (JsonElement element : plans) {
                JsonObject plan = element.getAsJsonObject();
                BlockKey key = new BlockKey(
                        optionalString(plan, "legacyNamespace"),
                        plan.get("legacyRegistryName").getAsString()
                );
                LegacyBlockHarvestEligibilityAnalyzer.Proof harvest = harvestProofs.get(key);
                LegacyBlockMaterialProvenanceAnalyzer.Proof material = materialProofs.get(key);

                boolean sourceSafe = harvest != null && harvest.materialFastPathSourceSafe();
                plan.addProperty("materialFastPathSourceSafe", sourceSafe);
                JsonArray fastPathReasons = new JsonArray();
                if (harvest != null) harvest.materialFastPathReasons().forEach(fastPathReasons::add);
                plan.add("materialFastPathSourceReasons", fastPathReasons);

                boolean provenanceComplete = material != null && material.complete() && material.material() != null;
                plan.addProperty("legacyMaterialProvenanceComplete", provenanceComplete);
                JsonArray materialReasons = new JsonArray();
                if (material != null) material.reasons().forEach(materialReasons::add);

                Optional<LegacyMaterialHarvestRules1710.Rule> vanillaRule = Optional.empty();
                LegacySourceMaterialHarvestAnalyzer.Proof sourceRule = null;
                Boolean toolNotRequired = null;
                String namedMaterial = null;
                boolean sourceOwnedRule = false;
                if (provenanceComplete) {
                    LegacyBlockMaterialProvenanceAnalyzer.MaterialRef ref = material.material();
                    vanillaRule = LegacyMaterialHarvestRules1710.lookup(
                            ref.owner(), ref.fieldName(), ref.descriptor());
                    if (vanillaRule.isPresent()) {
                        namedMaterial = vanillaRule.get().namedMaterial();
                        toolNotRequired = vanillaRule.get().toolNotRequired();
                    } else {
                        sourceRule = sourceMaterialProofs.get(ref);
                        if (sourceRule != null) {
                            sourceRule.reasons().forEach(materialReasons::add);
                            if (sourceRule.complete() && sourceRule.toolNotRequired() != null) {
                                namedMaterial = "source:" + ref.owner() + "#" + ref.fieldName();
                                toolNotRequired = sourceRule.toolNotRequired();
                                sourceOwnedRule = true;
                            }
                        }
                    }

                    JsonObject legacyMaterial = new JsonObject();
                    legacyMaterial.addProperty("owner", ref.owner());
                    legacyMaterial.addProperty("fieldName", ref.fieldName());
                    legacyMaterial.addProperty("descriptor", ref.descriptor());
                    if (namedMaterial != null && toolNotRequired != null) {
                        legacyMaterial.addProperty("namedMaterial", namedMaterial);
                        legacyMaterial.addProperty("toolNotRequired", toolNotRequired);
                        legacyMaterial.addProperty("sourceOwnedRule", sourceOwnedRule);
                        if (sourceOwnedRule) {
                            legacyMaterial.addProperty("sourceProofMode",
                                    "direct_material_default_without_set_requires_tool");
                        }
                    }
                    plan.add("legacyMaterial", legacyMaterial);
                }
                plan.add("legacyMaterialReasons", materialReasons);

                boolean ruleKnown = toolNotRequired != null;
                plan.addProperty("legacyMaterialHarvestRuleKnown", ruleKnown);
                boolean fastPathComplete = sourceSafe && provenanceComplete && ruleKnown && toolNotRequired;
                plan.addProperty("materialFastPathHarvestEligibilityProofComplete", fastPathComplete);
                plan.addProperty("harvestEligibilityProofComplete", fastPathComplete);
                if (fastPathComplete) {
                    plan.addProperty("harvestEligibilityMode", sourceOwnedRule
                            ? "source_material_tool_not_required_1_7_10"
                            : "material_tool_not_required_1_7_10");
                    completed++;
                }

                plan.add("runtimeBlockers", rewriteBlockers(
                        plan.getAsJsonArray("runtimeBlockers"),
                        fastPathComplete,
                        harvest,
                        provenanceComplete,
                        ruleKnown,
                        toolNotRequired
                ));
            }
        }

        root.addProperty("schemaVersion", OUTPUT_SCHEMA);
        root.addProperty("materialHarvestRuleVersion", RULE_VERSION);
        root.addProperty("harvestEligibilityProofCompletePlans", completed);
        root.addProperty("gameplayDropRuntimeWired", false);

        JsonArray materialDiagnostics = new JsonArray();
        materialAnalysis.diagnostics().forEach(materialDiagnostics::add);
        root.add("materialProvenanceAnalysisDiagnostics", materialDiagnostics);
        JsonArray sourceMaterialDiagnostics = new JsonArray();
        sourceMaterialAnalysis.diagnostics().forEach(sourceMaterialDiagnostics::add);
        root.add("sourceMaterialHarvestAnalysisDiagnostics", sourceMaterialDiagnostics);

        Files.writeString(sidecar, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
        context.diagnostics().info(
                "LFB-CONVERT-BLOCK-HARVEST-0001",
                SupportLevel.RUNTIME_BRIDGE,
                "Completed exact 1.7.10 no-tool Material harvest eligibility for plans=" + completed
                        + "; held-tool/player routes and gameplay drop wiring remain gated."
        );
    }

    private static JsonArray rewriteBlockers(
            JsonArray existing,
            boolean fastPathComplete,
            LegacyBlockHarvestEligibilityAnalyzer.Proof harvest,
            boolean provenanceComplete,
            boolean ruleKnown,
            Boolean toolNotRequired
    ) {
        LinkedHashSet<String> blockers = new LinkedHashSet<>();
        if (existing != null) {
            for (JsonElement value : existing) blockers.add(value.getAsString());
        }

        if (fastPathComplete) {
            blockers.removeAll(SUPERSEDED_BY_MATERIAL_FAST_PATH);
            blockers.remove(MATERIAL_FAST_PATH_SOURCE_BLOCKER);
            blockers.remove(MATERIAL_PROVENANCE_BLOCKER);
            blockers.remove(MATERIAL_RULE_BLOCKER);
            blockers.remove(TOOL_PLAYER_ROUTE_BLOCKER);
        } else {
            if (harvest == null || !harvest.materialFastPathSourceSafe()) {
                blockers.add(MATERIAL_FAST_PATH_SOURCE_BLOCKER);
            }
            if (!provenanceComplete) {
                blockers.add(MATERIAL_PROVENANCE_BLOCKER);
            } else if (!ruleKnown) {
                blockers.add(MATERIAL_RULE_BLOCKER);
            } else if (!Boolean.TRUE.equals(toolNotRequired)) {
                blockers.add(TOOL_PLAYER_ROUTE_BLOCKER);
            }
        }

        blockers.add(GAMEPLAY_RUNTIME_BLOCKER);
        JsonArray values = new JsonArray();
        blockers.forEach(values::add);
        return values;
    }

    private static String optionalString(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) return null;
        String value = object.get(key).getAsString();
        return value.isBlank() ? null : value;
    }

    private record BlockKey(String legacyNamespace, String registryName) { }
}
