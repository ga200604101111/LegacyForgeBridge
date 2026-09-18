package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyCandidateReferenceAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyClassDependencyAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Computes source-retirement readiness for runtime-complete single-input processors without
 * deleting any class. Registration retirement and candidate reference closure are evidence;
 * constructor/allocation/GUI retirement remain independent mandatory gates.
 */
public final class LegacySingleInputProcessorRetirementReadiness {
    public static final String OUTPUT =
            "legacyforgebridge/single-input-processor-retirement-readiness.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private LegacySingleInputProcessorRetirementReadiness() { }

    public static void materialize(
            ConversionContext context,
            LegacyClassDependencyAnalyzer.Analysis dependencies) throws Exception {
        Path processorPath =
                context.stagingDir().resolve(LegacySingleInputProcessorPass.OUTPUT);
        Path tileStripPath = context.stagingDir().resolve(
                LegacySingleInputProcessorTileRegistrationStripPass.OUTPUT);
        Path blockStripPath = context.stagingDir().resolve(
                LegacySingleInputProcessorBlockRegistrationStripPass.OUTPUT);
        Path tileConstructionPath = context.stagingDir().resolve(
                LegacySingleInputProcessorTileConstructionPass.OUTPUT);
        Path guiProofPath = context.stagingDir().resolve(
                LegacySingleInputProcessorGuiHandlerProofPass.OUTPUT);
        Path guiStripPath = context.stagingDir().resolve(
                LegacySingleInputProcessorGuiHandlerStripPass.OUTPUT);
        if (!Files.isRegularFile(processorPath)
                || !Files.isRegularFile(tileStripPath)
                || !Files.isRegularFile(blockStripPath)) {
            return;
        }

        JsonObject processor = read(processorPath);
        JsonObject tileStrip = read(tileStripPath);
        JsonObject blockStrip = read(blockStripPath);
        JsonObject tileConstruction = Files.isRegularFile(tileConstructionPath)
                ? read(tileConstructionPath) : null;
        boolean tileConstructionValid = tileConstruction != null
                && integer(tileConstruction, "schemaVersion", -1) == 1
                && context.sourceHash().equals(
                        string(tileConstruction, "sourceSha256", ""));
        JsonObject guiProof = Files.isRegularFile(guiProofPath)
                ? read(guiProofPath) : null;
        boolean guiProofValid = guiProof != null
                && integer(guiProof, "schemaVersion", -1) == 1
                && context.sourceHash().equals(
                        string(guiProof, "sourceSha256", ""));
        JsonObject guiStrip = Files.isRegularFile(guiStripPath)
                ? read(guiStripPath) : null;
        boolean guiStripValid = guiStrip != null
                && integer(guiStrip, "schemaVersion", -1) == 1
                && context.sourceHash().equals(
                        string(guiStrip, "sourceSha256", ""))
                && bool(guiStrip, "guiHandlerBranchStripWired", false);
        if (integer(processor, "schemaVersion", -1) != 4
                || integer(tileStrip, "schemaVersion", -1) != 1
                || integer(blockStrip, "schemaVersion", -1) != 1
                || !context.sourceHash().equals(string(processor, "sourceSha256", ""))
                || !context.sourceHash().equals(string(tileStrip, "sourceSha256", ""))
                || !context.sourceHash().equals(string(blockStrip, "sourceSha256", ""))) {
            return;
        }

        Map<String, LegacyClassDependencyAnalyzer.ClassDependency> dependencyByClass =
                new LinkedHashMap<>();
        for (var dependency : dependencies.classes()) {
            dependencyByClass.put(dependency.sourceClass(), dependency);
        }

        Map<String, JsonObject> tileStripByClass = index(
                tileStrip, "rules", "sourceTileClass");
        Map<String, JsonObject> blockStripByClass = index(
                blockStrip, "rules", "sourceBlockClass");
        Map<String, JsonObject> tileConstructionByClass = tileConstructionValid
                ? index(tileConstruction, "rules", "sourceTileClass")
                : Map.of();
        Map<String, JsonObject> guiProofByBlock = guiProofValid
                ? index(guiProof, "rules", "sourceBlockClass")
                : Map.of();
        Map<String, JsonObject> guiStripByBlock = guiStripValid
                ? index(guiStrip, "rules", "sourceBlockClass")
                : Map.of();

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("retirementReadinessAnalysisWired", true);
        root.addProperty("runtimeCompleteRequired", true);
        root.addProperty("tileRegistrationRetirementRequired", true);
        root.addProperty("blockRegistrationRetirementRequired", true);
        root.addProperty("blockConstructorReplacementRequired", true);
        root.addProperty("blockSourceAllocationRetirementRequired", true);
        root.addProperty("tileConstructorReplacementRequired", true);
        root.addProperty("tileConstructorReplacementAnalysisWired",
                tileConstructionValid);
        root.addProperty("guiHandlerRetirementRequired", true);
        root.addProperty("guiHandlerBranchProofRequired", true);
        root.addProperty("guiHandlerBranchProofAnalysisWired", guiProofValid);
        root.addProperty("guiHandlerBranchStripRequired", true);
        root.addProperty("guiHandlerBranchStripAnalysisWired", guiStripValid);
        root.addProperty("processorPresentationCohortExpansionWired", true);
        root.addProperty("nestedCompanionCohortExpansionWired", true);
        root.addProperty("retirementAuthorizationWired", false);
        root.addProperty("sourceClassDeletionWired", false);

        JsonArray rules = new JsonArray();
        int ready = 0;
        int blocked = 0;

        for (JsonElement element : array(processor, "machines")) {
            if (!element.isJsonObject()) continue;
            JsonObject machine = element.getAsJsonObject();
            if (!bool(machine, "runtimeComplete", false)
                    || !bool(machine, "baseRuntimeComplete", false)
                    || !bool(machine, "sourcePresentationComplete", false)) {
                continue;
            }

            String id = string(machine, "id", null);
            String blockClass = string(machine, "sourceBlockClass", null);
            String tileClass = string(machine, "sourceTileClass", null);
            if (blockClass == null || tileClass == null || blockClass.equals(tileClass)) {
                continue;
            }

            JsonObject guiProofRule = guiProofByBlock.get(blockClass);
            boolean guiHandlerBranchProofComplete = guiProofValid
                    && guiProofRule != null
                    && bool(guiProofRule,
                    "guiHandlerBranchProofComplete", false);
            JsonObject guiStripRule = guiStripByBlock.get(blockClass);
            boolean guiHandlerBranchStripComplete = guiStripValid
                    && guiStripRule != null
                    && bool(guiStripRule,
                    "guiHandlerBranchStripComplete", false)
                    && integer(guiStripRule, "strippedGuiHandlerBranches", 0) == 2;
            boolean guiHandlerRetirementComplete =
                    guiHandlerBranchProofComplete && guiHandlerBranchStripComplete;

            String guiHandlerClass = guiProofRule == null
                    ? null : string(guiProofRule, "handlerClass", null);
            String sourceContainerClass = guiProofRule == null
                    ? null : string(guiProofRule, "sourceContainerClass", null);
            String sourceGuiClass = guiProofRule == null
                    ? null : string(guiProofRule, "sourceGuiClass", null);

            LinkedHashSet<String> presentationClasses = new LinkedHashSet<>();
            if (guiHandlerRetirementComplete) {
                if (sourceContainerClass != null
                        && !sourceContainerClass.equals(blockClass)
                        && !sourceContainerClass.equals(tileClass)) {
                    presentationClasses.add(sourceContainerClass);
                }
                if (sourceGuiClass != null
                        && !sourceGuiClass.equals(blockClass)
                        && !sourceGuiClass.equals(tileClass)) {
                    presentationClasses.add(sourceGuiClass);
                }
            }

            LinkedHashSet<String> baseCohort = new LinkedHashSet<>();
            baseCohort.add(blockClass);
            baseCohort.add(tileClass);
            baseCohort.addAll(presentationClasses);
            LinkedHashSet<String> companions =
                    discoverNestedCompanions(context.stagingDir(), baseCohort);
            LinkedHashSet<String> cohort = new LinkedHashSet<>(baseCohort);
            cohort.addAll(companions);

            LegacyCandidateReferenceAnalyzer.Analysis references =
                    new LegacyCandidateReferenceAnalyzer().analyze(
                            context.stagingDir(), cohort);

            LinkedHashSet<String> blockers = new LinkedHashSet<>();
            if (!references.classReferenceClosureComplete()) {
                blockers.add("candidate-class-reference-scan-incomplete");
            }
            if (!references.resourceReferenceClosureComplete()) {
                blockers.add("candidate-resource-reference-scan-incomplete");
            }

            JsonObject tileRule = tileStripByClass.get(tileClass);
            if (!bool(tileStrip, "tileRegistrationStripWired", false)
                    || tileRule == null
                    || !bool(tileRule, "tileRegistrationStripComplete", false)
                    || integer(tileRule, "strippedTileRegistrationSites", 0) != 1) {
                blockers.add("tile-registration-strip-incomplete");
            }

            JsonObject blockRule = blockStripByClass.get(blockClass);
            if (!bool(blockStrip, "blockRegistrationStripWired", false)
                    || blockRule == null
                    || !bool(blockRule, "blockRegistrationStripComplete", false)
                    || integer(blockRule, "strippedBlockRegistrationSites", 0) != 1) {
                blockers.add("block-registration-strip-incomplete");
            }

            JsonObject tileConstructionRule =
                    tileConstructionByClass.get(tileClass);
            boolean tileConstructorReplacementProven = tileConstructionValid
                    && tileConstructionRule != null
                    && bool(tileConstructionRule,
                    "tileConstructorReplacementProven", false);
            if (!tileConstructorReplacementProven) {
                blockers.add("processor-tile-constructor-replacement-not-wired");
            }

            if (!guiHandlerBranchProofComplete) {
                blockers.add("processor-gui-handler-branch-proof-incomplete");
            } else if (!guiStripValid) {
                blockers.add("processor-gui-handler-branch-strip-not-wired");
            } else if (!guiHandlerBranchStripComplete) {
                blockers.add("processor-gui-handler-branch-strip-incomplete");
            }

            // These remain intentionally separate gates. Runtime completeness proves the modern
            // behavior, but does not itself prove source Block constructor/global side effects.
            blockers.add("processor-block-constructor-replacement-not-wired");
            blockers.add("processor-block-source-allocation-retirement-not-wired");

            inspectClass(
                    "block", blockClass, cohort,
                    dependencyByClass.get(blockClass),
                    references.forTarget(blockClass), blockers);
            inspectClass(
                    "tile", tileClass, cohort,
                    dependencyByClass.get(tileClass),
                    references.forTarget(tileClass), blockers);
            for (String presentationClass : presentationClasses) {
                inspectClass(
                        "presentation:" + presentationClass,
                        presentationClass,
                        cohort,
                        dependencyByClass.get(presentationClass),
                        references.forTarget(presentationClass),
                        blockers);
            }
            for (String companion : companions) {
                inspectClass(
                        "companion:" + companion, companion, cohort,
                        dependencyByClass.get(companion),
                        references.forTarget(companion), blockers);
            }

            boolean cohortReady = blockers.isEmpty();
            if (cohortReady) ready++; else blocked++;

            JsonObject value = new JsonObject();
            if (id != null) value.addProperty("id", id);
            value.addProperty("sourceBlockClass", blockClass);
            value.addProperty("sourceTileClass", tileClass);
            value.addProperty("modernRuntimeReplacementComplete", true);
            value.addProperty("tileRegistrationStripComplete",
                    tileRule != null
                            && bool(tileRule, "tileRegistrationStripComplete", false));
            value.addProperty("blockRegistrationStripComplete",
                    blockRule != null
                            && bool(blockRule, "blockRegistrationStripComplete", false));
            value.addProperty("blockConstructorReplacementProven", false);
            value.addProperty("blockSourceAllocationStripComplete", false);
            value.addProperty("tileConstructorReplacementProven",
                    tileConstructorReplacementProven);
            value.addProperty("guiHandlerBranchProofComplete",
                    guiHandlerBranchProofComplete);
            value.addProperty("guiHandlerBranchStripComplete",
                    guiHandlerBranchStripComplete);
            value.addProperty("guiHandlerRetirementComplete",
                    guiHandlerRetirementComplete);
            if (guiHandlerClass != null) {
                value.addProperty("guiHandlerClass", guiHandlerClass);
            }
            if (sourceContainerClass != null) {
                value.addProperty("sourceContainerClass", sourceContainerClass);
            }
            if (sourceGuiClass != null) {
                value.addProperty("sourceGuiClass", sourceGuiClass);
            }
            value.addProperty(
                    "processorPresentationCohortExpanded",
                    guiHandlerRetirementComplete);
            value.addProperty(
                    "presentationSourceClassCount", presentationClasses.size());
            value.add("presentationSourceClasses", strings(presentationClasses));
            value.addProperty("nestedCompanionClassCount", companions.size());
            value.add("nestedCompanionClasses", strings(companions));
            value.addProperty("retirementCohortCandidateReady", cohortReady);
            value.addProperty("sourceClassDeletionAuthorized", false);
            value.addProperty("deletedSourceClassCount", 0);
            value.add("blockers", strings(blockers));

            addEvidence(
                    value, "block", blockClass,
                    dependencyByClass.get(blockClass),
                    references.forTarget(blockClass));
            addEvidence(
                    value, "tile", tileClass,
                    dependencyByClass.get(tileClass),
                    references.forTarget(tileClass));

            JsonArray presentationEvidence = new JsonArray();
            for (String presentationClass : presentationClasses) {
                JsonObject evidence = new JsonObject();
                evidence.addProperty("sourceClass", presentationClass);
                addEvidence(
                        evidence,
                        "presentation",
                        presentationClass,
                        dependencyByClass.get(presentationClass),
                        references.forTarget(presentationClass));
                presentationEvidence.add(evidence);
            }
            value.add("presentationSourceEvidence", presentationEvidence);

            JsonArray companionEvidence = new JsonArray();
            for (String companion : companions) {
                JsonObject evidence = new JsonObject();
                evidence.addProperty("sourceClass", companion);
                addEvidence(
                        evidence, "companion", companion,
                        dependencyByClass.get(companion),
                        references.forTarget(companion));
                companionEvidence.add(evidence);
            }
            value.add("nestedCompanionEvidence", companionEvidence);
            value.add("referenceScanDiagnostics", strings(references.diagnostics()));
            rules.add(value);
        }

        root.add("rules", rules);
        root.addProperty("evaluatedRuntimeCohorts", rules.size());
        root.addProperty("retirementCohortCandidateReadyCount", ready);
        root.addProperty("retirementCohortBlockedCount", blocked);
        root.addProperty("sourceClassDeletionAuthorizedCount", 0);

        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        if (ready > 0) {
            context.diagnostics().info(
                    "LFB-CONVERT-PROCESSOR-RETIRE-0001",
                    SupportLevel.RUNTIME_BRIDGE,
                    "Processor retirement readiness found " + ready
                            + " source cohort(s) with no modeled blockers; deletion remains "
                            + "intentionally unwired.");
        }
        if (blocked > 0) {
            context.diagnostics().warning(
                    "LFB-CONVERT-PROCESSOR-RETIRE-0002",
                    SupportLevel.RUNTIME_BRIDGE,
                    "Processor source retirement remains blocked for " + blocked
                            + " cohort(s); constructor/allocation/GUI and candidate reference "
                            + "closure remain explicit independent gates.");
        }
    }

    static LinkedHashSet<String> discoverNestedCompanions(
            Path stagingDir, Set<String> baseCohort) throws Exception {
        LinkedHashSet<String> companions = new LinkedHashSet<>();
        try (var stream = Files.walk(stagingDir)) {
            for (Path path : stream.filter(Files::isRegularFile)
                    .filter(value -> value.getFileName().toString().endsWith(".class"))
                    .sorted()
                    .toList()) {
                String relative =
                        stagingDir.relativize(path).toString().replace('\\', '/');
                String internalName =
                        relative.substring(0, relative.length() - ".class".length());
                for (String base : baseCohort) {
                    if (internalName.startsWith(base + "$")) {
                        companions.add(internalName);
                        break;
                    }
                }
            }
        }
        return companions;
    }

    private static void inspectClass(
            String kind,
            String sourceClass,
            Set<String> cohort,
            LegacyClassDependencyAnalyzer.ClassDependency dependency,
            LegacyCandidateReferenceAnalyzer.Evidence evidence,
            Set<String> blockers) {
        if (dependency == null) {
            blockers.add(kind + "-class-dependency-evidence-missing");
        } else {
            if (dependency.candidateState()
                    != LegacyClassDependencyAnalyzer.CandidateState.ORIGINAL_BYTES_RETAINED) {
                blockers.add(kind + "-candidate-source-class-state:"
                        + dependency.candidateState().name().toLowerCase());
            }
            if (!dependency.generatedReferences().isEmpty()) {
                blockers.add(kind + "-generated-symbolic-reference");
            }
            if (!dependency.dynamicEvidence().isEmpty()) {
                blockers.add(kind + "-dynamic-bytecode-evidence");
            }
        }

        for (String incoming : evidence.incomingClassReferences()) {
            if (!cohort.contains(incoming)) {
                blockers.add(kind + "-candidate-incoming-reference:" + incoming);
            }
        }
        for (String resource : evidence.resourceReferences()) {
            blockers.add(kind + "-candidate-resource-reference:" + resource);
        }
    }

    private static void addEvidence(
            JsonObject value,
            String prefix,
            String sourceClass,
            LegacyClassDependencyAnalyzer.ClassDependency dependency,
            LegacyCandidateReferenceAnalyzer.Evidence evidence) {
        value.addProperty(prefix + "Class", sourceClass);
        value.add(prefix + "CandidateIncomingReferences",
                strings(evidence.incomingClassReferences()));
        value.add(prefix + "CandidateResourceReferences",
                strings(evidence.resourceReferences()));
        if (dependency != null) {
            value.add(prefix + "SourceIncomingReferences",
                    strings(dependency.incomingSourceReferences()));
            value.add(prefix + "SourceReferences",
                    strings(dependency.sourceReferences()));
            value.add(prefix + "SourceRootEvidence",
                    strings(dependency.rootEvidence()));
            value.add(prefix + "GeneratedReferences",
                    strings(dependency.generatedReferences()));
            value.add(prefix + "DynamicEvidence",
                    strings(dependency.dynamicEvidence()));
        }
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

    private static JsonObject read(Path path) throws Exception {
        return JsonParser.parseString(
                Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static JsonArray array(JsonObject root, String name) {
        JsonElement value = root == null ? null : root.get(name);
        return value != null && value.isJsonArray()
                ? value.getAsJsonArray() : new JsonArray();
    }

    private static boolean bool(JsonObject root, String name, boolean fallback) {
        JsonElement value = root == null ? null : root.get(name);
        return value != null && value.isJsonPrimitive()
                ? value.getAsBoolean() : fallback;
    }

    private static int integer(JsonObject root, String name, int fallback) {
        JsonElement value = root == null ? null : root.get(name);
        return value != null && value.isJsonPrimitive()
                ? value.getAsInt() : fallback;
    }

    private static String string(JsonObject root, String name, String fallback) {
        JsonElement value = root == null ? null : root.get(name);
        return value != null && value.isJsonPrimitive()
                ? value.getAsString() : fallback;
    }

    private static JsonArray strings(Iterable<String> values) {
        JsonArray output = new JsonArray();
        for (String value : values) output.add(value);
        return output;
    }
}
