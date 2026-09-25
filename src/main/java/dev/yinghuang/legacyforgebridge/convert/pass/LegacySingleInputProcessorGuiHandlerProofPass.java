package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacySingleInputProcessorAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacySingleInputProcessorGuiHandlerAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Materializes exact legacy IGuiHandler branch proof for runtime-complete processors. */
public final class LegacySingleInputProcessorGuiHandlerProofPass
        implements ConversionPass {
    public static final String OUTPUT =
            "legacyforgebridge/single-input-processor-gui-handler-proof.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public String id() {
        return "legacy-single-input-processor-gui-handler-proof";
    }

    @Override
    public void apply(ConversionContext context) throws Exception {
        Path processorPath =
                context.stagingDir().resolve(LegacySingleInputProcessorPass.OUTPUT);
        if (!Files.isRegularFile(processorPath)) return;

        JsonObject processor = JsonParser.parseString(
                Files.readString(processorPath, StandardCharsets.UTF_8)).getAsJsonObject();
        if (integer(processor, "schemaVersion", -1) != 4
                || !context.sourceHash().equals(string(processor, "sourceSha256", ""))) {
            return;
        }

        Map<String, LegacySingleInputProcessorAnalyzer.Rule> sourceRules =
                new LinkedHashMap<>();
        var topology = new LegacySingleInputProcessorAnalyzer()
                .analyze(context.sourceJar());
        for (var rule : topology.rules()) {
            sourceRules.putIfAbsent(rule.sourceBlockClass(), rule);
        }

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("guiHandlerBranchProofAnalysisWired", true);
        root.addProperty("sourceBranchMutationWired", false);
        root.addProperty("sourceClassDeletionWired", false);

        JsonArray rules = new JsonArray();
        int proven = 0;
        int blocked = 0;
        LegacySingleInputProcessorGuiHandlerAnalyzer analyzer =
                new LegacySingleInputProcessorGuiHandlerAnalyzer();

        for (JsonElement element : array(processor, "machines")) {
            if (!element.isJsonObject()) continue;
            JsonObject machine = element.getAsJsonObject();
            if (!bool(machine, "runtimeComplete", false)
                    || !bool(machine, "sourcePresentationComplete", false)) {
                continue;
            }

            String id = string(machine, "id", null);
            String blockClass = string(machine, "sourceBlockClass", null);
            String tileClass = string(machine, "sourceTileClass", null);
            int guiId = integer(machine, "legacyGuiId", -1);
            JsonObject presentation = object(machine, "presentation");
            String sourceGuiClass =
                    presentation == null ? null
                            : string(presentation, "sourceGuiClass", null);
            LegacySingleInputProcessorAnalyzer.Rule sourceRule =
                    blockClass == null ? null : sourceRules.get(blockClass);

            JsonObject value = new JsonObject();
            if (id != null) value.addProperty("id", id);
            if (blockClass != null) value.addProperty("sourceBlockClass", blockClass);
            if (tileClass != null) value.addProperty("sourceTileClass", tileClass);
            value.addProperty("legacyGuiId", guiId);
            if (sourceGuiClass != null) {
                value.addProperty("sourceGuiClass", sourceGuiClass);
            }

            JsonArray blockers = new JsonArray();
            boolean complete = false;
            if (sourceRule == null || sourceGuiClass == null || guiId < 0) {
                blockers.add("processor-gui-handler-proof-input-incomplete");
            } else {
                var analysis = analyzer.analyze(
                        context.sourceJar(), sourceRule, sourceGuiClass);
                analysis.diagnostics().forEach(diagnostic ->
                        context.diagnostics().warning(
                                "LFB-CONVERT-PROCESSOR-GUIHANDLER-0003",
                                SupportLevel.MANUAL_REQUIRED,
                                diagnostic));

                LegacySingleInputProcessorGuiHandlerAnalyzer.Proof proof =
                        analysis.proofs().isEmpty()
                                ? null : analysis.proofs().getFirst();
                if (proof == null) {
                    blockers.add("processor-gui-handler-proof-missing");
                } else {
                    if (proof.handlerClass() != null) {
                        value.addProperty("handlerClass", proof.handlerClass());
                    }
                    if (proof.sourceContainerClass() != null) {
                        value.addProperty(
                                "sourceContainerClass",
                                proof.sourceContainerClass());
                    }
                    value.addProperty(
                            "sameHandlerProven", proof.sameHandlerProven());
                    value.add("serverBranch", branch(proof.serverBranch()));
                    value.add("clientBranch", branch(proof.clientBranch()));
                    proof.blockers().forEach(blockers::add);
                    complete = proof.branchRetirementProofComplete()
                            && proof.sameHandlerProven()
                            && blockers.isEmpty();
                }
            }

            value.addProperty("guiHandlerBranchProofComplete", complete);
            value.addProperty("sourceBranchMutationWired", false);
            value.add("blockers", blockers);
            rules.add(value);
            if (complete) proven++; else blocked++;
        }

        root.add("rules", rules);
        root.addProperty("evaluatedRuntimeRules", rules.size());
        root.addProperty("guiHandlerBranchProofCompleteRules", proven);
        root.addProperty("guiHandlerBranchProofBlockedRules", blocked);

        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        if (proven > 0) {
            context.diagnostics().info(
                    "LFB-CONVERT-PROCESSOR-GUIHANDLER-0001",
                    SupportLevel.RUNTIME_BRIDGE,
                    "Proved exact server/client IGuiHandler branches for "
                            + proven + " runtime-complete processor rule(s); "
                            + "branch mutation remains intentionally unwired.");
        }
        if (blocked > 0) {
            context.diagnostics().warning(
                    "LFB-CONVERT-PROCESSOR-GUIHANDLER-0002",
                    SupportLevel.RUNTIME_BRIDGE,
                    "Processor GUI-handler branch proof remains blocked for "
                            + blocked + " runtime-complete rule(s).");
        }
    }

    private static JsonObject branch(
            LegacySingleInputProcessorGuiHandlerAnalyzer.BranchProof proof) {
        JsonObject value = new JsonObject();
        value.addProperty("methodName", proof.methodName());
        value.addProperty("methodDescriptor", proof.methodDescriptor());
        if (proof.switchKind() != null) {
            value.addProperty("switchKind", proof.switchKind());
        }
        value.addProperty("guiIdDispatchProven", proof.guiIdDispatchProven());
        value.addProperty(
                "uniqueCaseLabelProven", proof.uniqueCaseLabelProven());
        value.addProperty(
                "simpleCaseFlowProven", proof.simpleCaseFlowProven());
        value.addProperty("tileHandoffProven", proof.tileHandoffProven());
        if (proof.constructedClass() != null) {
            value.addProperty("constructedClass", proof.constructedClass());
        }
        value.addProperty("returnObjectProven", proof.returnObjectProven());
        value.addProperty("otherCaseCount", proof.otherCaseCount());
        value.addProperty("complete", proof.complete());
        JsonArray blockers = new JsonArray();
        proof.blockers().forEach(blockers::add);
        value.add("blockers", blockers);
        return value;
    }

    private static JsonObject object(JsonObject root, String name) {
        JsonElement value = root == null ? null : root.get(name);
        return value != null && value.isJsonObject()
                ? value.getAsJsonObject() : null;
    }

    private static JsonArray array(JsonObject root, String name) {
        JsonElement value = root == null ? null : root.get(name);
        return value != null && value.isJsonArray()
                ? value.getAsJsonArray() : new JsonArray();
    }

    private static boolean bool(JsonObject root, String name, boolean fallback) {
        JsonElement value = root == null ? null : root.get(name);
        return value != null && value.isJsonPrimitive()
                ? value.getAsBoolean() : fallback;
    }

    private static int integer(JsonObject root, String name, int fallback) {
        JsonElement value = root == null ? null : root.get(name);
        return value != null && value.isJsonPrimitive()
                ? value.getAsInt() : fallback;
    }

    private static String string(JsonObject root, String name, String fallback) {
        JsonElement value = root == null ? null : root.get(name);
        return value != null && value.isJsonPrimitive()
                ? value.getAsString() : fallback;
    }
}
