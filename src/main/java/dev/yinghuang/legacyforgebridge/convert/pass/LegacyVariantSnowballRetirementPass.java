package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyCandidateReferenceAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Retires a proof-complete legacy variant-snowball item/projectile/selector cohort only after fresh
 * pre-delete and post-delete candidate reference checks. All class bytes are restored on failure.
 */
public final class LegacyVariantSnowballRetirementPass implements ConversionPass {
    public static final String OUTPUT =
            "legacyforgebridge/variant-snowball-retirement.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override public String id() {
        return "legacy-variant-snowball-retirement";
    }

    @Override
    public void apply(ConversionContext context) throws Exception {
        Path readinessPath = context.stagingDir().resolve(
                LegacyVariantSnowballRetirementReadiness.OUTPUT);
        if (!Files.isRegularFile(readinessPath)) return;

        JsonObject readiness = read(readinessPath);
        if (integer(readiness, "schemaVersion", -1) != 1
                || !context.sourceHash().equals(
                        string(readiness, "sourceSha256", ""))) {
            return;
        }

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("retirementAuthorizationWired", true);
        root.addProperty("sourceClassDeletionWired", true);
        root.addProperty("nestedCompanionRetirementWired", true);
        root.addProperty("freshNestedCompanionSetRecheckWired", true);
        root.addProperty("freshPreDeleteReferenceCheckWired", true);
        root.addProperty("freshPostDeleteReferenceCheckWired", true);
        root.addProperty("restoreOnPostDeleteFailureWired", true);

        JsonArray rules = new JsonArray();
        int retiredCohorts = 0, deletedClasses = 0, blockedCohorts = 0;
        LegacyCandidateReferenceAnalyzer analyzer =
                new LegacyCandidateReferenceAnalyzer();

        for (JsonElement element : array(readiness, "rules")) {
            if (!element.isJsonObject()) continue;
            JsonObject source = element.getAsJsonObject();

            String id = string(source, "id", null);
            String itemClass = string(source, "sourceItemClass", null);
            String projectileClass = string(source, "sourceProjectileClass", null);
            String selectorClass = string(source, "selectorClass", null);

            JsonObject value = new JsonObject();
            if (id != null) value.addProperty("id", id);
            if (itemClass != null) value.addProperty("sourceItemClass", itemClass);
            if (projectileClass != null) {
                value.addProperty("sourceProjectileClass", projectileClass);
            }
            if (selectorClass != null) value.addProperty("selectorClass", selectorClass);

            LinkedHashSet<String> blockers = new LinkedHashSet<>();
            if (!bool(source, "retirementCohortCandidateReady", false)) {
                blockers.add("retirement-cohort-readiness-not-complete");
                for (JsonElement blocker : array(source, "blockers")) {
                    if (blocker.isJsonPrimitive()) {
                        blockers.add("readiness:" + blocker.getAsString());
                    }
                }
            }

            LinkedHashSet<String> baseCohort = new LinkedHashSet<>();
            if (itemClass != null) baseCohort.add(itemClass);
            if (projectileClass != null) baseCohort.add(projectileClass);
            if (selectorClass != null) baseCohort.add(selectorClass);
            if (baseCohort.size() != 3) {
                blockers.add("retirement-cohort-class-identity-incomplete-or-colliding");
            }

            LinkedHashSet<String> declaredCompanions = new LinkedHashSet<>();
            for (JsonElement companion : array(source, "nestedCompanionClasses")) {
                if (!companion.isJsonPrimitive()) {
                    blockers.add("retirement-nested-companion-identity-malformed");
                    continue;
                }
                String name = companion.getAsString();
                if (!isNestedCompanion(name, baseCohort)) {
                    blockers.add("retirement-nested-companion-outside-base-cohort:" + name);
                    continue;
                }
                declaredCompanions.add(name);
            }

            LinkedHashSet<String> freshCompanions =
                    LegacyVariantSnowballRetirementReadiness.discoverNestedCompanions(
                            context.stagingDir(), baseCohort);
            if (!freshCompanions.equals(declaredCompanions)) {
                blockers.add("nested-companion-set-changed-after-readiness");
            }

            LinkedHashSet<String> cohort = new LinkedHashSet<>(baseCohort);
            cohort.addAll(freshCompanions);

            Map<String,Path> paths = new LinkedHashMap<>();
            for (String target : cohort) {
                Path path = safeClassPath(context.stagingDir(), target);
                if (path == null) {
                    blockers.add("unsafe-source-class-path:" + target);
                } else if (!Files.isRegularFile(path)) {
                    blockers.add("source-class-not-present:" + target);
                } else {
                    paths.put(target, path);
                }
            }

            LegacyCandidateReferenceAnalyzer.Analysis pre = null;
            if (blockers.isEmpty()) {
                pre = analyzer.analyze(context.stagingDir(), cohort);
                if (!pre.classReferenceClosureComplete()) {
                    blockers.add("fresh-predelete-class-reference-scan-incomplete");
                }
                if (!pre.resourceReferenceClosureComplete()) {
                    blockers.add("fresh-predelete-resource-reference-scan-incomplete");
                }
                for (String target : cohort) {
                    addUnexpectedPreDeleteReferences(
                            blockers, pre.forTarget(target), cohort, target);
                }
            }

            boolean retired = false, restored = false;
            JsonArray postDiagnostics = new JsonArray();

            if (blockers.isEmpty()) {
                Map<String,byte[]> originalBytes = new LinkedHashMap<>();
                for (String target : cohort) {
                    originalBytes.put(target, Files.readAllBytes(paths.get(target)));
                }

                try {
                    for (String target : cohort) {
                        Files.delete(paths.get(target));
                    }

                    LegacyCandidateReferenceAnalyzer.Analysis post =
                            analyzer.analyze(context.stagingDir(), cohort);
                    for (String diagnostic : post.diagnostics()) {
                        postDiagnostics.add(diagnostic);
                    }

                    LinkedHashSet<String> postBlockers = new LinkedHashSet<>();
                    if (!post.classReferenceClosureComplete()) {
                        postBlockers.add("postdelete-class-reference-scan-incomplete");
                    }
                    if (!post.resourceReferenceClosureComplete()) {
                        postBlockers.add("postdelete-resource-reference-scan-incomplete");
                    }
                    for (String target : cohort) {
                        var evidence = post.forTarget(target);
                        for (String incoming : evidence.incomingClassReferences()) {
                            postBlockers.add(
                                    "postdelete-incoming-reference:" + target + "<-" + incoming);
                        }
                        for (String resource : evidence.resourceReferences()) {
                            postBlockers.add(
                                    "postdelete-resource-reference:" + target + "<-" + resource);
                        }
                    }

                    if (postBlockers.isEmpty()) {
                        retired = true;
                    } else {
                        restore(paths, originalBytes);
                        restored = true;
                        blockers.addAll(postBlockers);
                    }
                } catch (Exception failure) {
                    restore(paths, originalBytes);
                    restored = true;
                    blockers.add("retirement-delete-or-postcheck-failed:"
                            + failure.getClass().getSimpleName());
                }
            }

            value.addProperty("retirementComplete", retired);
            value.addProperty("sourceClassDeletionAuthorized", retired);
            value.addProperty("nestedCompanionClassCount", freshCompanions.size());
            value.add("nestedCompanionClasses", strings(freshCompanions));
            value.addProperty("deletedSourceClassCount", retired ? cohort.size() : 0);
            value.addProperty("restoredAfterFailedRetirement", restored);
            if (pre != null) {
                for (String target : baseCohort) {
                    String key = target.equals(itemClass) ? "item"
                            : target.equals(projectileClass) ? "projectile" : "selector";
                    value.add(key + "FreshPreDeleteIncomingReferences",
                            strings(pre.forTarget(target).incomingClassReferences()));
                    value.add(key + "FreshPreDeleteResourceReferences",
                            strings(pre.forTarget(target).resourceReferences()));
                }
                JsonArray companionEvidence = new JsonArray();
                for (String companion : freshCompanions) {
                    JsonObject evidence = new JsonObject();
                    evidence.addProperty("sourceClass", companion);
                    evidence.add("freshPreDeleteIncomingReferences",
                            strings(pre.forTarget(companion).incomingClassReferences()));
                    evidence.add("freshPreDeleteResourceReferences",
                            strings(pre.forTarget(companion).resourceReferences()));
                    companionEvidence.add(evidence);
                }
                value.add("nestedCompanionFreshPreDeleteEvidence", companionEvidence);
            }
            value.add("postDeleteScanDiagnostics", postDiagnostics);
            value.add("blockers", strings(blockers));
            rules.add(value);

            if (retired) {
                retiredCohorts++;
                deletedClasses += cohort.size();
            } else {
                blockedCohorts++;
            }
        }

        root.add("rules", rules);
        root.addProperty("retirementAuthorizedCohorts", retiredCohorts);
        root.addProperty("deletedSourceClasses", deletedClasses);
        root.addProperty("blockedRetirementCohorts", blockedCohorts);

        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        if (retiredCohorts > 0) {
            context.diagnostics().info(
                    "LFB-CONVERT-VARIANT-SNOWBALL-RETIRE-0101",
                    SupportLevel.ADAPTED,
                    "Retired " + retiredCohorts
                            + " proof-complete variant-snowball source cohort(s) ("
                            + deletedClasses
                            + " class files) after fresh pre-delete and post-delete reference checks.");
        }
        if (blockedCohorts > 0) {
            context.diagnostics().warning(
                    "LFB-CONVERT-VARIANT-SNOWBALL-RETIRE-0102",
                    SupportLevel.RUNTIME_BRIDGE,
                    "Kept " + blockedCohorts
                            + " variant-snowball source cohort(s) because readiness or fresh "
                            + "candidate reference closure remained incomplete.");
        }
    }

    private static void addUnexpectedPreDeleteReferences(
            Set<String> blockers,
            LegacyCandidateReferenceAnalyzer.Evidence evidence,
            Set<String> cohort,
            String target) {
        for (String incoming : evidence.incomingClassReferences()) {
            if (!cohort.contains(incoming)) {
                blockers.add("fresh-predelete-incoming-reference:"
                        + target + "<-" + incoming);
            }
        }
        for (String resource : evidence.resourceReferences()) {
            blockers.add("fresh-predelete-resource-reference:"
                    + target + "<-" + resource);
        }
    }

    private static boolean isNestedCompanion(
            String internalName, Set<String> baseCohort) {
        if (internalName == null || internalName.isBlank()) return false;
        for (String base : baseCohort) {
            if (internalName.startsWith(base + "$")) return true;
        }
        return false;
    }

    private static Path safeClassPath(Path staging, String internalName) {
        if (internalName == null || internalName.isBlank()
                || internalName.startsWith("/")
                || internalName.contains("\\")
                || internalName.contains("..")
                || internalName.indexOf('\0') >= 0) {
            return null;
        }
        Path root = staging.toAbsolutePath().normalize();
        Path path = root.resolve(internalName + ".class").normalize();
        return path.startsWith(root) ? path : null;
    }

    private static void restore(
            Map<String,Path> paths,
            Map<String,byte[]> bytes) throws IOException {
        for (var entry : bytes.entrySet()) {
            Path path = paths.get(entry.getKey());
            Files.createDirectories(path.getParent());
            Files.write(path, entry.getValue());
        }
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
