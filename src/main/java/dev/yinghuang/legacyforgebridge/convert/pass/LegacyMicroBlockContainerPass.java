package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.yinghuang.legacyforgebridge.convert.LegacyMicroBlockContainerAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Materializes source-proven N^3 micro-block container rules for the modern client runtime. */
public final class LegacyMicroBlockContainerPass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/micro-block-container-rules.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override public String id() { return "legacy-micro-block-container"; }

    @Override
    public void apply(ConversionContext context) throws Exception {
        var analysis = new LegacyMicroBlockContainerAnalyzer().analyze(context.sourceJar());
        if (analysis.rules().isEmpty() && analysis.diagnostics().isEmpty()) return;

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("runtimeImplementationWired", true);
        root.addProperty("blockEntityRuntimeWired", true);
        root.addProperty("worldPresentationRuntimeWired", true);
        root.addProperty("dynamicCollisionRuntimeWired", true);
        JsonArray rules = new JsonArray();
        for (var rule : analysis.rules()) {
            JsonObject value = new JsonObject();
            value.addProperty("id", context.metadata().fabricId() + ":" + modernPath(rule.registryName()));
            value.addProperty("legacyRegistryName", rule.registryName());
            value.addProperty("sourceBlockClass", rule.sourceBlockClass());
            value.addProperty("sourceTileClass", rule.sourceTileClass());
            value.addProperty("sourceRendererClass", rule.sourceRendererClass());
            value.addProperty("listNbtKey", rule.listNbtKey());
            value.addProperty("sizeNbtKey", rule.sizeNbtKey());
            value.addProperty("minFieldSize", rule.minFieldSize());
            value.addProperty("maxFieldSize", rule.maxFieldSize());
            value.addProperty("fallbackFieldSize", rule.fallbackFieldSize());
            value.addProperty("translucentPass", rule.translucentPass());
            value.addProperty("dynamicCellCollision", rule.dynamicCellCollision());
            value.addProperty("proof", rule.proof());
            value.addProperty("runtimeComplete", true);
            rules.add(value);
        }
        root.add("rules", rules);
        root.addProperty("runtimeCompleteRules", rules.size());

        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        for (String diagnostic : analysis.diagnostics())
            context.diagnostics().warning("LFB-CONVERT-MICROBLOCK-0002", SupportLevel.RUNTIME_BRIDGE, diagnostic);
        if (!rules.isEmpty()) context.diagnostics().info(
                "LFB-CONVERT-MICROBLOCK-0001",
                SupportLevel.ADAPTED,
                "Materialized " + rules.size()
                        + " source-proven micro-block container runtime rule(s): N^3 TileEntity state, S35/NBT sync, per-cell collision and scaled block presentation.");
    }

    private static String modernPath(String legacyName) {
        String path = legacyName == null ? "" : legacyName.trim().toLowerCase(Locale.ROOT)
                .replace('\\', '/').replaceAll("[^a-z0-9/._-]", "_").replaceAll("_+", "_");
        while (path.startsWith("/")) path = path.substring(1);
        return path.isBlank() ? "legacy_micro_block" : path;
    }
}
