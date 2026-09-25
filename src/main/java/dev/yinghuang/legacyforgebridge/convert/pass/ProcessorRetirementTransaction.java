package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonArray;
import dev.yinghuang.legacyforgebridge.convert.LegacyCandidateReferenceAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;

import java.util.LinkedHashSet;
import java.util.Map;

final class ProcessorRetirementTransaction {
    private ProcessorRetirementTransaction() { }

    static ProcessorRetirementResult execute(ConversionContext context,
            LegacyCandidateReferenceAnalyzer analyzer,
            ProcessorRetirementPlan plan) throws Exception {
        LegacyCandidateReferenceAnalyzer.Analysis pre =
                ProcessorRetirementReferences.precheck(context, analyzer, plan);
        boolean retired = false;
        boolean restored = false;
        JsonArray postDiagnostics = new JsonArray();

        if (plan.blockers().isEmpty()) {
            Map<String,byte[]> bytes = ProcessorRetirementStorage.snapshot(plan);
            try {
                ProcessorRetirementStorage.delete(plan);
                LinkedHashSet<String> postBlockers =
                        ProcessorRetirementReferences.postcheck(
                                context, analyzer, plan, postDiagnostics);
                if (postBlockers.isEmpty()) retired = true;
                else {
                    ProcessorRetirementStorage.restore(plan.paths(), bytes);
                    restored = true;
                    plan.blockers().addAll(postBlockers);
                }
            } catch (Exception failure) {
                ProcessorRetirementStorage.restore(plan.paths(), bytes);
                restored = true;
                plan.blockers().add("retirement-delete-or-postcheck-failed:"
                        + failure.getClass().getSimpleName());
            }
        }
        return ProcessorRetirementEvidence.result(
                plan, pre, postDiagnostics, retired, restored);
    }
}
