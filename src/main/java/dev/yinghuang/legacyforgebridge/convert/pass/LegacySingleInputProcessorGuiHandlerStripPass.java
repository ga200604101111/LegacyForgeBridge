package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacySingleInputProcessorAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacySingleInputProcessorGuiHandlerAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacySingleInputProcessorGuiHandlerBranchStripper;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Retires only the exact server/client GUI-id branches already replaced by the modern processor
 * menu/runtime. The shared legacy IGuiHandler and every unrelated GUI id remain intact.
 */
public final class LegacySingleInputProcessorGuiHandlerStripPass
        implements ConversionPass {
    public static final String OUTPUT =
            "legacyforgebridge/single-input-processor-gui-handler-strip.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public String id() {
        return "legacy-single-input-processor-gui-handler-strip";
    }

    @Override
    public void apply(ConversionContext context) throws Exception {
        Path processorPath =
                context.stagingDir().resolve(LegacySingleInputProcessorPass.OUTPUT);
        Path proofPath =
                context.stagingDir().resolve(LegacySingleInputProcessorGuiHandlerProofPass.OUTPUT);
        if (!Files.isRegularFile(processorPath) || !Files.isRegularFile(proofPath)) {
            return;
        }

        JsonObject processor = read(processorPath);
        JsonObject proofRoot = read(proofPath);
        if (integer(processor, "schemaVersion", -1) != 4
                || integer(proofRoot, "schemaVersion", -1) != 1
                || !context.sourceHash().equals(string(processor, "sourceSha256", ""))
                || !context.sourceHash().equals(string(proofRoot, "sourceSha256", ""))
                || !bool(proofRoot, "guiHandlerBranchProofAnalysisWired", false)) {
            return;
        }

        Map<String, LegacySingleInputProcessorAnalyzer.Rule> sourceRules =
                new LinkedHashMap<>();
        var topology = new LegacySingleInputProcessorAnalyzer()
                .analyze(context.sourceJar());
        for (var rule : topology.rules()) {
            sourceRules.putIfAbsent(rule.sourceBlockClass(), rule);
        }
        Map<String, JsonObject> proofByBlock =
                index(proofRoot, "rules", "sourceBlockClass");

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("guiHandlerBranchStripWired", true);
        root.addProperty("freshSourceReproofRequired", true);
        root.addProperty("sharedHandlerPreserved", true);
        root.addProperty("unrelatedGuiIdsPreserved", true);
        root.addProperty("sourceClassDeletionWired", false);

        JsonArray rules = new JsonArray();
        int complete = 0;
        int blocked = 0;
        int strippedBranches = 0;

        LegacySingleInputProcessorGuiHandlerAnalyzer analyzer =
                new LegacySingleInputProcessorGuiHandlerAnalyzer();
        LegacySingleInputProcessorGuiHandlerBranchStripper stripper =
                new LegacySingleInputProcessorGuiHandlerBranchStripper();

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
            String sourceGuiClass = presentation == null
                    ? null : string(presentation, "sourceGuiClass", null);
            LegacySingleInputProcessorAnalyzer.Rule sourceRule =
                    blockClass == null ? null : sourceRules.get(blockClass);
            JsonObject previousProof =
                    blockClass == null ? null : proofByBlock.get(blockClass);

            JsonObject value = new JsonObject();
            if (id != null) value.addProperty("id", id);
            if (blockClass != null) value.addProperty("sourceBlockClass", blockClass);
            if (tileClass != null) value.addProperty("sourceTileClass", tileClass);
            value.addProperty("legacyGuiId", guiId);
            if (sourceGuiClass != null) {
                value.addProperty("sourceGuiClass", sourceGuiClass);
            }

            JsonArray blockers = new JsonArray();
            boolean serverStripped = false;
            boolean clientStripped = false;
            int ruleStrippedBranches = 0;
            LegacySingleInputProcessorGuiHandlerAnalyzer.Proof freshProof = null;

            if (sourceRule == null || sourceGuiClass == null || guiId < 0) {
                blockers.add("processor-gui-handler-strip-input-incomplete");
            } else if (previousProof == null
                    || !bool(previousProof, "guiHandlerBranchProofComplete", false)) {
                blockers.add("processor-gui-handler-prior-proof-incomplete");
            } else {
                var freshAnalysis = analyzer.analyze(
                        context.sourceJar(), sourceRule, sourceGuiClass);
                freshAnalysis.diagnostics().forEach(diagnostic ->
                        context.diagnostics().warning(
                                "LFB-CONVERT-PROCESSOR-GUISTRIP-0003",
                                SupportLevel.MANUAL_REQUIRED,
                                diagnostic));
                freshProof = freshAnalysis.proofs().isEmpty()
                        ? null : freshAnalysis.proofs().getFirst();

                if (freshProof == null
                        || !freshProof.branchRetirementProofComplete()
                        || !freshProof.sameHandlerProven()) {
                    blockers.add("processor-gui-handler-fresh-reproof-incomplete");
                } else if (!sameProof(previousProof, freshProof, guiId)) {
                    blockers.add("processor-gui-handler-proof-drift");
                }
            }

            if (freshProof != null) {
                if (freshProof.handlerClass() != null) {
                    value.addProperty("handlerClass", freshProof.handlerClass());
                }
                if (freshProof.sourceContainerClass() != null) {
                    value.addProperty(
                            "sourceContainerClass", freshProof.sourceContainerClass());
                }
                value.addProperty(
                        "serverOtherCaseCount", freshProof.serverBranch().otherCaseCount());
                value.addProperty(
                        "clientOtherCaseCount", freshProof.clientBranch().otherCaseCount());
            }

            if (blockers.isEmpty() && freshProof != null) {
                Path handlerPath = context.stagingDir().resolve(
                        freshProof.handlerClass() + ".class");
                if (!Files.isRegularFile(handlerPath)) {
                    blockers.add("processor-gui-handler-staged-class-missing");
                } else {
                    byte[] original = Files.readAllBytes(handlerPath);
                    var target =
                            new LegacySingleInputProcessorGuiHandlerBranchStripper.Target(
                                    guiId,
                                    freshProof.serverBranch().methodName(),
                                    freshProof.serverBranch().methodDescriptor(),
                                    freshProof.clientBranch().methodName(),
                                    freshProof.clientBranch().methodDescriptor(),
                                    freshProof.sourceTileClass(),
                                    freshProof.sourceContainerClass(),
                                    freshProof.sourceGuiClass());
                    var result = stripper.strip(original, target);
                    result.blockers().forEach(blockers::add);
                    serverStripped = result.serverBranchStripped();
                    clientStripped = result.clientBranchStripped();
                    ruleStrippedBranches = result.strippedBranches();
                    if (blockers.isEmpty()
                            && serverStripped
                            && clientStripped
                            && ruleStrippedBranches == 2) {
                        Files.write(handlerPath, result.bytes());
                    }
                }
            }

            boolean stripComplete = blockers.isEmpty()
                    && serverStripped
                    && clientStripped
                    && ruleStrippedBranches == 2;
            value.addProperty("freshSourceReproofComplete",
                    freshProof != null
                            && freshProof.branchRetirementProofComplete()
                            && freshProof.sameHandlerProven());
            value.addProperty("serverBranchStripComplete", serverStripped);
            value.addProperty("clientBranchStripComplete", clientStripped);
            value.addProperty("strippedGuiHandlerBranches", ruleStrippedBranches);
            value.addProperty("guiHandlerBranchStripComplete", stripComplete);
            value.addProperty("sharedHandlerPreserved", true);
            value.addProperty("sourceClassDeletionWired", false);
            value.add("blockers", blockers);
            rules.add(value);

            if (stripComplete) {
                complete++;
                strippedBranches += ruleStrippedBranches;
            } else {
                blocked++;
            }
        }

        root.add("rules", rules);
        root.addProperty("evaluatedRuntimeRules", rules.size());
        root.addProperty("guiHandlerBranchStripCompleteRules", complete);
        root.addProperty("guiHandlerBranchStripBlockedRules", blocked);
        root.addProperty("strippedGuiHandlerBranches", strippedBranches);

        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(
                output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        if (complete > 0) {
            context.diagnostics().info(
                    "LFB-CONVERT-PROCESSOR-GUISTRIP-0001",
                    SupportLevel.ADAPTED,
                    "Retired " + strippedBranches
                            + " exact legacy processor GUI-handler branch(es) across "
                            + complete
                            + " runtime-complete processor rule(s) while preserving the shared "
                            + "handler and unrelated GUI ids.");
        }
        if (blocked > 0) {
            context.diagnostics().warning(
                    "LFB-CONVERT-PROCESSOR-GUISTRIP-0002",
                    SupportLevel.RUNTIME_BRIDGE,
                    "Kept legacy processor GUI-handler branches for "
                            + blocked
                            + " rule(s) because fresh source proof, staged-bytecode identity, "
                            + "or post-rewrite reference closure was incomplete.");
        }
    }

    private static boolean sameProof(
            JsonObject previous,
            LegacySingleInputProcessorGuiHandlerAnalyzer.Proof fresh,
            int guiId) {
        if (previous == null || fresh == null) return false;
        if (integer(previous, "legacyGuiId", Integer.MIN_VALUE) != guiId) {
            return false;
        }
        if (!equalsNullable(
                string(previous, "handlerClass", null), fresh.handlerClass())) {
            return false;
        }
        if (!equalsNullable(
                string(previous, "sourceContainerClass", null),
                fresh.sourceContainerClass())) {
            return false;
        }
        if (!equalsNullable(
                string(previous, "sourceGuiClass", null), fresh.sourceGuiClass())) {
            return false;
        }

        JsonObject server = object(previous, "serverBranch");
        JsonObject client = object(previous, "clientBranch");
        return server != null
                && client != null
                && string(server, "methodName", "").equals(
                        fresh.serverBranch().methodName())
                && string(server, "methodDescriptor", "").equals(
                        fresh.serverBranch().methodDescriptor())
                && string(client, "methodName", "").equals(
                        fresh.clientBranch().methodName())
                && string(client, "methodDescriptor", "").equals(
                        fresh.clientBranch().methodDescriptor())
                && bool(server, "uniqueCaseLabelProven", false)
                && bool(client, "uniqueCaseLabelProven", false)
                && bool(server, "complete", false)
                && bool(client, "complete", false);
    }

    private static boolean equalsNullable(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static Map<String, JsonObject> index(
            JsonObject root, String arrayName, String keyName) {
        Map<String, JsonObject> result = new LinkedHashMap<>();
        for (JsonElement element : array(root, arrayName)) {
            if (!element.isJsonObject()) continue;
            JsonObject value = element.getAsJsonObject();
            String key = string(value, keyName, null);
            if (key != null) result.putIfAbsent(key, value);
        }
        return result;
    }

    private static JsonObject read(Path path) throws Exception {
        return JsonParser.parseString(
                Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
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
