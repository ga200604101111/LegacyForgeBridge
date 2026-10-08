package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.yinghuang.legacyforgebridge.convert.LegacyBlockTileModelPreflight;
import dev.yinghuang.legacyforgebridge.convert.LegacyBlockTileVisualStateAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyTileFacingRotationAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyTileDynamicYawAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Map;
import java.util.LinkedHashMap;

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
            // A static six-facing map is independent of TileEntity animation/sync proof.
            // Rev285's state observation must never become a fake client networking bridge.
            Map<String, LegacyTileFacingRotationAnalyzer.Proof> facing = new LinkedHashMap<>();
            LegacyTileFacingRotationAnalyzer facingAnalyzer = new LegacyTileFacingRotationAnalyzer();
            for (var candidate : source.candidates()) {
                try {
                    var result = facingAnalyzer.analyze(context.sourceJar(),candidate);
                    result.proof().ifPresent(proof -> facing.put(candidate.sourceBlockClass(),proof));
                } catch (IOException | RuntimeException incomplete) {
                    context.diagnostics().info("LFB-CONVERT-BLOCK-TILE-0006",SupportLevel.AUTO,
                            "Optional static facing proof unavailable for " + candidate.sourceBlockClass()
                                    + " (" + incomplete.getClass().getSimpleName() + "); runtime unwired.");
                }
            }
            // Rev287: the optional, third source GL-Y rotation must read ONE actual
            // TileEntity field. This is angle-operand evidence, not packet/NBT proof.
            Map<String, LegacyTileDynamicYawAnalyzer.Proof> dynamicYaw=new LinkedHashMap<>();
            LegacyTileDynamicYawAnalyzer yawAnalyzer=new LegacyTileDynamicYawAnalyzer();
            for (var candidate:source.candidates()) {
                var staticFace=facing.get(candidate.sourceBlockClass());
                if (staticFace==null || !staticFace.additionalSourceGlRotationPresent())continue;
                try {
                    var dynamic=yawAnalyzer.analyze(context.sourceJar(),candidate,staticFace);
                    dynamic.proof().ifPresent(proof -> dynamicYaw.put(candidate.sourceBlockClass(),proof));
                } catch (IOException | RuntimeException incomplete) {
                    context.diagnostics().info("LFB-CONVERT-BLOCK-TILE-0007",SupportLevel.AUTO,
                            "Optional tile Y-axis operand source proof unavailable for "
                                    + candidate.sourceBlockClass()+" ("+incomplete.getClass().getSimpleName()
                                    + "); tile field sync and client rendering remain unwired.");
                }
            }
            var manifest=manifest(context.sourceHash(),context.metadata().primary().modId(),
                    source,visuals,facing,dynamicYaw);
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
        return manifest(sourceSha,modId,analysis,Map.of(),Map.of(),Map.of());
    }

    /** Optional per-source-Class observations only; no runtime admission is possible here. */
    public static JsonObject manifest(String sourceSha, String modId,
                                      LegacyBlockTileModelPreflight.Analysis analysis,
                                      Map<String, LegacyBlockTileVisualStateAnalyzer.Evidence> visualState) {
        return manifest(sourceSha,modId,analysis,visualState,Map.of(),Map.of());
    }

    /** Bounded, static source facing metadata; never implies client animation or tile sync. */
    public static JsonObject manifest(String sourceSha, String modId,
                                      LegacyBlockTileModelPreflight.Analysis analysis,
                                      Map<String, LegacyBlockTileVisualStateAnalyzer.Evidence> visualState,
                                      Map<String, LegacyTileFacingRotationAnalyzer.Proof> sourceFacingMaps) {
        return manifest(sourceSha,modId,analysis,visualState,sourceFacingMaps,Map.of());
    }

    /** Independent, source-causal GL operand evidence. Still no network/render runtime. */
    public static JsonObject manifest(String sourceSha, String modId,
                                      LegacyBlockTileModelPreflight.Analysis analysis,
                                      Map<String, LegacyBlockTileVisualStateAnalyzer.Evidence> visualState,
                                      Map<String, LegacyTileFacingRotationAnalyzer.Proof> sourceFacingMaps,
                                      Map<String, LegacyTileDynamicYawAnalyzer.Proof> sourceYawMaps) {
        Objects.requireNonNull(analysis);
        Objects.requireNonNull(visualState);
        Objects.requireNonNull(sourceFacingMaps);
        Objects.requireNonNull(sourceYawMaps);
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
        int observedState=0, tickDriven=0, staticFacing=0, sourceYaw=0;
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
                entry.addProperty("sourceAnimationRuntimeWired",false);
                if(!state.tickDrivenRenderFields().isEmpty())tickDriven++;
            }
            var map = sourceFacingMaps.get(p.sourceBlockClass());
            boolean facingProven = map != null && map.sourceTileClass().equals(p.tileClass())
                    && map.sourceRendererClass().equals(p.rendererClass()) && !map.runtimeWired();
            entry.addProperty("sourceFacingMapProven",facingProven);
            entry.addProperty("sourceFacingRendererRuntimeWired",false);
            if (facingProven) {
                staticFacing++;
                entry.addProperty("sourceFacingMetadataMask",map.sourceMetadataMask());
                entry.addProperty("sourceFacingDrawMethod",map.drawMethod());
                entry.addProperty("sourceFacingDrawDescriptor",map.drawDescriptor());
                entry.addProperty("sourceAdditionalGlRotationPresent",map.additionalSourceGlRotationPresent());
                JsonArray faces=new JsonArray();
                for (var face:map.facing0to15()) {
                    JsonObject element=new JsonObject();
                    element.addProperty("legacyMetadata",face.metadata());
                    element.addProperty("sourceRotationXDegrees",face.rotationXDegrees());
                    element.addProperty("sourceRotationZDegrees",face.rotationZDegrees());
                    faces.add(element);
                }
                entry.add("sourceFacingMap0to15",faces);
            }
            // Do NOT promote source-proven Y rotation to executable animation or assume
            // a legacy server's TileEntity field was serialized on any packet.
            var yaw=sourceYawMaps.get(p.sourceBlockClass());
            boolean yawProven=facingProven && yaw!=null
                    && yaw.sourceTileClass().equals(p.tileClass())
                    && yaw.sourceRendererClass().equals(p.rendererClass())
                    && yaw.drawMethod().equals(map.drawMethod())
                    && yaw.drawDescriptor().equals(map.drawDescriptor())
                    && yaw.unconditionalSourceRotation()
                    && !yaw.tileFieldSyncProven() && !yaw.runtimeWired();
            entry.addProperty("sourceDynamicYawOperandProven",yawProven);
            entry.addProperty("sourceDynamicYawFieldSyncProven",false);
            entry.addProperty("sourceDynamicYawRuntimeWired",false);
            if(yawProven) {
                sourceYaw++;
                entry.addProperty("sourceDynamicYawFieldOwner",yaw.sourceFieldOwner());
                entry.addProperty("sourceDynamicYawFieldName",yaw.sourceFieldName());
                entry.addProperty("sourceDynamicYawFieldDescriptor",yaw.sourceFieldDescriptor());
                entry.addProperty("sourceDynamicYawOperand",yaw.operand().name());
                entry.addProperty("sourceDynamicYawUnconditionalSourceRotation",
                        yaw.unconditionalSourceRotation());
                entry.addProperty("sourceYawFieldTickWrittenObserved",
                        relevant && state.tickDrivenRenderFields().contains(yaw.sourceFieldName()));
                entry.addProperty("sourceYawFieldPacketHookObserved", relevant && state.sourceTilePacketHookPresent());
            }
            entry.addProperty("runtimeReady",false);
            candidates.add(entry);
        }
        root.add("candidates",candidates);
        root.addProperty("sourceCandidates",candidates.size());
        root.addProperty("visualStateAuditCandidateCount",observedState);
        root.addProperty("tileTickVisualDependencyCandidateCount",tickDriven);
        root.addProperty("sourceStaticFacingMapCandidateCount",staticFacing);
        root.addProperty("sourceDynamicYawOperandCandidateCount",sourceYaw);
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
