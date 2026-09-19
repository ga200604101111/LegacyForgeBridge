package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Set;

final class ProcessorRetirementGates {
    private static final String[][] REQUIRED_GATES = {
            {"modernRuntimeReplacementComplete", "processor-runtime-replacement-incomplete"},
            {"tileRegistrationStripComplete", "tile-registration-retirement-incomplete"},
            {"blockRegistrationStripComplete", "block-registration-retirement-incomplete"},
            {"blockConstructorReplacementProven", "block-constructor-replacement-incomplete"},
            {"blockConstructionRuntimeWired", "block-construction-runtime-not-wired"},
            {"blockAllocationEffectsRuntimeWired", "block-allocation-effects-runtime-not-wired"},
            {"blockSourceAllocationStripComplete", "block-source-allocation-retirement-incomplete"},
            {"tileConstructorReplacementProven", "tile-constructor-replacement-incomplete"},
            {"guiHandlerBranchProofComplete", "gui-handler-branch-proof-incomplete"},
            {"guiHandlerBranchStripComplete", "gui-handler-branch-retirement-incomplete"},
            {"guiHandlerRetirementComplete", "gui-handler-retirement-incomplete"},
            {"processorPresentationCohortExpanded", "processor-presentation-cohort-not-expanded"}
    };

    private ProcessorRetirementGates() { }

    static void requireCandidate(JsonObject source, Set<String> blockers) {
        if (!ProcessorRetirementJson.bool(source, "retirementCohortCandidateReady", false)) {
            blockers.add("retirement-cohort-readiness-not-complete");
            for (JsonElement item : ProcessorRetirementJson.array(source, "blockers"))
                if (item.isJsonPrimitive()) blockers.add("readiness:" + item.getAsString());
        }
        for (String[] gate : REQUIRED_GATES)
            if (!ProcessorRetirementJson.bool(source, gate[0], false)) blockers.add(gate[1]);
    }
}
