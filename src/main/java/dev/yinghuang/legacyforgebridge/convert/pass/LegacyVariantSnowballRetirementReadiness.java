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
 * Computes deletion readiness for a fully replaced variant-snowball source cohort without mutating
 * any source class. Item registration retirement remains a mandatory independent gate.
 */
public final class LegacyVariantSnowballRetirementReadiness {
    public static final String OUTPUT =
            "legacyforgebridge/variant-snowball-retirement-readiness.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private LegacyVariantSnowballRetirementReadiness() { }

    public static void materialize(
            ConversionContext context,
            LegacyClassDependencyAnalyzer.Analysis dependencies) throws Exception {
        Path runtimePath =
                context.stagingDir().resolve(LegacyVariantSnowballRuntimePass.OUTPUT);
        Path stripPath =
                context.stagingDir().resolve(LegacyVariantSnowballRegistrationStripPass.OUTPUT);
        Path itemStripPath =
                context.stagingDir().resolve(LegacyVariantSnowballItemRegistrationStripPass.OUTPUT);
        Path constructionPath = context.stagingDir().resolve(
                LegacyVariantSnowballConstructionReplacementReadiness.OUTPUT);
        Path allocationStripPath = context.stagingDir().resolve(
                LegacyVariantSnowballSourceAllocationStripPass.OUTPUT);
        if (!Files.isRegularFile(runtimePath)
                || !Files.isRegularFile(stripPath)
                || !Files.isRegularFile(itemStripPath)
                || !Files.isRegularFile(constructionPath)
                || !Files.isRegularFile(allocationStripPath)) return;

        JsonObject runtime = read(runtimePath);
        JsonObject strip = read(stripPath);
        JsonObject itemStrip = read(itemStripPath);
        JsonObject construction = read(constructionPath);
        JsonObject allocationStrip = read(allocationStripPath);
        if (integer(runtime, "schemaVersion", -1) != LegacyVariantSnowballRuntimePass.SCHEMA
                || integer(strip, "schemaVersion", -1) != 1
                || integer(itemStrip, "schemaVersion", -1) != 1
                || integer(construction, "schemaVersion", -1) != 1
                || integer(allocationStrip, "schemaVersion", -1) != 1
                || !context.sourceHash().equals(string(runtime, "sourceSha256", ""))
                || !context.sourceHash().equals(string(strip, "sourceSha256", ""))
                || !context.sourceHash().equals(string(itemStrip, "sourceSha256", ""))
                || !context.sourceHash().equals(string(construction, "sourceSha256", ""))
                || !context.sourceHash().equals(string(allocationStrip, "sourceSha256", ""))) {
            return;
        }

        Map<String, LegacyClassDependencyAnalyzer.ClassDependency> dependencyByClass =
                new LinkedHashMap<>();
        for (var dependency : dependencies.classes()) {
            dependencyByClass.put(dependency.sourceClass(), dependency);
        }

        Map<String, JsonObject> stripByProjectile = new LinkedHashMap<>();
        for (JsonElement element : array(strip, "rules")) {
            if (!element.isJsonObject()) continue;
            JsonObject rule = element.getAsJsonObject();
            String projectile = string(rule, "sourceProjectileClass", null);
            if (projectile != null) stripByProjectile.putIfAbsent(projectile, rule);
        }

        Map<String, JsonObject> stripByItem = new LinkedHashMap<>();
        for (JsonElement element : array(itemStrip, "rules")) {
            if (!element.isJsonObject()) continue;
            JsonObject rule = element.getAsJsonObject();
            String item = string(rule, "sourceItemClass", null);
            if (item != null) stripByItem.putIfAbsent(item, rule);
        }

        Map<String, JsonObject> constructionByItem = new LinkedHashMap<>();
        for (JsonElement element : array(construction, "rules")) {
            if (!element.isJsonObject()) continue;
            JsonObject rule = element.getAsJsonObject();
            String item = string(rule, "sourceItemClass", null);
            if (item != null) constructionByItem.putIfAbsent(item, rule);
        }

        Map<String, JsonObject> allocationStripByItem = new LinkedHashMap<>();
        for (JsonElement element : array(allocationStrip, "rules")) {
            if (!element.isJsonObject()) continue;
            JsonObject rule = element.getAsJsonObject();
            String item = string(rule, "sourceItemClass", null);
            if (item != null) allocationStripByItem.putIfAbsent(item, rule);
        }

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("retirementReadinessAnalysisWired", true);
        root.addProperty("itemRegistrationRetirementRequired", true);
        root.addProperty("constructorReplacementRequired", true);
        root.addProperty("sourceAllocationRetirementRequired", true);
        root.addProperty("retirementAuthorizationWired", false);
        root.addProperty("sourceClassDeletionWired", false);

        JsonArray rules = new JsonArray();
        int ready = 0;
        int blocked = 0;

        for (JsonElement element : array(runtime, "rules")) {
            if (!element.isJsonObject()) continue;
            JsonObject runtimeRule = element.getAsJsonObject();
            if (!completeRuntime(runtimeRule)) continue;

            String id = string(runtimeRule, "id", null);
            String itemClass = string(runtimeRule, "sourceItemClass", null);
            String projectileClass = string(runtimeRule, "sourceProjectileClass", null);
            String selectorClass = string(runtimeRule, "selectorClass", null);
            if (itemClass == null || projectileClass == null || selectorClass == null) continue;

            Set<String> cohort = Set.of(itemClass, projectileClass, selectorClass);
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

            JsonObject stripRule = stripByProjectile.get(projectileClass);
            if (stripRule == null
                    || !bool(stripRule, "projectileRegistrationStripComplete", false)
                    || integer(stripRule, "strippedProjectileRegistrationSites", 0) != 1) {
                blockers.add("projectile-registration-strip-incomplete");
            }

            JsonObject itemStripRule = stripByItem.get(itemClass);
            if (!bool(itemStrip, "itemRegistrationStripWired", false)
                    || itemStripRule == null
                    || !bool(itemStripRule, "itemRegistrationStripComplete", false)
                    || integer(itemStripRule, "strippedItemRegistrationSites", 0) != 1) {
                blockers.add("item-registration-strip-incomplete");
            }

            JsonObject constructionRule = constructionByItem.get(itemClass);
            if (!bool(construction, "constructionReplacementAnalysisWired", false)
                    || constructionRule == null
                    || !bool(constructionRule, "constructorReplacementProven", false)) {
                blockers.add("item-constructor-replacement-incomplete");
            }
            JsonObject allocationStripRule = allocationStripByItem.get(itemClass);
            if (!bool(allocationStrip, "sourceAllocationStripWired", false)
                    || allocationStripRule == null
                    || !bool(allocationStripRule, "sourceAllocationStripComplete", false)
                    || integer(allocationStripRule, "strippedSourceAllocationSites", 0) != 1) {
                blockers.add("item-source-allocation-strip-incomplete");
            }

            inspectClass(
                    "item",
                    itemClass,
                    cohort,
                    dependencyByClass.get(itemClass),
                    references.forTarget(itemClass),
                    blockers);
            inspectClass(
                    "projectile",
                    projectileClass,
                    cohort,
                    dependencyByClass.get(projectileClass),
                    references.forTarget(projectileClass),
                    blockers);
            inspectClass(
                    "selector",
                    selectorClass,
                    cohort,
                    dependencyByClass.get(selectorClass),
                    references.forTarget(selectorClass),
                    blockers);

            boolean cohortReady = blockers.isEmpty();
            if (cohortReady) ready++; else blocked++;

            JsonObject value = new JsonObject();
            if (id != null) value.addProperty("id", id);
            value.addProperty("sourceItemClass", itemClass);
            value.addProperty("sourceProjectileClass", projectileClass);
            value.addProperty("selectorClass", selectorClass);
            value.addProperty("modernRuntimeReplacementComplete", true);
            value.addProperty("projectileRegistrationStripComplete",
                    stripRule != null
                            && bool(stripRule, "projectileRegistrationStripComplete", false));
            value.addProperty("itemRegistrationStripComplete",
                    itemStripRule != null
                            && bool(itemStripRule, "itemRegistrationStripComplete", false));
            value.addProperty("constructorReplacementProven",
                    constructionRule != null
                            && bool(constructionRule, "constructorReplacementProven", false));
            value.addProperty("sourceAllocationStripComplete",
                    allocationStripRule != null
                            && bool(allocationStripRule, "sourceAllocationStripComplete", false));
            value.addProperty("retirementCohortCandidateReady", cohortReady);
            value.addProperty("sourceClassDeletionAuthorized", false);
            value.addProperty("deletedSourceClassCount", 0);
            value.add("blockers", strings(blockers));

            addEvidence(value, "item", itemClass,
                    dependencyByClass.get(itemClass), references.forTarget(itemClass));
            addEvidence(value, "projectile", projectileClass,
                    dependencyByClass.get(projectileClass), references.forTarget(projectileClass));
            addEvidence(value, "selector", selectorClass,
                    dependencyByClass.get(selectorClass), references.forTarget(selectorClass));
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
                    "LFB-CONVERT-VARIANT-SNOWBALL-RETIRE-0001",
                    SupportLevel.RUNTIME_BRIDGE,
                    "Variant-snowball retirement readiness found " + ready
                            + " source cohort(s) with no modeled candidate references; deletion "
                            + "remains intentionally unwired.");
        }
        if (blocked > 0) {
            context.diagnostics().warning(
                    "LFB-CONVERT-VARIANT-SNOWBALL-RETIRE-0002",
                    SupportLevel.RUNTIME_BRIDGE,
                    "Variant-snowball source retirement remains blocked for " + blocked
                            + " cohort(s); registration retirement and candidate reference "
                            + "closure must complete before deletion.");
        }
    }

    private static boolean completeRuntime(JsonObject rule) {
        return bool(rule, "runtimeRuleReady", false)
                && bool(rule, "projectileEntityTypeRegistrationWired", false)
                && bool(rule, "projectileItemStackCarrierWired", false)
                && bool(rule, "legacyMetadataSyncWired", false)
                && bool(rule, "itemRuntimeWired", false)
                && bool(rule, "projectileRuntimeWired", false)
                && bool(rule, "projectileImpactRuntimeWired", false)
                && bool(rule, "rendererRuntimeWired", false)
                && bool(rule, "runtimeImplementationWired", false);
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

    private static JsonObject read(Path path) throws Exception {
        return JsonParser.parseString(
                Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static JsonArray array(JsonObject root, String name) {
        JsonElement value = root.get(name);
        return value != null && value.isJsonArray()
                ? value.getAsJsonArray() : new JsonArray();
    }

    private static boolean bool(JsonObject root, String name, boolean fallback) {
        JsonElement value = root.get(name);
        return value != null && value.isJsonPrimitive()
                ? value.getAsBoolean() : fallback;
    }

    private static int integer(JsonObject root, String name, int fallback) {
        JsonElement value = root.get(name);
        return value != null && value.isJsonPrimitive()
                ? value.getAsInt() : fallback;
    }

    private static String string(JsonObject root, String name, String fallback) {
        JsonElement value = root.get(name);
        return value != null && value.isJsonPrimitive()
                ? value.getAsString() : fallback;
    }

    private static JsonArray strings(Iterable<String> values) {
        JsonArray output = new JsonArray();
        for (String value : values) output.add(value);
        return output;
    }
}
