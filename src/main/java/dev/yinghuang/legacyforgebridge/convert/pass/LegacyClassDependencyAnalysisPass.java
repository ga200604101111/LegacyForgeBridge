package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.yinghuang.legacyforgebridge.convert.LegacyClassDependencyAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes conservative source/runtime dependency evidence without authorizing source-class removal. */
public final class LegacyClassDependencyAnalysisPass implements ConversionPass {
    public static final String ANALYSIS_PATH = "legacyforgebridge/class-dependency-analysis.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override public String id() { return "legacy-class-dependency-analysis"; }

    @Override
    public void apply(ConversionContext context) throws Exception {
        LegacyClassDependencyAnalyzer.Analysis analysis = new LegacyClassDependencyAnalyzer()
                .analyze(context.sourceJar(), context.stagingDir());
        if (analysis.classes().isEmpty()) return;

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("analysisKind", "conservative-symbolic-reference-inventory");
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("classCount", analysis.classes().size());
        root.addProperty("potentiallyReachableClasses", analysis.potentiallyReachableCount());
        root.addProperty("unresolvedReachabilityClasses", analysis.classes().size() - analysis.potentiallyReachableCount());
        root.addProperty("exclusionAuthorizations", 0);

        JsonArray classes = new JsonArray();
        for (LegacyClassDependencyAnalyzer.ClassDependency dependency : analysis.classes()) {
            JsonObject value = new JsonObject();
            value.addProperty("sourceClass", dependency.sourceClass());
            value.addProperty("sourceSha256", dependency.sourceSha256());
            value.addProperty("declaredSide", dependency.declaredSide());
            value.addProperty("reachability", dependency.reachability().name().toLowerCase());
            value.addProperty("candidateState", dependency.candidateState().name().toLowerCase());
            value.addProperty("modernReplacement", dependency.modernReplacement());
            value.addProperty("action", dependency.action());
            value.add("roles", strings(dependency.roles()));
            value.add("rootEvidence", strings(dependency.rootEvidence()));
            value.add("sourceReferences", strings(dependency.sourceReferences()));
            value.add("incomingSourceReferences", strings(dependency.incomingSourceReferences()));
            value.add("generatedReferences", strings(dependency.generatedReferences()));
            value.add("resourceReferences", strings(dependency.resourceReferences()));
            value.add("externalReferences", strings(dependency.externalReferences()));
            value.add("capabilities", strings(dependency.capabilities()));
            value.add("dynamicEvidence", strings(dependency.dynamicEvidence()));
            classes.add(value);
        }
        root.add("classes", classes);

        JsonArray capabilities = new JsonArray();
        for (LegacyClassDependencyAnalyzer.CapabilitySummary summary : analysis.capabilities()) {
            JsonObject value = new JsonObject();
            value.addProperty("name", summary.name());
            value.addProperty("directClassCount", summary.directSourceClasses().size());
            value.addProperty("potentiallyReachableDependentCount", summary.potentiallyReachableDependents().size());
            value.addProperty("unresolvedDependentCount", summary.unresolvedDependents().size());
            value.add("directSourceClasses", strings(summary.directSourceClasses()));
            value.add("potentiallyReachableDependents", strings(summary.potentiallyReachableDependents()));
            value.add("unresolvedDependents", strings(summary.unresolvedDependents()));
            capabilities.add(value);
        }
        root.add("capabilities", capabilities);
        root.add("diagnostics", strings(analysis.diagnostics()));
        root.add("limitations", strings(analysis.limitations()));

        Path output = context.stagingDir().resolve(ANALYSIS_PATH);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
        context.diagnostics().info(
                "LFB-CONVERT-DEPENDENCY-0002",
                SupportLevel.ADAPTED,
                "Inventoried original source-class dependencies without executing legacy classes: classes="
                        + analysis.classes().size() + ", potentiallyReachable=" + analysis.potentiallyReachableCount()
                        + ", capabilities=" + analysis.capabilities().size()
                        + ". This inventory does not authorize deleting any source class."
        );

        // This runs here, after generated semantic/entrypoint bytecode exists, so retirement readiness
        // can inspect the final staged candidate reference graph rather than the original source graph.
        LegacyPlainEntityRetirementReadiness.materialize(context, analysis);
    }

    private static JsonArray strings(Iterable<String> values) {
        JsonArray output = new JsonArray();
        for (String value : values) output.add(value);
        return output;
    }
}
