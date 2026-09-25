package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.yinghuang.legacyforgebridge.convert.LegacyGridPotPresentationAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Materializes the source-proven legacy GridPot stored-content presentation surface. */
public final class LegacyGridPotPresentationProofPass implements ConversionPass {
    public static final String OUTPUT = "legacyforgebridge/grid-pot-presentation-proof.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override public String id() { return "legacy-grid-pot-presentation-proof"; }

    @Override
    public void apply(ConversionContext context) throws Exception {
        LegacyGridPotPresentationAnalyzer.Analysis analysis =
                new LegacyGridPotPresentationAnalyzer().analyze(context.sourceJar());
        if (analysis.proofs().isEmpty() && analysis.skipped().isEmpty()) return;

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("sourceSha256", context.sourceHash());
        root.addProperty("storedContentPresentationRuntimeWired", false);
        JsonArray rules = new JsonArray();
        for (LegacyGridPotPresentationAnalyzer.Proof proof : analysis.proofs()) {
            JsonObject value = new JsonObject();
            value.addProperty("registryName", proof.registryName());
            value.addProperty("sourceBlockClass", proof.sourceBlockClass());
            value.addProperty("sourceTileClass", proof.sourceTileClass());
            value.addProperty("sourceRendererClass", proof.sourceRendererClass());
            value.addProperty("cellCarrierLegacyRegistryName", proof.cellCarrierLegacyRegistryName());
            value.addProperty("flatInventorySourceProven", proof.flatInventory());
            if(proof.inventoryTextureName()!=null)value.addProperty("inventoryTextureName",proof.inventoryTextureName());
            value.addProperty("storedContentPresentationProven", true);
            JsonArray offsets = new JsonArray();
            proof.gridOffsets().forEach(offsets::add);
            value.add("gridOffsets", offsets);
            value.addProperty("contentTranslateY", proof.contentTranslateY());
            value.addProperty("crossedScale", proof.crossedScale());
            value.addProperty("cactusHalfWidth", proof.cactusHalfWidth());
            value.addProperty("storedContentPresentationRuntimeWired", false);
            rules.add(value);
        }
        root.add("rules", rules);

        JsonArray skipped = new JsonArray();
        for (LegacyGridPotPresentationAnalyzer.Skipped item : analysis.skipped()) {
            JsonObject value = new JsonObject();
            value.addProperty("registryName", item.registryName());
            value.addProperty("sourceBlockClass", item.sourceBlockClass());
            value.addProperty("reason", item.reason());
            skipped.add(value);
        }
        root.add("skipped", skipped);
        root.addProperty("proofCompleteRules", rules.size());
        root.addProperty("skippedRules", skipped.size());

        Path output = context.stagingDir().resolve(OUTPUT);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);

        for (String diagnostic : analysis.diagnostics())
            context.diagnostics().warning("LFB-CONVERT-GRIDPOT-PRESENT-0002", SupportLevel.MANUAL_REQUIRED, diagnostic);
        if (!analysis.proofs().isEmpty())
            context.diagnostics().info("LFB-CONVERT-GRIDPOT-PRESENT-0001", SupportLevel.ADAPTED,
                    "Source-proven GridPot stored-content presentation surfaces: " + analysis.proofs().size()
                            + "; modern ItemStack render-state wiring remains closed until the client runtime pass.");
    }
}
