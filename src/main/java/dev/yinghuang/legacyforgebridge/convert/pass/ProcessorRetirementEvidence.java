package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.yinghuang.legacyforgebridge.convert.LegacyCandidateReferenceAnalyzer;

final class ProcessorRetirementEvidence {
    private ProcessorRetirementEvidence() { }

    static ProcessorRetirementResult result(
            ProcessorRetirementPlan plan,
            LegacyCandidateReferenceAnalyzer.Analysis pre,
            JsonArray postDiagnostics,
            boolean retired,
            boolean restored) {
        JsonObject value = plan.identity();
        value.addProperty("presentationSourceClassCount", plan.presentation().size());
        value.add("presentationSourceClasses", strings(plan.presentation()));
        value.addProperty("nestedCompanionClassCount", plan.nested().size());
        value.add("nestedCompanionClasses", strings(plan.nested()));
        value.addProperty("retirementCohortClassCount", plan.cohort().size());
        value.add("retirementCohortClasses", strings(plan.cohort()));
        value.addProperty("retirementComplete", retired);
        value.addProperty("sourceClassDeletionAuthorized", retired);
        value.addProperty("deletedSourceClassCount", retired ? plan.cohort().size() : 0);
        value.addProperty("restoredAfterFailedRetirement", restored);

        JsonArray preEvidence = new JsonArray();
        if (pre != null) for (String target : plan.cohort()) {
            JsonObject evidence = new JsonObject();
            evidence.addProperty("sourceClass", target);
            evidence.add("freshPreDeleteIncomingReferences",
                    strings(pre.forTarget(target).incomingClassReferences()));
            evidence.add("freshPreDeleteResourceReferences",
                    strings(pre.forTarget(target).resourceReferences()));
            preEvidence.add(evidence);
        }
        value.add("freshPreDeleteEvidence", preEvidence);
        value.add("postDeleteScanDiagnostics", postDiagnostics);
        value.add("blockers", strings(plan.blockers()));
        return new ProcessorRetirementResult(
                value, retired, retired ? plan.cohort().size() : 0);
    }

    static JsonArray strings(Iterable<String> values) {
        JsonArray result = new JsonArray();
        for (String value : values) result.add(value);
        return result;
    }
}

record ProcessorRetirementResult(
        JsonObject evidence, boolean retired, int deletedClasses) { }
