package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.yinghuang.legacyforgebridge.convert.LegacyBlockMaterialProvenanceAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Materializes source-proven raw legacy Block Material constructor provenance.
 *
 * <p>This sidecar is evidence only. In particular, a Material field name is not interpreted as a
 * modern mining/tool rule here, and this pass does not enable block-drop gameplay runtime.</p>
 */
public final class LegacyBlockMaterialProvenancePass implements ConversionPass {
    public static final String OUTPUT_PATH = "legacyforgebridge/block-material-provenance.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public String id() {
        return "legacy-block-material-provenance";
    }

    @Override
    public void apply(ConversionContext context) throws IOException {
        LegacyBlockMaterialProvenanceAnalyzer.Analysis analysis =
                new LegacyBlockMaterialProvenanceAnalyzer().analyze(context.sourceJar());
        if (analysis.proofs().isEmpty()) return;

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());

        JsonArray blocks = new JsonArray();
        int complete = 0;
        int incomplete = 0;
        for (LegacyBlockMaterialProvenanceAnalyzer.Proof proof : analysis.proofs()) {
            JsonObject block = new JsonObject();
            block.addProperty("legacyRegistryName", proof.registryName());
            if (proof.legacyNamespace() != null && !proof.legacyNamespace().isBlank()) {
                block.addProperty("legacyNamespace", proof.legacyNamespace());
            }
            if (proof.implementationClass() != null && !proof.implementationClass().isBlank()) {
                block.addProperty("sourceClass", proof.implementationClass());
            }
            if (proof.directBlockSourceClass() != null && !proof.directBlockSourceClass().isBlank()) {
                block.addProperty("directBlockSourceClass", proof.directBlockSourceClass());
            }
            block.addProperty("complete", proof.complete());

            if (proof.material() != null) {
                JsonObject material = new JsonObject();
                material.addProperty("owner", proof.material().owner());
                material.addProperty("fieldName", proof.material().fieldName());
                material.addProperty("descriptor", proof.material().descriptor());
                block.add("material", material);
            }

            JsonArray reasons = new JsonArray();
            proof.reasons().forEach(reasons::add);
            block.add("reasons", reasons);
            blocks.add(block);

            if (proof.complete()) complete++;
            else incomplete++;
        }
        root.add("blocks", blocks);

        JsonArray diagnostics = new JsonArray();
        analysis.diagnostics().forEach(diagnostics::add);
        root.add("diagnostics", diagnostics);
        root.addProperty("completeBlocks", complete);
        root.addProperty("incompleteBlocks", incomplete);

        Path output = context.stagingDir().resolve(OUTPUT_PATH);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
        context.diagnostics().info(
                "LFB-CONVERT-BLOCK-MATERIAL-0001",
                SupportLevel.ADAPTED,
                "Materialized non-executing legacy Block Material constructor provenance: complete="
                        + complete + ", incomplete=" + incomplete + "."
        );
    }
}
