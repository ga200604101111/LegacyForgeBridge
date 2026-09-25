package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.yinghuang.legacyforgebridge.convert.LegacyBlockActivationCompiler;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/** Materializes bounded, source-proven Minecraft 1.7 block activation decisions. */
public final class LegacyBlockActivationPass implements ConversionPass {
    public static final String RULES_PATH = "legacyforgebridge/block-activation-rules.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override public String id() { return "legacy-block-activation"; }

    @Override
    public void apply(ConversionContext context) throws Exception {
        LegacyBlockActivationCompiler.Analysis analysis = new LegacyBlockActivationCompiler().compile(context.sourceJar());

        // No activation callback means there is no semantic surface for this optional pass. Do not
        // forward the registry analyzer's standalone "no concrete registrations" diagnostic for
        // unrelated resource/item-only JARs.
        if (analysis.activationCallbacks() == 0) return;

        var effects = LegacyBlockActivationEffectsPass.materialize(context);
        JsonArray rules = new JsonArray();
        int skipped = Math.max(0, analysis.activationCallbacks() - analysis.programs().size() - effects.writtenRules());
        for (LegacyBlockActivationCompiler.Program program : analysis.programs()) {
            String legacyId = legacyId(context, program.legacyNamespace(), program.registryName());
            String modernId = modernBlockId(context, legacyId);
            if (modernId == null) {
                skipped++;
                context.diagnostics().warning("LFB-CONVERT-BLOCK-ACTIVATION-0003", SupportLevel.MANUAL_REQUIRED,
                        "No generated modern block identity exists for source activation rule " + legacyId + ".");
                continue;
            }

            JsonObject rule = new JsonObject();
            rule.addProperty("id", modernId);
            rule.addProperty("legacyId", legacyId);
            rule.add("program", program(program));
            JsonObject provenance = new JsonObject();
            provenance.addProperty("owner", program.sourceOwner());
            provenance.addProperty("method", program.sourceMethod());
            provenance.addProperty("descriptor", program.sourceDescriptor());
            rule.add("provenance", provenance);
            rules.add(rule);
        }

        for (String diagnostic : analysis.diagnostics()) {
            // Suppress a pure-compiler rejection ONLY when every registration sharing this
            // callback has a complete, materialized effect rule. Missing identity stays visible.
            boolean covered = effects.completedCallbacks().stream().anyMatch(key ->
                    diagnostic.startsWith("Unsupported pure activation callback " + key + ":"));
            if (!covered) context.diagnostics().warning("LFB-CONVERT-BLOCK-ACTIVATION-0002", SupportLevel.MANUAL_REQUIRED, diagnostic);
        }

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.add("rules", rules);
        root.addProperty("sourceCallbacks", analysis.activationCallbacks());
        root.addProperty("effectRules", effects.writtenRules());
        root.addProperty("skippedRules", skipped);
        Path output = context.stagingDir().resolve(RULES_PATH);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        if (!rules.isEmpty()) {
            context.diagnostics().info("LFB-CONVERT-BLOCK-ACTIVATION-0001", SupportLevel.RUNTIME_BRIDGE,
                    "Materialized " + rules.size() + " source-proven pure block activation rules; skipped=" + skipped + ".");
        }
    }

    private static JsonObject program(LegacyBlockActivationCompiler.Program program) {
        JsonObject output = new JsonObject();
        JsonArray instructions = new JsonArray();
        for (var instruction : program.instructions()) {
            JsonObject value = new JsonObject();
            value.addProperty("op", instruction.op().name());
            value.addProperty("operand", instruction.operand());
            value.addProperty("target", instruction.target());
            value.add("keys", integers(instruction.keys()));
            value.add("targets", integers(instruction.targets()));
            instructions.add(value);
        }
        output.add("instructions", instructions);
        return output;
    }

    private static JsonArray integers(java.util.List<Integer> values) {
        JsonArray output = new JsonArray();
        for (Integer value : values) output.add(value);
        return output;
    }

    private static String legacyId(ConversionContext context, String namespace, String registryName) {
        String resolvedNamespace = namespace;
        if (resolvedNamespace == null || resolvedNamespace.isBlank()) {
            resolvedNamespace = context.metadata().primary().modId();
        }
        return resolvedNamespace + ":" + registryName;
    }

    private static String modernBlockId(ConversionContext context, String legacyId) {
        Map<String, String> blocks = context.registryIdentities().get("blocks");
        if (blocks == null) return null;
        String value = blocks.get(legacyId);
        if (value != null) return value;
        return blocks.get(legacyId.toLowerCase(Locale.ROOT));
    }
}
