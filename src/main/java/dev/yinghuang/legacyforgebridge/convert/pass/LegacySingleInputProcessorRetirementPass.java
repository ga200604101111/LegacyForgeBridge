package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.yinghuang.legacyforgebridge.convert.LegacyCandidateReferenceAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Atomically retires proof-complete single-input processor source cohorts. */
public final class LegacySingleInputProcessorRetirementPass implements ConversionPass {
    public static final String OUTPUT =
            "legacyforgebridge/single-input-processor-retirement.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override public String id() { return "legacy-single-input-processor-retirement"; }

    @Override
    public void apply(ConversionContext context) throws Exception {
        Path readinessPath = context.stagingDir().resolve(
                LegacySingleInputProcessorRetirementReadiness.OUTPUT);
        if (!Files.isRegularFile(readinessPath)) return;
        JsonObject readiness = ProcessorRetirementPlan.read(readinessPath);
        if (!ProcessorRetirementPlan.validReadiness(context, readiness)) return;

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        for (String flag : new String[]{
                "retirementAuthorizationWired", "sourceClassDeletionWired",
                "processorPresentationCohortRetirementWired", "freshPresentationSetRecheckWired",
                "nestedCompanionRetirementWired", "freshNestedCompanionSetRecheckWired",
                "freshPreDeleteReferenceCheckWired", "freshPostDeleteReferenceCheckWired",
                "restoreOnPostDeleteFailureWired"}) {
            root.addProperty(flag, true);
        }

        LegacyCandidateReferenceAnalyzer analyzer = new LegacyCandidateReferenceAnalyzer();
        JsonArray rules = new JsonArray();
        int retired = 0;
        int deleted = 0;
        int blocked = 0;
        for (JsonElement element : ProcessorRetirementPlan.array(readiness, "rules")) {
            if (!element.isJsonObject()) continue;
            ProcessorRetirementResult result = ProcessorRetirementTransaction.execute(
                    context, analyzer,
                    ProcessorRetirementPlan.build(context, element.getAsJsonObject()));
            rules.add(result.evidence());
            if (result.retired()) {
                retired++;
                deleted += result.deletedClasses();
            } else {
                blocked++;
            }
        }
        root.add("rules", rules);
        root.addProperty("retirementAuthorizedCohorts", retired);
        root.addProperty("deletedSourceClasses", deleted);
        root.addProperty("blockedRetirementCohorts", blocked);

        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
        if (retired > 0) context.diagnostics().info(
                "LFB-CONVERT-PROCESSOR-RETIRE-0101", SupportLevel.ADAPTED,
                "Retired " + retired + " proof-complete processor source cohort(s) ("
                        + deleted + " class files) after fresh pre/post reference checks.");
        if (blocked > 0) context.diagnostics().warning(
                "LFB-CONVERT-PROCESSOR-RETIRE-0102", SupportLevel.RUNTIME_BRIDGE,
                "Kept " + blocked + " processor source cohort(s): proof or fresh reference closure remained incomplete.");
    }
}
