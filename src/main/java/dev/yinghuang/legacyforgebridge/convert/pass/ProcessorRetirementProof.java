package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;

import java.util.LinkedHashSet;
import java.util.Set;

final class ProcessorRetirementProof {
    private ProcessorRetirementProof() { }

    static LinkedHashSet<String> primary(JsonObject source, Set<String> blockers) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        String block = ProcessorRetirementJson.string(source, "sourceBlockClass", null);
        String tile = ProcessorRetirementJson.string(source, "sourceTileClass", null);
        if (block != null) result.add(block);
        if (tile != null) result.add(tile);
        if (result.size() != 2)
            blockers.add("retirement-primary-class-identity-incomplete-or-colliding");
        return result;
    }

    static LinkedHashSet<String> presentation(JsonObject source,
            Set<String> primary, Set<String> blockers) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        addPresentation(result,
                ProcessorRetirementJson.string(source, "sourceContainerClass", null),
                primary, "container", blockers);
        addPresentation(result,
                ProcessorRetirementJson.string(source, "sourceGuiClass", null),
                primary, "gui", blockers);
        LinkedHashSet<String> declared = declaredSet(
                source, "presentationSourceClasses", "retirement-presentation-class", blockers);
        if (ProcessorRetirementJson.integer(source, "presentationSourceClassCount", -1)
                != declared.size())
            blockers.add("retirement-presentation-class-count-mismatch");
        if (!result.equals(declared))
            blockers.add("presentation-source-class-set-changed-or-inconsistent");
        return result;
    }

    static LinkedHashSet<String> nested(ConversionContext context, JsonObject source,
            Set<String> bases, Set<String> blockers) throws Exception {
        LinkedHashSet<String> declared = declaredSet(
                source, "nestedCompanionClasses", "retirement-nested-companion", blockers);
        for (String nested : declared)
            if (!isNested(nested, bases))
                blockers.add("retirement-nested-companion-outside-base-cohort:" + nested);
        if (ProcessorRetirementJson.integer(source, "nestedCompanionClassCount", -1)
                != declared.size())
            blockers.add("retirement-nested-companion-count-mismatch");
        LinkedHashSet<String> fresh =
                LegacySingleInputProcessorRetirementReadiness.discoverNestedCompanions(
                        context.stagingDir(), bases);
        if (!fresh.equals(declared))
            blockers.add("nested-companion-set-changed-after-readiness");
        return fresh;
    }

    private static void addPresentation(Set<String> result, String name,
            Set<String> primary, String kind, Set<String> blockers) {
        if (name == null || name.isBlank())
            blockers.add("retirement-" + kind + "-class-identity-missing");
        else if (primary.contains(name))
            blockers.add("retirement-" + kind + "-class-collides-with-primary:" + name);
        else if (!result.add(name))
            blockers.add("retirement-presentation-class-identity-collision:" + name);
    }

    private static LinkedHashSet<String> declaredSet(JsonObject source,
            String property, String prefix, Set<String> blockers) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (JsonElement item : ProcessorRetirementJson.array(source, property)) {
            if (!item.isJsonPrimitive()) blockers.add(prefix + "-identity-malformed");
            else {
                String value = item.getAsString();
                if (value.isBlank()) blockers.add(prefix + "-identity-blank");
                else if (!result.add(value)) blockers.add(prefix + "-identity-duplicate:" + value);
            }
        }
        return result;
    }

    private static boolean isNested(String name, Set<String> bases) {
        if (name == null || name.isBlank()) return false;
        for (String base : bases) if (name.startsWith(base + "$")) return true;
        return false;
    }
}
