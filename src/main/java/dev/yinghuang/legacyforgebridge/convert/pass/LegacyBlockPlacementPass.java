package dev.longyu.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.longyu.legacyforgebridge.convert.LegacyBlockPlacementCompiler;
import dev.longyu.legacyforgebridge.convert.LegacyItemBlockPlacementAnalyzer;
import dev.longyu.legacyforgebridge.convert.LegacyPureIntFunctionCompiler;
import dev.longyu.legacyforgebridge.convert.api.ConversionContext;
import dev.longyu.legacyforgebridge.convert.api.ConversionPass;
import dev.longyu.legacyforgebridge.convert.api.SupportLevel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Materializes the proven pure 1.7 ItemBlock metadata -> Block placement metadata pipeline. */
public final class LegacyBlockPlacementPass implements ConversionPass {
    public static final String RULES_PATH = "legacyforgebridge/block-placement-rules.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override public String id() { return "legacy-block-placement"; }

    @Override
    public void apply(ConversionContext context) throws Exception {
        LegacyBlockPlacementCompiler.Analysis blockAnalysis = new LegacyBlockPlacementCompiler().compile(context.sourceJar());
        LegacyItemBlockPlacementAnalyzer.Analysis itemAnalysis = new LegacyItemBlockPlacementAnalyzer().analyze(context.sourceJar());

        // A resource-only or item-only JAR has no block placement semantic surface. The underlying
        // registry analyzer deliberately reports "no concrete GameRegistry registrations" for its
        // own standalone use; that absence is not a conversion warning for this optional pass.
        if (blockAnalysis.programs().isEmpty() && itemAnalysis.behaviors().isEmpty()) return;

        Map<String, LegacyItemBlockPlacementAnalyzer.Behavior> itemByLegacyId = new LinkedHashMap<>();
        for (var behavior : itemAnalysis.behaviors()) {
            itemByLegacyId.put(legacyId(context, behavior.legacyNamespace(), behavior.registryName()), behavior);
        }

        JsonArray rules = new JsonArray();
        int skipped = 0;
        for (LegacyBlockPlacementCompiler.Program placement : blockAnalysis.programs()) {
            String legacyId = legacyId(context, placement.legacyNamespace(), placement.registryName());
            LegacyItemBlockPlacementAnalyzer.Behavior item = itemByLegacyId.get(legacyId);
            if (item == null) {
                skipped++;
                context.diagnostics().warning("LFB-CONVERT-BLOCK-PLACEMENT-0003", SupportLevel.MANUAL_REQUIRED,
                        "Pure source Block placement was proven for " + legacyId
                                + " but its ItemBlock metadata path could not be proven; placement rule was not emitted.");
                continue;
            }
            if (item.customPlaceBlockAt()) {
                skipped++;
                context.diagnostics().warning("LFB-CONVERT-BLOCK-PLACEMENT-0004", SupportLevel.MANUAL_REQUIRED,
                        "Block " + legacyId + " uses source ItemBlock.placeBlockAt at " + item.placeBlockAtOwner()
                                + "; default modern BlockItem placement is not substituted for that custom hook.");
                continue;
            }
            String modernId = modernBlockId(context, legacyId);
            if (modernId == null) {
                skipped++;
                context.diagnostics().warning("LFB-CONVERT-BLOCK-PLACEMENT-0005", SupportLevel.MANUAL_REQUIRED,
                        "No generated modern block identity exists for source placement rule " + legacyId + ".");
                continue;
            }

            JsonObject rule = new JsonObject();
            rule.addProperty("id", modernId);
            rule.addProperty("legacyId", legacyId);
            rule.add("itemMetadata", intProgram(item.metadataProgram()));
            rule.add("blockPlacement", placementProgram(placement));
            JsonObject provenance = new JsonObject();
            provenance.addProperty("itemMetadataOwner", item.metadataOwner());
            provenance.addProperty("itemMetadataMethod", item.metadataMethod());
            provenance.addProperty("blockOwner", placement.sourceOwner());
            provenance.addProperty("blockMethod", placement.sourceMethod());
            provenance.addProperty("blockDescriptor", placement.sourceDescriptor());
            rule.add("provenance", provenance);
            rules.add(rule);
        }

        for (String diagnostic : blockAnalysis.diagnostics()) {
            context.diagnostics().warning("LFB-CONVERT-BLOCK-PLACEMENT-0002", SupportLevel.MANUAL_REQUIRED, diagnostic);
        }
        for (String diagnostic : itemAnalysis.diagnostics()) {
            context.diagnostics().warning("LFB-CONVERT-BLOCK-PLACEMENT-0006", SupportLevel.MANUAL_REQUIRED, diagnostic);
        }

        if (rules.isEmpty() && skipped == 0) return;
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.add("rules", rules);
        root.addProperty("skippedRules", skipped);
        Path output = context.stagingDir().resolve(RULES_PATH);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        if (!rules.isEmpty()) {
            context.diagnostics().info("LFB-CONVERT-BLOCK-PLACEMENT-0001", SupportLevel.RUNTIME_BRIDGE,
                    "Materialized " + rules.size() + " source-proven legacy block placement rules; skipped=" + skipped + ".");
        }
    }

    private static String legacyId(ConversionContext context, String namespace, String registryName) {
        String resolvedNamespace = namespace;
        if (resolvedNamespace == null || resolvedNamespace.isBlank()) resolvedNamespace = context.metadata().primary().modId();
        return resolvedNamespace + ":" + registryName;
    }

    private static String modernBlockId(ConversionContext context, String legacyId) {
        Map<String, String> blocks = context.registryIdentities().get("blocks");
        if (blocks == null) return null;
        String value = blocks.get(legacyId);
        if (value != null) return value;
        return blocks.get(legacyId.toLowerCase(Locale.ROOT));
    }

    private static JsonObject intProgram(LegacyPureIntFunctionCompiler.Program program) {
        JsonObject output = new JsonObject();
        output.addProperty("inputLocal", program.inputLocal());
        JsonArray instructions = new JsonArray();
        for (var instruction : program.instructions()) {
            JsonObject value = commonInstruction(instruction.op().name(), instruction.operand(), instruction.value(), instruction.target());
            value.add("keys", integers(instruction.keys()));
            value.add("targets", integers(instruction.targets()));
            instructions.add(value);
        }
        output.add("instructions", instructions);
        return output;
    }

    private static JsonObject placementProgram(LegacyBlockPlacementCompiler.Program program) {
        JsonObject output = new JsonObject();
        JsonArray instructions = new JsonArray();
        for (var instruction : program.instructions()) {
            JsonObject value = commonInstruction(instruction.op().name(), instruction.operand(), instruction.number(), instruction.target());
            value.add("keys", integers(instruction.keys()));
            value.add("targets", integers(instruction.targets()));
            instructions.add(value);
        }
        output.add("instructions", instructions);
        return output;
    }

    private static JsonObject commonInstruction(String op, int operand, double number, int target) {
        JsonObject value = new JsonObject();
        value.addProperty("op", op);
        value.addProperty("operand", operand);
        value.addProperty("number", number);
        value.addProperty("target", target);
        return value;
    }

    private static JsonArray integers(java.util.List<Integer> values) {
        JsonArray output = new JsonArray();
        for (Integer value : values) output.add(value);
        return output;
    }
}
