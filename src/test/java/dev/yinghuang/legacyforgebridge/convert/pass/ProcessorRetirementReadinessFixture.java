package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class ProcessorRetirementReadinessFixture {
    private ProcessorRetirementReadinessFixture() { }

    static void write(Path staging, List<String> companions) throws Exception {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", "sha");
        root.addProperty("retirementReadinessAnalysisWired", true);
        root.addProperty("retirementAuthorizationWired", false);
        root.addProperty("sourceClassDeletionWired", false);

        JsonObject rule = new JsonObject();
        rule.addProperty("id", "foreign:processor");
        rule.addProperty("sourceBlockClass", ProcessorRetirementTestSupport.BLOCK);
        rule.addProperty("sourceTileClass", ProcessorRetirementTestSupport.TILE);
        rule.addProperty("sourceContainerClass", ProcessorRetirementTestSupport.CONTAINER);
        rule.addProperty("sourceGuiClass", ProcessorRetirementTestSupport.SCREEN);
        for (String gate : List.of(
                "modernRuntimeReplacementComplete", "tileRegistrationStripComplete",
                "blockRegistrationStripComplete", "blockConstructorReplacementProven",
                "blockConstructionRuntimeWired", "blockAllocationEffectsRuntimeWired",
                "blockSourceAllocationStripComplete", "tileConstructorReplacementProven",
                "guiHandlerBranchProofComplete", "guiHandlerBranchStripComplete",
                "guiHandlerRetirementComplete", "processorPresentationCohortExpanded",
                "retirementCohortCandidateReady")) rule.addProperty(gate, true);

        JsonArray presentation = array(
                ProcessorRetirementTestSupport.CONTAINER,
                ProcessorRetirementTestSupport.SCREEN);
        rule.addProperty("presentationSourceClassCount", presentation.size());
        rule.add("presentationSourceClasses", presentation);
        JsonArray nested = new JsonArray();
        companions.forEach(nested::add);
        rule.addProperty("nestedCompanionClassCount", nested.size());
        rule.add("nestedCompanionClasses", nested);
        rule.add("blockers", new JsonArray());

        JsonArray rules = new JsonArray();
        rules.add(rule);
        root.add("rules", rules);
        Path output = staging.resolve(LegacySingleInputProcessorRetirementReadiness.OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output,
                new GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n",
                StandardCharsets.UTF_8);
    }

    private static JsonArray array(String... values) {
        JsonArray result = new JsonArray();
        for (String value : values) result.add(value);
        return result;
    }
}
