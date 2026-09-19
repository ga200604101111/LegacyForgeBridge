package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonArray;
import dev.yinghuang.legacyforgebridge.convert.LegacyCandidateReferenceAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;

import java.util.LinkedHashSet;
import java.util.Set;

final class ProcessorRetirementReferences {
    private ProcessorRetirementReferences() { }

    static LegacyCandidateReferenceAnalyzer.Analysis precheck(
            ConversionContext context,
            LegacyCandidateReferenceAnalyzer analyzer,
            ProcessorRetirementPlan plan) throws Exception {
        if (!plan.blockers().isEmpty()) return null;
        var analysis = analyzer.analyze(context.stagingDir(), plan.cohort());
        requireComplete(analysis, "fresh-predelete", plan.blockers());
        for (String target : plan.cohort()) {
            var evidence = analysis.forTarget(target);
            for (String incoming : evidence.incomingClassReferences())
                if (!plan.cohort().contains(incoming)) plan.blockers().add(
                        "fresh-predelete-incoming-reference:" + target + "<-" + incoming);
            for (String resource : evidence.resourceReferences()) plan.blockers().add(
                    "fresh-predelete-resource-reference:" + target + "<-" + resource);
        }
        return analysis;
    }

    static LinkedHashSet<String> postcheck(
            ConversionContext context,
            LegacyCandidateReferenceAnalyzer analyzer,
            ProcessorRetirementPlan plan,
            JsonArray diagnostics) throws Exception {
        var analysis = analyzer.analyze(context.stagingDir(), plan.cohort());
        analysis.diagnostics().forEach(diagnostics::add);
        LinkedHashSet<String> blockers = new LinkedHashSet<>();
        requireComplete(analysis, "postdelete", blockers);
        for (String target : plan.cohort()) {
            var evidence = analysis.forTarget(target);
            for (String incoming : evidence.incomingClassReferences()) blockers.add(
                    "postdelete-incoming-reference:" + target + "<-" + incoming);
            for (String resource : evidence.resourceReferences()) blockers.add(
                    "postdelete-resource-reference:" + target + "<-" + resource);
        }
        return blockers;
    }

    private static void requireComplete(
            LegacyCandidateReferenceAnalyzer.Analysis analysis,
            String phase, Set<String> blockers) {
        if (!analysis.classReferenceClosureComplete())
            blockers.add(phase + "-class-reference-scan-incomplete");
        if (!analysis.resourceReferenceClosureComplete())
            blockers.add(phase + "-resource-reference-scan-incomplete");
    }
}
