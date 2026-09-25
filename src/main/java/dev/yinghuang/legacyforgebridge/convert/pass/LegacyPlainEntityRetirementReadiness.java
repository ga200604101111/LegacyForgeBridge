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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Joins executable plain-Entity runtime proof with current candidate reference evidence.
 *
 * <p>This materializer is intentionally not a deletion pass. A positive candidate result means
 * all currently modeled in-candidate blockers are absent; source-class deletion remains unwired
 * until a later pass explicitly authorizes and performs the mutation.</p>
 */
public final class LegacyPlainEntityRetirementReadiness {
    public static final String OUTPUT = "legacyforgebridge/plain-entity-retirement-readiness.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private LegacyPlainEntityRetirementReadiness() { }

    public static void materialize(ConversionContext context,
                                   LegacyClassDependencyAnalyzer.Analysis dependencies) throws Exception {
        Path runtimePath = context.stagingDir().resolve(LegacyPlainEntityRuntimePass.OUTPUT);
        Path instantiationPath = context.stagingDir().resolve(LegacyEntityInstantiationPass.OUTPUT);
        Path presentationPath = context.stagingDir().resolve(LegacyEntityPresentationPass.OUTPUT);
        if (!Files.isRegularFile(runtimePath) || !Files.isRegularFile(instantiationPath)
                || !Files.isRegularFile(presentationPath)) return;

        JsonObject runtime = read(runtimePath), instantiation = read(instantiationPath), presentation = read(presentationPath);
        if (!valid(runtime, context.sourceHash()) || !valid(instantiation, context.sourceHash())
                || !valid(presentation, context.sourceHash())) return;

        Map<String,JsonObject> instantiationByClass = index(instantiation, "sourceClass");
        Map<String,JsonObject> presentationByClass = index(presentation, "sourceClass");
        Map<String,LegacyClassDependencyAnalyzer.ClassDependency> dependencyByClass = new LinkedHashMap<>();
        for (var dependency : dependencies.classes()) dependencyByClass.put(dependency.sourceClass(), dependency);

        Map<String,Integer> rendererUseCounts = new LinkedHashMap<>();
        for (JsonElement element : array(presentation, "rules")) {
            if (!element.isJsonObject()) continue;
            String renderer = provenRenderer(element.getAsJsonObject());
            if (renderer != null) rendererUseCounts.merge(renderer, 1, Integer::sum);
        }

        List<JsonObject> runtimeRules = new ArrayList<>();
        Set<String> targets = new LinkedHashSet<>();
        for (JsonElement element : array(runtime, "rules")) {
            if (!element.isJsonObject()) continue;
            JsonObject rule = element.getAsJsonObject();
            if (!bool(rule, "runtimeComplete", false)) continue;
            String sourceClass = string(rule, "sourceClass", null);
            if (sourceClass == null) continue;
            runtimeRules.add(rule);
            targets.add(sourceClass);
            String renderer = provenRenderer(presentationByClass.get(sourceClass));
            if (renderer != null) targets.add(renderer);
        }
        if (runtimeRules.isEmpty()) return;

        LegacyCandidateReferenceAnalyzer.Analysis candidateReferences =
                new LegacyCandidateReferenceAnalyzer().analyze(context.stagingDir(), targets);
        int unresolvedWorldSpawns = integer(instantiation, "unresolvedWorldSpawnArgumentCount", -1);
        boolean instantiationInventoryComplete = bool(instantiation, "sourceInstantiationInventoryComplete", false);

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("retirementReadinessAnalysisWired", true);
        root.addProperty("candidateClassReferenceClosureComplete", candidateReferences.classReferenceClosureComplete());
        root.addProperty("candidateResourceReferenceClosureComplete", candidateReferences.resourceReferenceClosureComplete());
        root.addProperty("retirementAuthorizationWired", false);
        root.addProperty("sourceClassDeletionWired", false);
        root.addProperty("unresolvedWorldSpawnArgumentCount", unresolvedWorldSpawns);
        JsonArray rules = new JsonArray();
        int readyCohorts = 0;

        for (JsonObject runtimeRule : runtimeRules) {
            String sourceClass = string(runtimeRule, "sourceClass", null);
            String id = string(runtimeRule, "id", null);
            JsonObject instantiationRule = instantiationByClass.get(sourceClass);
            JsonObject presentationRule = presentationByClass.get(sourceClass);
            String rendererClass = provenRenderer(presentationRule);

            LinkedHashSet<String> entityBlockers = new LinkedHashSet<>();
            LinkedHashSet<String> rendererBlockers = new LinkedHashSet<>();
            if (!candidateReferences.classReferenceClosureComplete()) {
                entityBlockers.add("candidate-class-reference-scan-incomplete");
                rendererBlockers.add("candidate-class-reference-scan-incomplete");
            }
            if (!candidateReferences.resourceReferenceClosureComplete()) {
                entityBlockers.add("candidate-resource-reference-scan-incomplete");
                rendererBlockers.add("candidate-resource-reference-scan-incomplete");
            }

            if (!instantiationInventoryComplete) entityBlockers.add("source-instantiation-inventory-incomplete");
            if (unresolvedWorldSpawns < 0) entityBlockers.add("unresolved-world-spawn-count-missing");
            else if (unresolvedWorldSpawns > 0) entityBlockers.add("unresolved-world-spawn-arguments:" + unresolvedWorldSpawns);
            if (instantiationRule == null) entityBlockers.add("source-instantiation-rule-missing");
            else {
                int direct = integer(instantiationRule, "directConstructionCount", -1);
                int provenSpawn = integer(instantiationRule, "provenWorldSpawnCount", -1);
                boolean rewriteRequired = bool(instantiationRule, "sourceInstantiationRewriteRequired", false);
                boolean rewriteWired = bool(instantiationRule, "sourceInstantiationRewriteWired", false);
                if (direct < 0 || provenSpawn < 0) entityBlockers.add("source-instantiation-counts-missing");
                if ((direct > 0 || provenSpawn > 0 || rewriteRequired) && !rewriteWired)
                    entityBlockers.add("source-instantiation-rewrite-required");
            }

            if (presentationRule == null || !bool(presentationRule, "sourceNoOpRendererProven", false)) {
                entityBlockers.add("source-noop-renderer-proof-missing");
                rendererBlockers.add("source-noop-renderer-proof-missing");
            }
            if (rendererClass == null) {
                entityBlockers.add("source-renderer-identity-missing");
                rendererBlockers.add("source-renderer-identity-missing");
            } else if (rendererUseCounts.getOrDefault(rendererClass, 0) != 1) {
                rendererBlockers.add("renderer-shared-by-multiple-entity-registrations");
            }

            LegacyClassDependencyAnalyzer.ClassDependency entityDependency = dependencyByClass.get(sourceClass);
            addDependencyBlockers(entityBlockers, entityDependency, true);
            LegacyCandidateReferenceAnalyzer.Evidence entityEvidence = candidateReferences.forTarget(sourceClass);
            addCandidateReferenceBlockers(entityBlockers, entityEvidence,
                    rendererClass == null ? Set.of() : Set.of(rendererClass));

            LegacyClassDependencyAnalyzer.ClassDependency rendererDependency = rendererClass == null
                    ? null : dependencyByClass.get(rendererClass);
            if (rendererClass != null) addDependencyBlockers(rendererBlockers, rendererDependency, false);
            if (rendererClass != null) {
                LegacyCandidateReferenceAnalyzer.Evidence rendererEvidence = candidateReferences.forTarget(rendererClass);
                addCandidateReferenceBlockers(rendererBlockers, rendererEvidence, Set.of(sourceClass));
            }

            boolean entityReady = entityBlockers.isEmpty();
            boolean rendererReady = rendererClass != null && rendererBlockers.isEmpty();
            boolean cohortReady = entityReady && rendererReady;
            if (cohortReady) readyCohorts++;

            JsonObject value = new JsonObject();
            if (id != null) value.addProperty("id", id);
            value.addProperty("sourceClass", sourceClass);
            if (rendererClass != null) value.addProperty("rendererClass", rendererClass);
            value.addProperty("modernRuntimeReplacementComplete", true);
            value.addProperty("entityRetirementCandidateReady", entityReady);
            value.addProperty("rendererRetirementCandidateReady", rendererReady);
            value.addProperty("retirementCohortCandidateReady", cohortReady);
            value.addProperty("sourceClassDeletionAuthorized", false);
            value.addProperty("rendererClassDeletionAuthorized", false);
            value.add("entityBlockers", strings(entityBlockers));
            value.add("rendererBlockers", strings(rendererBlockers));
            value.add("candidateEntityIncomingReferences", strings(entityEvidence.incomingClassReferences()));
            value.add("candidateEntityResourceReferences", strings(entityEvidence.resourceReferences()));
            if (rendererClass != null) {
                var rendererEvidence = candidateReferences.forTarget(rendererClass);
                value.add("candidateRendererIncomingReferences", strings(rendererEvidence.incomingClassReferences()));
                value.add("candidateRendererResourceReferences", strings(rendererEvidence.resourceReferences()));
            }
            if (entityDependency != null) value.add("sourceJarEntityRootEvidence", strings(entityDependency.rootEvidence()));
            if (rendererDependency != null) value.add("sourceJarRendererRootEvidence", strings(rendererDependency.rootEvidence()));
            rules.add(value);
        }

        root.add("rules", rules);
        root.add("referenceScanDiagnostics", strings(candidateReferences.diagnostics()));
        root.addProperty("evaluatedRuntimeCohorts", rules.size());
        root.addProperty("retirementCohortCandidateReadyCount", readyCohorts);
        root.addProperty("retirementCohortBlockedCount", rules.size() - readyCohorts);
        root.addProperty("sourceClassDeletionAuthorizedCount", 0);

        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        if (readyCohorts > 0) context.diagnostics().info("LFB-CONVERT-ENTITY-RETIRE-0001", SupportLevel.RUNTIME_BRIDGE,
                "Plain Entity retirement readiness found " + readyCohorts + " candidate cohort(s) with no modeled candidate-bytecode/resource/instantiation blockers; deletion remains intentionally unwired.");
        if (readyCohorts < rules.size()) context.diagnostics().warning("LFB-CONVERT-ENTITY-RETIRE-0002", SupportLevel.RUNTIME_BRIDGE,
                "Plain Entity retirement remains blocked for " + (rules.size() - readyCohorts)
                        + " runtime cohort(s); inspect candidate incoming references, spawn migration, renderer sharing, or reference-scan closure before any source-class removal.");
        for (String diagnostic : candidateReferences.diagnostics())
            context.diagnostics().warning("LFB-CONVERT-ENTITY-RETIRE-0003", SupportLevel.MANUAL_REQUIRED, diagnostic);
    }

    private static void addDependencyBlockers(Set<String> blockers,
                                              LegacyClassDependencyAnalyzer.ClassDependency dependency,
                                              boolean requireEntityRole) {
        if (dependency == null) {
            blockers.add("class-dependency-evidence-missing");
            return;
        }
        if (dependency.candidateState() != LegacyClassDependencyAnalyzer.CandidateState.ORIGINAL_BYTES_RETAINED)
            blockers.add("candidate-source-class-state:" + dependency.candidateState().name().toLowerCase());
        if (requireEntityRole && !dependency.roles().contains("entity")) blockers.add("source-class-not-proven-entity-role");
        if (!dependency.rootEvidence().isEmpty()) blockers.add("source-jar-root-evidence");
        if (!dependency.generatedReferences().isEmpty()) blockers.add("generated-symbolic-references-to-source-class");
        if (!dependency.dynamicEvidence().isEmpty()) blockers.add("source-class-dynamic-bytecode-evidence");
    }

    private static void addCandidateReferenceBlockers(Set<String> blockers,
                                                      LegacyCandidateReferenceAnalyzer.Evidence evidence,
                                                      Set<String> allowedCohortIncoming) {
        for (String incoming : evidence.incomingClassReferences())
            if (!allowedCohortIncoming.contains(incoming)) blockers.add("candidate-incoming-reference:" + incoming);
        for (String resource : evidence.resourceReferences())
            blockers.add("candidate-resource-reference:" + resource);
    }

    private static String provenRenderer(JsonObject rule) {
        if (rule == null || !bool(rule, "sourceNoOpRendererProven", false)) return null;
        JsonArray registrations = array(rule, "registrations");
        if (registrations.size() != 1 || !registrations.get(0).isJsonObject()) return null;
        JsonObject registration = registrations.get(0).getAsJsonObject();
        if (!bool(registration, "rendererClassPresent", false) || !bool(registration, "noOpRenderProven", false)) return null;
        return string(registration, "rendererClass", null);
    }

    private static Map<String,JsonObject> index(JsonObject root, String keyName) {
        Map<String,JsonObject> output = new LinkedHashMap<>();
        for (JsonElement element : array(root, "rules")) {
            if (!element.isJsonObject()) continue;
            JsonObject rule = element.getAsJsonObject();
            String key = string(rule, keyName, null);
            if (key != null) output.putIfAbsent(key, rule);
        }
        return output;
    }

    private static JsonObject read(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }
    private static boolean valid(JsonObject root, String hash) {
        return integer(root, "schemaVersion", -1) == 1 && hash.equals(string(root, "sourceSha256", ""));
    }
    private static JsonArray array(JsonObject root, String name) {
        JsonElement value = root.get(name);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }
    private static boolean bool(JsonObject root, String name, boolean fallback) {
        JsonElement value = root.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsBoolean() : fallback;
    }
    private static int integer(JsonObject root, String name, int fallback) {
        JsonElement value = root.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsInt() : fallback;
    }
    private static String string(JsonObject root, String name, String fallback) {
        JsonElement value = root.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }
    private static JsonArray strings(Iterable<String> values) {
        JsonArray output = new JsonArray();
        for (String value : values) output.add(value);
        return output;
    }
}
