package dev.longyu.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.longyu.legacyforgebridge.convert.LegacyBlockBehaviorAnalyzer;
import dev.longyu.legacyforgebridge.convert.api.ConversionContext;
import dev.longyu.legacyforgebridge.convert.api.ConversionPass;
import dev.longyu.legacyforgebridge.convert.api.SupportLevel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/** Emits source-owned Block callback provenance for later P1 compilers. */
public final class LegacyBlockBehaviorAnalysisPass implements ConversionPass {
    public static final String ANALYSIS_PATH = "legacyforgebridge/block-behavior-analysis.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public String id() {
        return "legacy-block-behavior-analysis";
    }

    @Override
    public void apply(ConversionContext context) throws IOException {
        LegacyBlockBehaviorAnalyzer.Analysis analysis = new LegacyBlockBehaviorAnalyzer().analyze(context.sourceJar());
        if (analysis.blocks().isEmpty()) return;

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        JsonArray blocks = new JsonArray();
        Map<String, String> identities = context.registryIdentities().getOrDefault("blocks", Map.of());

        for (LegacyBlockBehaviorAnalyzer.BlockBehavior behavior : analysis.blocks()) {
            JsonObject block = new JsonObject();
            block.addProperty("legacyRegistryName", behavior.registryName());
            if (behavior.legacyNamespace() != null && !behavior.legacyNamespace().isBlank()) {
                block.addProperty("legacyNamespace", behavior.legacyNamespace());
            }
            block.addProperty("sourceClass", behavior.implementationClass());

            String legacyNamespace = behavior.legacyNamespace();
            if (legacyNamespace == null || legacyNamespace.isBlank()) {
                legacyNamespace = context.metadata().primary().modId();
            }
            String legacyId = legacyNamespace + ":" + behavior.registryName();
            String modernId = identities.get(legacyId);
            if (modernId == null) modernId = identities.get(legacyId.toLowerCase(Locale.ROOT));
            if (modernId != null) {
                block.addProperty("id", modernId);
            } else {
                context.diagnostics().warning("LFB-CONVERT-BLOCK-0002", SupportLevel.MANUAL_REQUIRED,
                        "Block callback evidence exists but no modern registry identity was proven for " + legacyId + ".");
            }

            JsonArray callbacks = new JsonArray();
            for (LegacyBlockBehaviorAnalyzer.Callback callback : behavior.callbacks()) {
                JsonObject value = new JsonObject();
                value.addProperty("kind", callback.kind().name());
                value.addProperty("sourceOwner", callback.owner());
                value.addProperty("sourceMethod", callback.method());
                value.addProperty("sourceDescriptor", callback.descriptor());
                callbacks.add(value);
            }
            block.add("callbacks", callbacks);
            blocks.add(block);
        }
        root.add("blocks", blocks);

        Path output = context.stagingDir().resolve(ANALYSIS_PATH);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
        context.diagnostics().info("LFB-CONVERT-BLOCK-0003", SupportLevel.ADAPTED,
                "Inventoried source-owned legacy Block callbacks without executing source classes: blocks="
                        + analysis.blocks().size() + ", callbacks=" + analysis.callbackCount() + ".");
    }
}
