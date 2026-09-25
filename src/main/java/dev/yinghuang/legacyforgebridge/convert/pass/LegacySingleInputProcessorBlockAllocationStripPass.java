package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyBlockAllocationResidueStripper;
import dev.yinghuang.legacyforgebridge.convert.LegacySingleInputProcessorBlockAllocationAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Materializes source-proven inline allocation-site Block property effects, then removes the exact
 * discarded source Block allocation left behind by registerBlock neutralization.
 */
public final class LegacySingleInputProcessorBlockAllocationStripPass
        implements ConversionPass {
    public static final String OUTPUT =
            "legacyforgebridge/single-input-processor-block-source-allocation-strip.json";
    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().create();

    @Override
    public String id() {
        return "legacy-single-input-processor-block-source-allocation-strip";
    }

    @Override
    public void apply(ConversionContext context) throws Exception {
        Path processorPath =
                context.stagingDir().resolve(LegacySingleInputProcessorPass.OUTPUT);
        Path constructionPath = context.stagingDir().resolve(
                LegacySingleInputProcessorBlockConstructionPass.OUTPUT);
        Path registrationPath = context.stagingDir().resolve(
                LegacySingleInputProcessorBlockRegistrationStripPass.OUTPUT);
        if (!Files.isRegularFile(processorPath)
                || !Files.isRegularFile(constructionPath)
                || !Files.isRegularFile(registrationPath)) {
            return;
        }

        JsonObject processor = read(processorPath);
        JsonObject construction = read(constructionPath);
        JsonObject registration = read(registrationPath);
        if (integer(processor, "schemaVersion", -1) != 4
                || integer(construction, "schemaVersion", -1) != 1
                || integer(registration, "schemaVersion", -1) != 1
                || !context.sourceHash().equals(
                        string(processor, "sourceSha256", ""))
                || !context.sourceHash().equals(
                        string(construction, "sourceSha256", ""))
                || !context.sourceHash().equals(
                        string(registration, "sourceSha256", ""))) {
            return;
        }

        Map<String, JsonObject> constructionByBlock =
                index(construction, "rules", "sourceBlockClass");
        Map<String, JsonObject> registrationByBlock =
                index(registration, "rules", "sourceBlockClass");

        LegacySingleInputProcessorBlockAllocationAnalyzer.Analysis analysis =
                new LegacySingleInputProcessorBlockAllocationAnalyzer()
                        .analyze(context.sourceJar());
        Map<String, LegacySingleInputProcessorBlockAllocationAnalyzer.Proof>
                proofByBlock = new LinkedHashMap<>();
        for (var proof : analysis.proofs()) {
            proofByBlock.putIfAbsent(proof.sourceBlockClass(), proof);
        }

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("sourceAllocationStripWired", true);
        root.addProperty("constructorReplacementRequired", true);
        root.addProperty("blockRegistrationStripRequired", true);
        root.addProperty("allocationPropertyEffectsRuntimeWired", true);
        root.addProperty("freshPostStripReferenceCheckWired", true);
        root.addProperty("inlineAllocationOnly", true);
        root.addProperty("staticFieldAllocationRetirementWired", false);
        root.addProperty("sourceClassDeletionWired", false);

        JsonArray rules = new JsonArray();
        int complete = 0;
        int blocked = 0;
        int strippedSites = 0;
        int runtimeEffects = 0;

        for (JsonElement element : array(processor, "machines")) {
            if (!element.isJsonObject()) continue;
            JsonObject machine = element.getAsJsonObject();
            if (!bool(machine, "runtimeComplete", false)
                    || !bool(machine, "baseRuntimeComplete", false)
                    || !bool(machine, "sourcePresentationComplete", false)) {
                continue;
            }

            String id = string(machine, "id", null);
            String sourceBlockClass =
                    string(machine, "sourceBlockClass", null);
            if (sourceBlockClass == null) continue;

            JsonObject value = new JsonObject();
            if (id != null) value.addProperty("id", id);
            value.addProperty("sourceBlockClass", sourceBlockClass);
            value.addProperty("inlineAllocationOnly", true);
            value.addProperty("staticFieldAllocationRetirementWired", false);

            JsonArray blockers = new JsonArray();
            JsonObject constructionRule =
                    constructionByBlock.get(sourceBlockClass);
            JsonObject registrationRule =
                    registrationByBlock.get(sourceBlockClass);
            var proof = proofByBlock.get(sourceBlockClass);

            boolean constructorReplacement =
                    constructionRule != null
                            && bool(constructionRule,
                            "blockConstructorReplacementProven", false)
                            && bool(constructionRule,
                            "modernBlockPropertiesRuntimeWired", false)
                            && bool(machine,
                            "blockConstructorReplacementProven", false)
                            && bool(machine,
                            "blockConstructionRuntimeWired", false)
                            && object(machine, "blockConstruction") != null;
            if (!constructorReplacement) {
                blockers.add("block-constructor-replacement-incomplete");
            }

            boolean registrationStrip =
                    registrationRule != null
                            && bool(registrationRule,
                            "blockRegistrationStripComplete", false)
                            && integer(registrationRule,
                            "strippedBlockRegistrationSites", 0) == 1;
            if (!registrationStrip) {
                blockers.add("block-registration-strip-incomplete");
            }

            boolean allocationProof =
                    proof != null && proof.allocationProofComplete();
            if (!allocationProof) {
                blockers.add("source-block-inline-allocation-proof-incomplete");
                if (proof != null) {
                    proof.blockers().forEach(blockers::add);
                }
            }

            if (proof != null) {
                if (proof.sourceOwner() != null) {
                    value.addProperty("sourceOwner", proof.sourceOwner());
                }
                if (proof.sourceMethod() != null) {
                    value.addProperty("sourceMethod", proof.sourceMethod());
                }
                if (proof.sourceDescriptor() != null) {
                    value.addProperty(
                            "sourceDescriptor", proof.sourceDescriptor());
                }
                if (proof.registerBlockDescriptor() != null) {
                    value.addProperty(
                            "registerBlockDescriptor",
                            proof.registerBlockDescriptor());
                }
                if (proof.sourceConstructor() != null) {
                    value.addProperty(
                            "sourceConstructor", proof.sourceConstructor());
                }
                value.addProperty(
                        "inlineAllocationProven",
                        proof.inlineAllocationProven());
                value.addProperty(
                        "allocationControlFlowSimple",
                        proof.allocationControlFlowSimple());
                value.addProperty(
                        "allocationSetterEffectsSupported",
                        proof.allocationSetterEffectsSupported());
                value.add("allocationEffects", effects(proof));
            } else {
                value.addProperty("inlineAllocationProven", false);
                value.addProperty("allocationControlFlowSimple", false);
                value.addProperty(
                        "allocationSetterEffectsSupported", false);
                value.add("allocationEffects", new JsonArray());
            }

            if (allocationProof && registrationRule != null) {
                if (!same(
                        proof.sourceOwner(),
                        string(registrationRule, "sourceOwner", null))
                        || !same(
                        proof.sourceMethod(),
                        string(registrationRule, "sourceMethod", null))
                        || !same(
                        proof.sourceDescriptor(),
                        string(registrationRule,
                                "sourceDescriptor", null))) {
                    blockers.add(
                            "source-block-allocation-registration-owner-drift");
                }
                String registryName = string(
                        registrationRule, "legacyRegistryName", null);
                if (!same(proof.registryName(), registryName)) {
                    blockers.add(
                            "source-block-allocation-registration-name-drift");
                }
            }

            JsonObject effectiveProperties = null;
            int ruleStrippedSites = 0;
            boolean effectsRuntimeWired = false;

            if (blockers.isEmpty() && proof != null) {
                JsonObject base = object(machine, "blockConstruction");
                effectiveProperties = applyEffects(
                        base, proof.effects(), blockers);
            }

            if (blockers.isEmpty()
                    && proof != null
                    && effectiveProperties != null) {
                Path classPath = context.stagingDir().resolve(
                        proof.sourceOwner() + ".class");
                if (!Files.isRegularFile(classPath)) {
                    blockers.add("source-block-allocation-owner-class-missing");
                } else {
                    var target =
                            new LegacyBlockAllocationResidueStripper.Target(
                                    proof.sourceMethod(),
                                    proof.sourceDescriptor(),
                                    sourceBlockClass,
                                    proof.sourceConstructor(),
                                    proof.effects());
                    var result = new LegacyBlockAllocationResidueStripper()
                            .strip(Files.readAllBytes(classPath), target);
                    ruleStrippedSites = result.strippedSites();
                    result.blockers().forEach(blockers::add);

                    if (ruleStrippedSites == 1 && blockers.isEmpty()) {
                        Files.write(classPath, result.bytes());
                        machine.add(
                                "blockConstruction",
                                effectiveProperties.deepCopy());
                        machine.addProperty(
                                "blockAllocationEffectsRuntimeWired", true);
                        machine.addProperty(
                                "blockSourceAllocationStripComplete", true);
                        effectsRuntimeWired = true;
                    }
                }
            }

            boolean stripComplete =
                    ruleStrippedSites == 1 && blockers.isEmpty();
            if (!stripComplete) {
                machine.addProperty(
                        "blockAllocationEffectsRuntimeWired", false);
                machine.addProperty(
                        "blockSourceAllocationStripComplete", false);
            }

            value.addProperty(
                    "constructorReplacementProven",
                    constructorReplacement);
            value.addProperty(
                    "blockRegistrationStripComplete",
                    registrationStrip);
            value.addProperty(
                    "sourceAllocationProofComplete",
                    allocationProof);
            value.addProperty(
                    "allocationPropertyEffectsRuntimeWired",
                    effectsRuntimeWired);
            value.addProperty(
                    "sourceAllocationStripComplete",
                    stripComplete);
            value.addProperty(
                    "strippedSourceAllocationSites",
                    ruleStrippedSites);
            value.addProperty(
                    "sourceClassDeletionAuthorized", false);
            if (effectiveProperties != null) {
                value.add(
                        "effectiveBlockConstruction",
                        effectiveProperties.deepCopy());
            }
            value.add("blockers", blockers);
            rules.add(value);

            if (stripComplete) {
                complete++;
                strippedSites += ruleStrippedSites;
                if (effectsRuntimeWired) runtimeEffects++;
            } else {
                blocked++;
            }
        }

        processor.addProperty(
                "blockSourceAllocationStripAnalysisWired", true);
        processor.addProperty(
                "blockSourceAllocationStripCompleteMachines", complete);
        processor.addProperty(
                "blockSourceAllocationStripBlockedMachines", blocked);
        Files.writeString(
                processorPath,
                GSON.toJson(processor) + "\n",
                StandardCharsets.UTF_8);

        root.add("rules", rules);
        root.addProperty("evaluatedRuntimeRules", rules.size());
        root.addProperty(
                "sourceAllocationStripCompleteRules", complete);
        root.addProperty(
                "blockedSourceAllocationStripRules", blocked);
        root.addProperty(
                "strippedSourceAllocationSites", strippedSites);
        root.addProperty(
                "allocationEffectsRuntimeWiredRules", runtimeEffects);

        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(
                output,
                GSON.toJson(root) + "\n",
                StandardCharsets.UTF_8);

        if (complete > 0) {
            context.diagnostics().info(
                    "LFB-CONVERT-PROCESSOR-BLOCKALLOC-0001",
                    SupportLevel.ADAPTED,
                    "Retired " + strippedSites
                            + " exact inline processor source Block allocation(s) "
                            + "after materializing source-proven fluent property "
                            + "effects into the modern Block runtime.");
        }
        if (blocked > 0) {
            context.diagnostics().warning(
                    "LFB-CONVERT-PROCESSOR-BLOCKALLOC-0002",
                    SupportLevel.RUNTIME_BRIDGE,
                    "Kept " + blocked
                            + " processor source Block allocation(s) because "
                            + "constructor replacement, registration retirement, "
                            + "inline provenance, supported fluent effects, or "
                            + "post-strip reference closure was incomplete.");
        }
        for (String diagnostic : analysis.diagnostics()) {
            context.diagnostics().warning(
                    "LFB-CONVERT-PROCESSOR-BLOCKALLOC-0003",
                    SupportLevel.MANUAL_REQUIRED,
                    diagnostic);
        }
    }

    private static JsonObject applyEffects(
            JsonObject base,
            java.util.List<
                    LegacySingleInputProcessorBlockAllocationAnalyzer.Effect>
                    effects,
            JsonArray blockers) {
        if (base == null) {
            blockers.add("base-block-construction-properties-missing");
            return null;
        }

        float destroyTime =
                (float) decimal(base, "destroyTime", Float.NaN);
        float explosionResistance =
                (float) decimal(base, "explosionResistance", Float.NaN);
        String soundType = string(base, "soundType", null);
        String mapColor = string(base, "mapColor", null);
        int lightLevel = integer(base, "lightLevel", -1);
        if (!Float.isFinite(destroyTime) || destroyTime < 0.0F
                || !Float.isFinite(explosionResistance)
                || explosionResistance < 0.0F
                || soundType == null || mapColor == null
                || lightLevel < 0 || lightLevel > 15) {
            blockers.add("base-block-construction-properties-invalid");
            return null;
        }

        float internalResistance = explosionResistance * 5.0F;
        for (var effect : effects) {
            switch (effect.kind()) {
                case HARDNESS -> {
                    Float value = effect.floatValue();
                    if (value == null || !Float.isFinite(value)
                            || value < 0.0F) {
                        blockers.add(
                                "allocation-hardness-effect-invalid");
                        return null;
                    }
                    destroyTime = value;
                    internalResistance =
                            Math.max(internalResistance, value * 5.0F);
                }
                case RESISTANCE -> {
                    Float value = effect.floatValue();
                    if (value == null || !Float.isFinite(value)
                            || value < 0.0F) {
                        blockers.add(
                                "allocation-resistance-effect-invalid");
                        return null;
                    }
                    internalResistance = value * 3.0F;
                }
                case LIGHT_LEVEL -> {
                    Float value = effect.floatValue();
                    if (value == null || !Float.isFinite(value)
                            || value < 0.0F || value > 1.0F) {
                        blockers.add(
                                "allocation-light-effect-invalid");
                        return null;
                    }
                    lightLevel = (int) (15.0F * value);
                }
                case SOUND -> {
                    String value = effect.textValue();
                    if (value == null || value.isBlank()) {
                        blockers.add(
                                "allocation-sound-effect-invalid");
                        return null;
                    }
                    soundType = value;
                }
            }
        }

        JsonObject result = new JsonObject();
        result.addProperty("destroyTime", destroyTime);
        result.addProperty(
                "explosionResistance", internalResistance / 5.0F);
        result.addProperty("soundType", soundType);
        result.addProperty("mapColor", mapColor);
        result.addProperty("lightLevel", lightLevel);
        return result;
    }

    private static JsonArray effects(
            LegacySingleInputProcessorBlockAllocationAnalyzer.Proof proof) {
        JsonArray output = new JsonArray();
        for (var effect : proof.effects()) {
            JsonObject value = new JsonObject();
            value.addProperty("kind", effect.kind().name());
            value.addProperty("owner", effect.owner());
            value.addProperty("methodName", effect.methodName());
            value.addProperty(
                    "methodDescriptor", effect.methodDescriptor());
            if (effect.floatValue() != null) {
                value.addProperty(
                        "floatValue", effect.floatValue());
            }
            if (effect.textValue() != null) {
                value.addProperty(
                        "textValue", effect.textValue());
            }
            output.add(value);
        }
        return output;
    }

    private static Map<String, JsonObject> index(
            JsonObject root, String arrayName, String keyName) {
        Map<String, JsonObject> result = new LinkedHashMap<>();
        for (JsonElement element : array(root, arrayName)) {
            if (!element.isJsonObject()) continue;
            JsonObject value = element.getAsJsonObject();
            String key = string(value, keyName, null);
            if (key != null) result.putIfAbsent(key, value);
        }
        return result;
    }

    private static boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static JsonObject read(Path path) throws Exception {
        return JsonParser.parseString(
                Files.readString(path, StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    private static JsonObject object(JsonObject root, String name) {
        JsonElement value = root == null ? null : root.get(name);
        return value != null && value.isJsonObject()
                ? value.getAsJsonObject() : null;
    }

    private static JsonArray array(JsonObject root, String name) {
        JsonElement value = root == null ? null : root.get(name);
        return value != null && value.isJsonArray()
                ? value.getAsJsonArray() : new JsonArray();
    }

    private static boolean bool(
            JsonObject root, String name, boolean fallback) {
        JsonElement value = root == null ? null : root.get(name);
        return value != null && value.isJsonPrimitive()
                ? value.getAsBoolean() : fallback;
    }

    private static int integer(
            JsonObject root, String name, int fallback) {
        JsonElement value = root == null ? null : root.get(name);
        return value != null && value.isJsonPrimitive()
                ? value.getAsInt() : fallback;
    }

    private static double decimal(
            JsonObject root, String name, double fallback) {
        JsonElement value = root == null ? null : root.get(name);
        return value != null && value.isJsonPrimitive()
                ? value.getAsDouble() : fallback;
    }

    private static String string(
            JsonObject root, String name, String fallback) {
        JsonElement value = root == null ? null : root.get(name);
        return value != null && value.isJsonPrimitive()
                ? value.getAsString() : fallback;
    }
}
