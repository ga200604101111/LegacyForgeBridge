package dev.yinghuang.legacyforgebridge.convert.pass;

import dev.yinghuang.legacyforgebridge.convert.LegacyBlockTileVisualStateAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyTileDynamicYawAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionStatus;
import dev.yinghuang.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Diagnostic surface only. Even packet-hook observation must not become a network bridge. */
class LegacyBlockTileDynamicYawManifestTest {
    private static String tile(){return LegacyBlockTileFacingRotationManifestTest.candidate().tileClass();}
    private static String renderer(){return LegacyBlockTileFacingRotationManifestTest.candidate().rendererClass();}
    private static String key(){return LegacyBlockTileFacingRotationManifestTest.candidate().sourceBlockClass();}
    private static LegacyTileDynamicYawAnalyzer.Proof yaw(String name,String owner,String tileClass,
                                                            String render,String draw){
        return new LegacyTileDynamicYawAnalyzer.Proof(tileClass,render,draw,
                "(L"+tileClass+";DDDF)V",owner,name,"I",
                LegacyTileDynamicYawAnalyzer.Operand.INT_FIELD_TO_FLOAT,true,false,false);
    }
    private static LegacyTileDynamicYawAnalyzer.Proof yaw(){
        return yaw("yaw",tile(),tile(),renderer(),"sourceDraw");
    }
    private static Map<String,dev.yinghuang.legacyforgebridge.convert.LegacyTileFacingRotationAnalyzer.Proof> face(){
        return Map.of(key(),LegacyBlockTileFacingRotationManifestTest.proof(tile(),renderer()));
    }
    private static LegacyBlockTileVisualStateAnalyzer.Evidence visuals(boolean packets) {
        return new LegacyBlockTileVisualStateAnalyzer.Evidence(
                key(),tile(),renderer(),LegacyBlockTileFacingRotationManifestTest.candidate().modelClass(),
                List.of("yaw"),List.of("cooldown"),List.of("yaw"),List.of("yaw"),
                List.of("rotationPointY"),true,true,3,true,false,packets,
                packets ? LegacyBlockTileVisualStateAnalyzer.TileSyncAssessment.SOURCE_PACKET_HOOK_PRESENT_BUT_PAYLOAD_UNPROVEN
                        : LegacyBlockTileVisualStateAnalyzer.TileSyncAssessment.RENDER_STATE_SYNC_UNPROVEN,
                false,false,false);
    }
    private static com.google.gson.JsonObject manifest(
            Map<String,LegacyTileDynamicYawAnalyzer.Proof> yaw,
            Map<String,LegacyBlockTileVisualStateAnalyzer.Evidence> state,
            Map<String,dev.yinghuang.legacyforgebridge.convert.LegacyTileFacingRotationAnalyzer.Proof> face) {
        return LegacyBlockTileModelPreflightPass.manifest("sha", "foreign",
                LegacyBlockTileFacingRotationManifestTest.source(),state,face,yaw);
    }
    @Test void properSourceYawFieldIsEmittedButNoClientRuntimeIsEnabled(){
        var root=manifest(Map.of(key(),yaw()),Map.of(key(),visuals(false)),face());
        assertEquals(1,root.get("sourceDynamicYawOperandCandidateCount").getAsInt());
        var entry=root.getAsJsonArray("candidates").get(0).getAsJsonObject();
        assertTrue(entry.get("sourceDynamicYawOperandProven").getAsBoolean());
        assertEquals("yaw",entry.get("sourceDynamicYawFieldName").getAsString());
        assertEquals("I",entry.get("sourceDynamicYawFieldDescriptor").getAsString());
        assertEquals("INT_FIELD_TO_FLOAT",entry.get("sourceDynamicYawOperand").getAsString());
        assertTrue(entry.get("sourceYawFieldTickWrittenObserved").getAsBoolean());
        assertFalse(entry.get("sourceYawFieldPacketHookObserved").getAsBoolean());
        assertFalse(entry.get("sourceDynamicYawFieldSyncProven").getAsBoolean());
        assertFalse(entry.get("sourceDynamicYawRuntimeWired").getAsBoolean());
        assertFalse(entry.get("runtimeReady").getAsBoolean());
        assertFalse(root.get("tileStateSyncProven").getAsBoolean());
        assertFalse(root.has("rules"));
    }
    @Test void packetHookStillDoesNotProveYawFieldPayload(){
        var root=manifest(Map.of(key(),yaw()),Map.of(key(),visuals(true)),face());
        var entry=root.getAsJsonArray("candidates").get(0).getAsJsonObject();
        assertTrue(entry.get("sourceYawFieldPacketHookObserved").getAsBoolean());
        assertFalse(entry.get("sourceDynamicYawFieldSyncProven").getAsBoolean());
    }
    @Test void missingVisualStateDoesNotDisableExactFieldOperandProvenance(){
        var entry=manifest(Map.of(key(),yaw()),Map.of(),face())
                .getAsJsonArray("candidates").get(0).getAsJsonObject();
        assertTrue(entry.get("sourceDynamicYawOperandProven").getAsBoolean());
        assertFalse(entry.get("sourceYawFieldTickWrittenObserved").getAsBoolean());
    }
    @Test void unrelatedBlockSourceCannotInjectYawMetadata(){
        var result=manifest(Map.of("another/block",yaw()),Map.of(),face());
        assertEquals(0,result.get("sourceDynamicYawOperandCandidateCount").getAsInt());
        assertFalse(result.getAsJsonArray("candidates").get(0).getAsJsonObject()
                .get("sourceDynamicYawOperandProven").getAsBoolean());
    }
    @Test void unrelatedSourceRendererOrTileIsRejected(){
        for(var proof:List.of(
                yaw("yaw",tile(),"foreign/tile/Other",renderer(),"sourceDraw"),
                yaw("yaw",tile(),tile(),"foreign/client/OtherTESR","sourceDraw"))) {
            var result=manifest(Map.of(key(),proof),Map.of(),face());
            assertEquals(0,result.get("sourceDynamicYawOperandCandidateCount").getAsInt());
        }
    }
    @Test void mismatchedDrawMethodAndMissingFacingProofCannotClaimYaw(){
        var incompatible=yaw("yaw",tile(),tile(),renderer(),"wrongDraw");
        var one=manifest(Map.of(key(),incompatible),Map.of(),face());
        assertEquals(0,one.get("sourceDynamicYawOperandCandidateCount").getAsInt());
        var two=manifest(Map.of(key(),yaw()),Map.of(),Map.of());
        assertEquals(0,two.get("sourceDynamicYawOperandCandidateCount").getAsInt());
    }
    @Test void existingFiveArgumentManifestOverloadStillProducesNoExtraYaw(){
        var root=LegacyBlockTileModelPreflightPass.manifest("sha","foreign",
                LegacyBlockTileFacingRotationManifestTest.source(),Map.of(),face());
        assertEquals(0,root.get("sourceDynamicYawOperandCandidateCount").getAsInt());
        assertFalse(root.has("rules"));
    }
    @Test void invalidRuntimeProofCannotBeConstructed(){
        assertThrows(IllegalArgumentException.class,()->new LegacyTileDynamicYawAnalyzer.Proof(
                tile(),renderer(),"sourceDraw","(L"+tile()+";DDDF)V",tile(),"yaw","I",
                LegacyTileDynamicYawAnalyzer.Operand.INT_FIELD_TO_FLOAT,true,true,false));
        assertThrows(IllegalArgumentException.class,()->new LegacyTileDynamicYawAnalyzer.Proof(
                tile(),renderer(),"sourceDraw","(L"+tile()+";DDDF)V",tile(),"yaw","I",
                LegacyTileDynamicYawAnalyzer.Operand.INT_FIELD_TO_FLOAT,true,false,true));
    }
    @Test void optionalDiagnosticsNeverDowngradePreviouslyConvertibleContent(){
        var state=new DiagnosticCollector();
        state.warning("LFB-CONVERT-BLOCK-TILE-0007",SupportLevel.AUTO,"Optional Y source proof unavailable");
        assertEquals(ConversionStatus.CONVERTED,state.status());
    }
}
