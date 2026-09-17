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
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Retires a legacy plain Entity + proven no-op renderer pair only after a fresh pre-delete reference
 * check, then re-checks the staged candidate after deletion and restores both class files on failure.
 */
public final class LegacyPlainEntityRetirementPass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/plain-entity-retirement.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override public String id() { return "legacy-plain-entity-retirement"; }

    @Override
    public void apply(ConversionContext context) throws Exception {
        Path readinessPath = context.stagingDir().resolve(LegacyPlainEntityRetirementReadiness.OUTPUT);
        if (!Files.isRegularFile(readinessPath)) return;
        JsonObject readiness = JsonParser.parseString(Files.readString(readinessPath, StandardCharsets.UTF_8)).getAsJsonObject();
        if (integer(readiness, "schemaVersion", -1) != 1
                || !context.sourceHash().equals(string(readiness, "sourceSha256", ""))) return;

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("retirementAuthorizationWired", true);
        root.addProperty("sourceClassDeletionWired", true);
        root.addProperty("preDeleteReferenceRecheckWired", true);
        root.addProperty("postDeleteReferenceRecheckWired", true);
        root.addProperty("restoreOnPostDeleteFailureWired", true);
        JsonArray rules = new JsonArray();
        int authorized = 0, deletedClasses = 0, blocked = 0;
        LegacyCandidateReferenceAnalyzer analyzer = new LegacyCandidateReferenceAnalyzer();

        boolean readinessClosure = bool(readiness, "retirementReadinessAnalysisWired", false)
                && bool(readiness, "candidateClassReferenceClosureComplete", false)
                && bool(readiness, "candidateResourceReferenceClosureComplete", false);

        for (JsonElement element : array(readiness, "rules")) {
            if (!element.isJsonObject()) continue;
            JsonObject source = element.getAsJsonObject();
            String id = string(source, "id", null);
            String entityClass = string(source, "sourceClass", null);
            String rendererClass = string(source, "rendererClass", null);
            JsonObject value = new JsonObject();
            if (id != null) value.addProperty("id", id);
            if (entityClass != null) value.addProperty("sourceClass", entityClass);
            if (rendererClass != null) value.addProperty("rendererClass", rendererClass);
            JsonArray blockers = new JsonArray();

            if (!readinessClosure) blockers.add("retirement-readiness-reference-closure-incomplete");
            if (!bool(source, "retirementCohortCandidateReady", false)) {
                blockers.add("retirement-cohort-readiness-not-complete");
                copyBlockers(source, "entityBlockers", blockers, "readiness-entity:");
                copyBlockers(source, "rendererBlockers", blockers, "readiness-renderer:");
            }
            if (entityClass == null || rendererClass == null) blockers.add("retirement-cohort-class-identity-missing");
            else if (entityClass.equals(rendererClass)) blockers.add("retirement-cohort-entity-renderer-identity-collision");

            Path entityPath = safeClassPath(context.stagingDir(), entityClass);
            Path rendererPath = safeClassPath(context.stagingDir(), rendererClass);
            if (entityClass != null && entityPath == null) blockers.add("unsafe-entity-class-path");
            if (rendererClass != null && rendererPath == null) blockers.add("unsafe-renderer-class-path");
            if (entityPath != null && !Files.isRegularFile(entityPath)) blockers.add("entity-class-not-present-in-candidate");
            if (rendererPath != null && !Files.isRegularFile(rendererPath)) blockers.add("renderer-class-not-present-in-candidate");

            LegacyCandidateReferenceAnalyzer.Analysis pre = null;
            if (blockers.isEmpty()) {
                pre = analyzer.analyze(context.stagingDir(), Set.of(entityClass, rendererClass));
                if (!pre.classReferenceClosureComplete()) blockers.add("fresh-predelete-class-reference-scan-incomplete");
                if (!pre.resourceReferenceClosureComplete()) blockers.add("fresh-predelete-resource-reference-scan-incomplete");
                addUnexpectedReferences(blockers, pre.forTarget(entityClass), Set.of(rendererClass), "entity");
                addUnexpectedReferences(blockers, pre.forTarget(rendererClass), Set.of(entityClass), "renderer");
            }

            boolean retired = false, restored = false;
            JsonArray postDiagnostics = new JsonArray();
            if (blockers.isEmpty()) {
                byte[] entityBytes = Files.readAllBytes(entityPath);
                byte[] rendererBytes = Files.readAllBytes(rendererPath);
                try {
                    Files.delete(entityPath);
                    Files.delete(rendererPath);
                    LegacyCandidateReferenceAnalyzer.Analysis post = analyzer.analyze(
                            context.stagingDir(), Set.of(entityClass, rendererClass));
                    post.diagnostics().forEach(postDiagnostics::add);
                    LinkedHashSet<String> postBlockers = new LinkedHashSet<>();
                    if (!post.classReferenceClosureComplete()) postBlockers.add("postdelete-class-reference-scan-incomplete");
                    if (!post.resourceReferenceClosureComplete()) postBlockers.add("postdelete-resource-reference-scan-incomplete");
                    addPostDeleteReferences(postBlockers, post.forTarget(entityClass), "entity");
                    addPostDeleteReferences(postBlockers, post.forTarget(rendererClass), "renderer");
                    if (postBlockers.isEmpty()) retired = true;
                    else {
                        restore(entityPath, entityBytes, rendererPath, rendererBytes);
                        restored = true;
                        postBlockers.forEach(blockers::add);
                    }
                } catch (Exception deletionFailure) {
                    restore(entityPath, entityBytes, rendererPath, rendererBytes);
                    restored = true;
                    blockers.add("retirement-delete-or-postcheck-failed:" + deletionFailure.getClass().getSimpleName());
                }
            }

            value.addProperty("sourceClassDeletionAuthorized", retired);
            value.addProperty("rendererClassDeletionAuthorized", retired);
            value.addProperty("retirementComplete", retired);
            value.addProperty("restoredAfterFailedRetirement", restored);
            value.addProperty("deletedClassCount", retired ? 2 : 0);
            if (pre != null) {
                value.add("freshPreDeleteEntityIncomingReferences", strings(pre.forTarget(entityClass).incomingClassReferences()));
                value.add("freshPreDeleteRendererIncomingReferences", strings(pre.forTarget(rendererClass).incomingClassReferences()));
            }
            value.add("postDeleteScanDiagnostics", postDiagnostics);
            value.add("blockers", blockers);
            rules.add(value);
            if (retired) { authorized++; deletedClasses += 2; }
            else blocked++;
        }

        root.add("rules", rules);
        root.addProperty("retirementAuthorizedCohorts", authorized);
        root.addProperty("deletedSourceClasses", deletedClasses);
        root.addProperty("blockedRetirementCohorts", blocked);
        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        if (authorized > 0) context.diagnostics().info("LFB-CONVERT-ENTITY-RETIRE-0101", SupportLevel.ADAPTED,
                "Retired " + authorized + " proof-complete plain Entity/no-op-renderer source cohort(s) ("
                        + deletedClasses + " class files) after fresh pre-delete and post-delete candidate reference checks.");
        if (blocked > 0) context.diagnostics().warning("LFB-CONVERT-ENTITY-RETIRE-0102", SupportLevel.RUNTIME_BRIDGE,
                "Kept " + blocked + " plain Entity retirement cohort(s) because readiness, fresh references, scan closure, or post-delete validation remained incomplete.");
    }

    private static void addUnexpectedReferences(JsonArray blockers, LegacyCandidateReferenceAnalyzer.Evidence evidence,
                                                Set<String> allowedIncoming, String kind) {
        for (String incoming : evidence.incomingClassReferences())
            if (!allowedIncoming.contains(incoming)) blockers.add("fresh-predelete-" + kind + "-incoming-reference:" + incoming);
        for (String resource : evidence.resourceReferences())
            blockers.add("fresh-predelete-" + kind + "-resource-reference:" + resource);
    }

    private static void addPostDeleteReferences(Set<String> blockers, LegacyCandidateReferenceAnalyzer.Evidence evidence,
                                                String kind) {
        for (String incoming : evidence.incomingClassReferences())
            blockers.add("postdelete-" + kind + "-incoming-reference:" + incoming);
        for (String resource : evidence.resourceReferences())
            blockers.add("postdelete-" + kind + "-resource-reference:" + resource);
    }

    private static Path safeClassPath(Path staging, String internalName) {
        if (internalName == null || internalName.isBlank() || internalName.startsWith("/")
                || internalName.contains("\\") || internalName.contains("..") || internalName.indexOf('\0') >= 0) return null;
        Path root = staging.toAbsolutePath().normalize();
        Path path = root.resolve(internalName + ".class").normalize();
        return path.startsWith(root) ? path : null;
    }

    private static void restore(Path entityPath, byte[] entityBytes, Path rendererPath, byte[] rendererBytes) throws IOException {
        Files.createDirectories(entityPath.getParent());
        Files.createDirectories(rendererPath.getParent());
        Files.write(entityPath, entityBytes);
        Files.write(rendererPath, rendererBytes);
    }

    private static void copyBlockers(JsonObject source, String key, JsonArray target, String prefix) {
        for (JsonElement blocker : array(source, key)) if (blocker.isJsonPrimitive()) target.add(prefix + blocker.getAsString());
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
