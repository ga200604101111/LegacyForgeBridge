package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.yinghuang.legacyforgebridge.convert.LegacyBlockTileModelPreflight;
import dev.yinghuang.legacyforgebridge.convert.LegacyBlockTileVisualStateAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Map;

/**
 * Auditable, non-executable source provenance for ordinary 1.7.10 Block + TileEntity + TESR.
 * This does not create modern BlockEntity types or change gameplay conversion status.
 */
public final class LegacyBlockTileModelPreflightPass implements ConversionPass {
    public static final String OUTPUT="legacyforgebridge/block-tile-model-preflight.json";
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    @Override public String id(){return "legacy-block-tile-model-preflight";}

    @Override public void apply(ConversionContext context) {
        try {
            var source=new LegacyBlockTileModelPreflight().analyze(context.sourceJar());
            if (source.candidates().isEmpty() && source.skipped().isEmpty()) return;
            Map<String, LegacyBlockTileVisualStateAnalyzer.Evidence> visuals=Map.of();
            if (!source.candidates().isEmpty()) {
                try {
                    var state=new LegacyBlockTileVisualStateAnalyzer()
                            .analyze(context.sourceJar(),source.candidates());
                    visuals=state.byBlockSourceClass();
                    for (String note : state.diagnostics())
                        context.diagnostics().info("LFB-CONVERT-BLOCK-TILE-0004",SupportLevel.AUTO,
                                "Source-only visual state scan: "+note);
                } catch (IOException | RuntimeException incomplete) {
                    context.diagnostics().warning("LFB-CONVERT-BLOCK-TILE-0003",SupportLevel.AUTO,
                            "Optional tile visual state evidence unavailable ("
                                    +incomplete.getClass().getSimpleName()+"); runtime remains unwired.");
                }
            }
            var manifest=manifest(context.sourceHash(),context.metadata().primary().modId(),source,visuals);
            Path output=context.stagingDir().resolve(OUTPUT);
            Files.createDirectories(output.getParent());
            Files.writeString(output,JSON.toJson(manifest)+"\n",StandardCharsets.UTF_8);
            context.diagnostics().info("LFB-CONVERT-BLOCK-TILE-0001", SupportLevel.AUTO,
                    "Source-only Block+TileEntity+TESR provenance candidates="+source.candidates().size()
                            +", unadmitted="+source.skipped().size()
                            +"; no block entity runtime, animation or tile network bridge installed.");
        } catch (IOException | RuntimeException unavailable) {
            // This optional census must never turn a previously usable candidate into FAILED
            // or PARTIAL merely because its future renderer proof cannot be obtained.
            context.diagnostics().warning("LFB-CONVERT-BLOCK-TILE-0002", SupportLevel.AUTO,
                    "Optional Block/TileEntity renderer source evidence unavailable ("
                            +unavailable.getClass().getSimpleName()+"); no executable support added.");
        }
    }

    public static JsonObject manifest(String sourceSha, String modId,
                                      LegacyBlockTileModelPreflight.Analysis analysis) {
        return manifest(sourceSha,modId,analysis,Map.of());
    }

    /** Optional per-source-Class observations only; no runtime admission is possible here. */
    public static JsonObject manifest(String sourceSha, String modId,
                                      LegacyBlockTileModelPreflight.Analysis analysis,
                                      Map<String, LegacyBlockTileVisualStateAnalyzer.Evidence> visualState) {
        Objects.requireNonNull(analysis);
        Objects.requireNonNull(visualState);
        JsonObject root=new JsonObject();
        root.addProperty("schemaVersion",1);
        root.addProperty("sourceSha256",sourceSha);
        root.addProperty("legacyModId",modId);
        root.addProperty("family","LEGACY_SOURCE_BLOCK_TILE_MODEL");
        root.addProperty("sourceOnly",true);
        root.addProperty("runtimeWired",false);
        root.addProperty("blockEntityRuntimeWired",false);
        root.addProperty("animationSemanticsProven",false);
        root.addProperty("tileStateSyncProven",false);
        root.addProperty("modernBlockGeometryProven",false);
        JsonArray candidates=new JsonArray();
        int observedState=0, tickDriven=0;
        for (var p : analysis.candidates()) {
            JsonObject entry=new JsonObject();
            entry.addProperty("legacyBlockRegistryName",p.registryName());
            entry.addProperty("sourceBlockClass",p.sourceBlockClass());
            entry.addProperty("sourceTileClass",p.tileClass());
            entry.addProperty("legacyTileId",p.tileRegistryId());
            entry.addProperty("sourceRendererClass",p.rendererClass());
            entry.addProperty("sourceModelClass",p.modelClass());
            entry.addProperty("hasTileEntityDeclaredBy",p.hasTileEntityOwner());
            entry.addProperty("createTileEntityDeclaredBy",p.createTileEntityOwner());
            entry.addProperty("sourceTileTickPresent",p.sourceTileTickPresent());
            entry.addProperty("rendererCallsTileModelMethod",p.rendererCallsTileModelMethod());
            entry.addProperty("rendererReadsTileFields",p.rendererReadsTileFields());
            entry.addProperty("metadataDependentBoundsObserved",p.metadataDependentBounds());
            if(p.sourceConstantLight()!=null)entry.addProperty("sourceConstantLight",p.sourceConstantLight());
            entry.addProperty("sourceQuantityDroppedZeroProven",p.sourceQuantityDroppedZero());
            var state=visualState.get(p.sourceBlockClass());
            boolean relevant=state!=null && state.tileClass().equals(p.tileClass())
                    && state.rendererClass().equals(p.rendererClass())
                    && state.modelClass().equals(p.modelClass());
            entry.addProperty("sourceVisualStateAuditPresent",relevant);
            if(relevant) {
                observedState++;
                entry.add("rendererTileFieldsReadObserved",strings(state.rendererTileFieldsRead()));
                entry.add("modelAnimationTileFieldsReadObserved",strings(state.modelAnimationTileFieldsRead()));
                entry.add("tileTickFieldsWrittenObserved",strings(state.tileTickFieldsWritten()));
                entry.add("tickDrivenRenderFieldsObserved",strings(state.tickDrivenRenderFields()));
                entry.add("modelPivotFieldsWrittenObserved",strings(state.modelPivotFieldsWritten()));
                entry.addProperty("sourceModelAnimationCallObserved",state.modelAnimationCallObserved());
                entry.addProperty("sourceTileMetadataLookupObserved",state.tileMetadataLookupObserved());
                entry.addProperty("sourceGlRotateCallsObserved",state.sourceGlRotateCalls());
                entry.addProperty("sourceNegativeScaleObserved",state.negativeScaleObserved());
                entry.addProperty("sourceNbtHooksPresent",state.sourceNbtHooksPresent());
                entry.addProperty("sourceTilePacketHookPresent",state.sourceTilePacketHookPresent());
                entry.addProperty("sourceTileSyncAssessment",state.tileSyncAssessment().name());
                entry.addProperty("sourceTileStateSyncProven",false);
                entry.addProperty("sourceFacingMapProven",false);
                entry.addProperty("sourceAnimationRuntimeWired",false);
                if(!state.tickDrivenRenderFields().isEmpty())tickDriven++;
            }
            entry.addProperty("runtimeReady",false);
            candidates.add(entry);
        }
        root.add("candidates",candidates);
        root.addProperty("sourceCandidates",candidates.size());
        root.addProperty("visualStateAuditCandidateCount",observedState);
        root.addProperty("tileTickVisualDependencyCandidateCount",tickDriven);
        JsonArray skipped=new JsonArray();
        for(var p : analysis.skipped()){
            JsonObject entry=new JsonObject();
            entry.addProperty("legacyBlockRegistryName",p.registryName());
            entry.addProperty("sourceBlockClass",p.sourceBlockClass());
            entry.addProperty("reason",p.reason());
            skipped.add(entry);
        }
        root.add("skipped",skipped);
        JsonArray diagnostics=new JsonArray();
        analysis.diagnostics().forEach(diagnostics::add);
        root.add("analysisDiagnostics",diagnostics);
        return root;
    }
    private static JsonArray strings(java.util.List<String> text) {
        JsonArray array=new JsonArray();
        for(String value:text)array.add(value);
        return array;
    }
}
