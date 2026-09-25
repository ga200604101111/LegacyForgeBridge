package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;

record ProcessorRetirementPlan(
        JsonObject identity,
        LinkedHashSet<String> blockers,
        LinkedHashSet<String> presentation,
        LinkedHashSet<String> nested,
        LinkedHashSet<String> cohort,
        Map<String,Path> paths) {

    static ProcessorRetirementPlan build(ConversionContext context, JsonObject source) throws Exception {
        JsonObject identity = ProcessorRetirementJson.identity(source);
        LinkedHashSet<String> blockers = new LinkedHashSet<>();
        ProcessorRetirementGates.requireCandidate(source, blockers);

        LinkedHashSet<String> primary = ProcessorRetirementProof.primary(source, blockers);
        LinkedHashSet<String> presentation =
                ProcessorRetirementProof.presentation(source, primary, blockers);
        LinkedHashSet<String> bases = new LinkedHashSet<>(primary);
        bases.addAll(presentation);
        LinkedHashSet<String> nested =
                ProcessorRetirementProof.nested(context, source, bases, blockers);

        LinkedHashSet<String> cohort = new LinkedHashSet<>(bases);
        cohort.addAll(nested);
        Map<String,Path> paths =
                ProcessorRetirementJson.classPaths(context.stagingDir(), cohort, blockers);
        return new ProcessorRetirementPlan(
                identity, blockers, presentation, nested, cohort, paths);
    }

    static boolean validReadiness(ConversionContext context, JsonObject root) {
        return ProcessorRetirementJson.validReadiness(context, root);
    }

    static JsonObject read(Path path) throws Exception {
        return ProcessorRetirementJson.read(path);
    }

    static JsonArray array(JsonObject root, String name) {
        return ProcessorRetirementJson.array(root, name);
    }
}
