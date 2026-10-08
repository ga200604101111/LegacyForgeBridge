package dev.yinghuang.legacyforgebridge.convert.pass;

import dev.yinghuang.legacyforgebridge.convert.LegacyTilePivotAnimationAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionStatus;
import dev.yinghuang.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Source animation is deliberately non-executable: a codegen/packet readiness regression guard. */
class LegacyBlockTilePivotAnimationManifestTest {
    private static dev.yinghuang.legacyforgebridge.convert.LegacyBlockTileModelPreflight.Candidate candidate() {
        return LegacyBlockTileFacingRotationManifestTest.candidate();
    }
    private static LegacyTilePivotAnimationAnalyzer.Proof proof(String tile,String renderer,String model){
        List<LegacyTilePivotAnimationAnalyzer.Part> parts=List.of(
                new LegacyTilePivotAnimationAnalyzer.Part("wing","rotationPointY",7.0F,
                        List.of("actualYaw","desiredYaw"),true,true),
                new LegacyTilePivotAnimationAnalyzer.Part("head","rotationPointY",7.0F,
                        List.of("actualYaw","desiredYaw"),true,true));
        return new LegacyTilePivotAnimationAnalyzer.Proof(tile,renderer,model,"animate","yawDelay",
                parts,false,false);
    }
    private static LegacyTilePivotAnimationAnalyzer.Proof valid(){
        var c=candidate();return proof(c.tileClass(),c.rendererClass(),c.modelClass());
    }
    private static com.google.gson.JsonObject sidecar(
            Map<String,LegacyTilePivotAnimationAnalyzer.Proof> evidence){
        return LegacyBlockTileModelPreflightPass.manifest("source-sha","foreign",
                LegacyBlockTileFacingRotationManifestTest.source(),Map.of(),Map.of(),Map.of(),evidence);
    }
    @Test void modelPartsAndSourceStateArePublishedButNoGameplayRuleExists(){
        var json=sidecar(Map.of(candidate().sourceBlockClass(),valid()));
        assertEquals(1,json.get("sourcePivotSineClampCandidateCount").getAsInt());
        var c=json.getAsJsonArray("candidates").get(0).getAsJsonObject();
        assertTrue(c.get("sourcePivotSineClampAnimationProven").getAsBoolean());
        assertEquals("animate",c.get("sourceModelAnimatorMethod").getAsString());
        assertEquals("yawDelay",c.get("sourceModelAnimationGuardTileField").getAsString());
        assertEquals(2,c.getAsJsonArray("sourcePivotAnimatedParts").size());
        var wing=c.getAsJsonArray("sourcePivotAnimatedParts").get(0).getAsJsonObject();
        assertEquals("wing",wing.get("sourceModelPartField").getAsString());
        assertTrue(wing.has("sourceRestPivotY"));
        assertEquals(2,wing.getAsJsonArray("sourceDynamicTileFields").size());
        assertTrue(wing.get("sourceUsesRenderPartialTick").getAsBoolean());
        assertTrue(wing.get("sourceSineClampAndPriorPivotProven").getAsBoolean());
        assertFalse(wing.get("sourceAnimatedPartRuntimeWired").getAsBoolean());
        assertFalse(c.get("sourcePivotTileSyncProven").getAsBoolean());
        assertFalse(c.get("sourcePivotAnimationRuntimeWired").getAsBoolean());
        assertFalse(c.get("runtimeReady").getAsBoolean());
        assertFalse(json.get("runtimeWired").getAsBoolean());
        assertFalse(json.get("animationSemanticsProven").getAsBoolean());
        assertFalse(json.get("tileStateSyncProven").getAsBoolean());
        assertFalse(json.has("rules"));
    }
    @Test void missingSourceEvidenceDoesNotInventAnAnimation(){
        var root=sidecar(Map.of());
        assertEquals(0,root.get("sourcePivotSineClampCandidateCount").getAsInt());
        var item=root.getAsJsonArray("candidates").get(0).getAsJsonObject();
        assertFalse(item.get("sourcePivotSineClampAnimationProven").getAsBoolean());
        assertFalse(item.has("sourcePivotAnimatedParts"));
    }
    @Test void unrelatedBlockKeyCannotInjectAnAnimation(){
        var root=sidecar(Map.of("foreign/other/Block",valid()));
        assertEquals(0,root.get("sourcePivotSineClampCandidateCount").getAsInt());
    }
    @Test void anotherSourceTileMustNotBecomeARegisteredAnimation(){
        var c=candidate();var root=sidecar(Map.of(c.sourceBlockClass(),proof("other/tile",c.rendererClass(),c.modelClass())));
        assertEquals(0,root.get("sourcePivotSineClampCandidateCount").getAsInt());
    }
    @Test void anotherRendererMustNotBecomeARegisteredAnimation(){
        var c=candidate();var root=sidecar(Map.of(c.sourceBlockClass(),proof(c.tileClass(),"other/Tesr",c.modelClass())));
        assertEquals(0,root.get("sourcePivotSineClampCandidateCount").getAsInt());
    }
    @Test void anotherModelMustNotBecomeARegisteredAnimation(){
        var c=candidate();var root=sidecar(Map.of(c.sourceBlockClass(),proof(c.tileClass(),c.rendererClass(),"other/Model")));
        assertEquals(0,root.get("sourcePivotSineClampCandidateCount").getAsInt());
    }
    @Test void existingOverloadsKeepAnimationDisabled(){
        var root=LegacyBlockTileModelPreflightPass.manifest("sha","mod",
                LegacyBlockTileFacingRotationManifestTest.source(),Map.of(),Map.of(),Map.of());
        assertEquals(0,root.get("sourcePivotSineClampCandidateCount").getAsInt());
        assertFalse(root.get("runtimeWired").getAsBoolean());
    }
    @Test void sourceRuntimeOrPacketClaimsAreNotConstructible(){
        var p=valid();
        boolean refusedPacket=false,refusedClient=false;
        try{new LegacyTilePivotAnimationAnalyzer.Proof(p.sourceTileClass(),p.sourceRendererClass(),
                p.sourceModelClass(),p.modelAnimationMethod(),p.guardTileField(),p.parts(),true,false);}
        catch(IllegalArgumentException correct){refusedPacket=true;}
        try{new LegacyTilePivotAnimationAnalyzer.Proof(p.sourceTileClass(),p.sourceRendererClass(),
                p.sourceModelClass(),p.modelAnimationMethod(),p.guardTileField(),p.parts(),false,true);}
        catch(IllegalArgumentException correct){refusedClient=true;}
        assertTrue(refusedPacket);assertTrue(refusedClient);
    }
    @Test void auditDiagnosticCannotDowngradeExistingConvertedResult(){
        var diagnostics=new DiagnosticCollector();
        diagnostics.warning("LFB-CONVERT-BLOCK-TILE-0008", SupportLevel.AUTO,
                "Source-only animator proof unavailable");
        assertEquals(ConversionStatus.CONVERTED,diagnostics.status());
    }
}
