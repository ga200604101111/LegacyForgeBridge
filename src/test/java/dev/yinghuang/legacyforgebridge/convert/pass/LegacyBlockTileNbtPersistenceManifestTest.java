package dev.yinghuang.legacyforgebridge.convert.pass;

import dev.yinghuang.legacyforgebridge.convert.LegacyTileNbtPersistenceAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyTilePivotAnimationAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionStatus;
import dev.yinghuang.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** NBT save/read is separate from actual Forge1.7 server-to-Fabric packet delivery. */
class LegacyBlockTileNbtPersistenceManifestTest {
    private static dev.yinghuang.legacyforgebridge.convert.LegacyBlockTileModelPreflight.Candidate candidate(){
        return LegacyBlockTileFacingRotationManifestTest.candidate();
    }
    private static LegacyTilePivotAnimationAnalyzer.Proof pivot(){
        var c=candidate();
        return new LegacyTilePivotAnimationAnalyzer.Proof(c.tileClass(),c.rendererClass(),c.modelClass(),
                "animate","yawDelay",List.of(new LegacyTilePivotAnimationAnalyzer.Part(
                        "body","rotationPointY",7F,List.of("currentYaw","desiredYaw"),true,true)),false,false);
    }
    private static LegacyTileNbtPersistenceAnalyzer.Audit evidence(String tile,boolean full,
                                                                    boolean packet){
        List<String> required=List.of("currentYaw","desiredYaw","yawDelay");
        var persisted=new java.util.ArrayList<LegacyTileNbtPersistenceAnalyzer.StoredField>();
        persisted.add(new LegacyTileNbtPersistenceAnalyzer.StoredField(tile,"currentYaw","I","currentYaw","I"));
        if(full){
            persisted.add(new LegacyTileNbtPersistenceAnalyzer.StoredField(tile,"desiredYaw","I","desiredYaw","I"));
            persisted.add(new LegacyTileNbtPersistenceAnalyzer.StoredField(tile,"yawDelay","I","yawDelay","I"));
        }
        return new LegacyTileNbtPersistenceAnalyzer.Audit(tile,required,persisted,
                full?List.of():List.of("desiredYaw","yawDelay"),packet,packet,false,false,
                LegacyTileNbtPersistenceAnalyzer.Status.PAIRED_NBT_ONLY);
    }
    private static com.google.gson.JsonObject manifest(Map<String,LegacyTileNbtPersistenceAnalyzer.Audit> nbt){
        var c=candidate();
        return LegacyBlockTileModelPreflightPass.manifest("sha","mod",
                LegacyBlockTileFacingRotationManifestTest.source(),Map.of(),Map.of(),Map.of(),
                Map.of(c.sourceBlockClass(),pivot()),nbt);
    }
    @Test void allSavedFieldsStillDoNotProveWirePacketOrClientRuntime(){
        var c=candidate();var root=manifest(Map.of(c.sourceBlockClass(),evidence(c.tileClass(),true,true)));
        assertEquals(1,root.get("sourceTileNbtPairAuditCandidateCount").getAsInt());
        assertEquals(1,root.get("sourceFullyPersistentVisualStateCandidateCount").getAsInt());
        var row=root.getAsJsonArray("candidates").get(0).getAsJsonObject();
        assertTrue(row.get("sourceTileNbtFieldPairAuditPresent").getAsBoolean());
        assertTrue(row.get("sourceAllVisualFieldsNbtPersistent").getAsBoolean());
        assertEquals(3,row.getAsJsonArray("sourcePairedNbtVisualFields").size());
        assertEquals("currentYaw",row.getAsJsonArray("sourcePairedNbtVisualFields").get(0)
                .getAsJsonObject().get("sourceNbtTagKey").getAsString());
        assertTrue(row.get("sourceDescriptionPacketHookObserved").getAsBoolean());
        assertTrue(row.get("sourceOnDataPacketHookObserved").getAsBoolean());
        assertFalse(row.get("sourceTilePacketPayloadProven").getAsBoolean());
        assertFalse(row.get("sourceTileNbtRuntimeWired").getAsBoolean());
        assertFalse(row.get("runtimeReady").getAsBoolean());
        assertFalse(root.get("tileStateSyncProven").getAsBoolean());
        assertFalse(root.get("runtimeWired").getAsBoolean());
        assertFalse(root.has("rules"));
    }
    @Test void incompleteFieldPersistenceIsVisibleAndNeverCalledFullyPersistent(){
        var c=candidate();var root=manifest(Map.of(c.sourceBlockClass(),evidence(c.tileClass(),false,true)));
        assertEquals(1,root.get("sourceTileNbtPairAuditCandidateCount").getAsInt());
        assertEquals(0,root.get("sourceFullyPersistentVisualStateCandidateCount").getAsInt());
        var row=root.getAsJsonArray("candidates").get(0).getAsJsonObject();
        assertFalse(row.get("sourceAllVisualFieldsNbtPersistent").getAsBoolean());
        assertEquals(2,row.getAsJsonArray("sourceUnpairedNbtVisualFields").size());
        assertFalse(row.get("sourceTilePacketPayloadProven").getAsBoolean());
    }
    @Test void noNbtAuditDoesNotInventPersistence(){
        var row=manifest(Map.of()).getAsJsonArray("candidates").get(0).getAsJsonObject();
        assertFalse(row.get("sourceTileNbtFieldPairAuditPresent").getAsBoolean());
        assertFalse(row.has("sourcePairedNbtVisualFields"));
    }
    @Test void foreignBlockKeyCannotInjectNbtProof(){
        var c=candidate();
        var row=manifest(Map.of("wrong/Block",evidence(c.tileClass(),true,false)))
                .getAsJsonArray("candidates").get(0).getAsJsonObject();
        assertFalse(row.get("sourceTileNbtFieldPairAuditPresent").getAsBoolean());
    }
    @Test void differentTileIdentityCannotInjectNbtProof(){
        var c=candidate();
        var row=manifest(Map.of(c.sourceBlockClass(),evidence("foreign/OtherTile",true,false)))
                .getAsJsonArray("candidates").get(0).getAsJsonObject();
        assertFalse(row.get("sourceTileNbtFieldPairAuditPresent").getAsBoolean());
    }
    @Test void missingVisualFieldPartitionCannotClaimPersistence(){
        var c=candidate();
        var audit=new LegacyTileNbtPersistenceAnalyzer.Audit(c.tileClass(),
                List.of("currentYaw","desiredYaw","yawDelay"),List.of(
                new LegacyTileNbtPersistenceAnalyzer.StoredField(c.tileClass(),"currentYaw","I","yaw","I")),
                List.of("desiredYaw"),false,false,false,false,
                LegacyTileNbtPersistenceAnalyzer.Status.PAIRED_NBT_ONLY);
        var row=manifest(Map.of(c.sourceBlockClass(),audit)).getAsJsonArray("candidates").get(0).getAsJsonObject();
        assertFalse(row.get("sourceTileNbtFieldPairAuditPresent").getAsBoolean());
    }
    @Test void wrongRequiredFieldCollectionCannotClaimNbtProof(){
        var c=candidate();
        var audit=new LegacyTileNbtPersistenceAnalyzer.Audit(c.tileClass(),
                List.of("currentYaw"),List.of(new LegacyTileNbtPersistenceAnalyzer.StoredField(
                c.tileClass(),"currentYaw","I","yaw","I")),List.of(),false,false,false,false,
                LegacyTileNbtPersistenceAnalyzer.Status.PAIRED_NBT_ONLY);
        var row=manifest(Map.of(c.sourceBlockClass(),audit)).getAsJsonArray("candidates").get(0).getAsJsonObject();
        assertFalse(row.get("sourceTileNbtFieldPairAuditPresent").getAsBoolean());
    }
    @Test void duplicatedFieldPairsDoNotCountAsCompleteCoverage(){
        var c=candidate();var item=evidence(c.tileClass(),true,false);
        var bogus=new java.util.ArrayList<>(item.pairedNbtFields());bogus.add(item.pairedNbtFields().getFirst());
        var audit=new LegacyTileNbtPersistenceAnalyzer.Audit(c.tileClass(),item.requiredVisualFields(),
                bogus,List.of(),false,false,false,false,LegacyTileNbtPersistenceAnalyzer.Status.PAIRED_NBT_ONLY);
        var row=manifest(Map.of(c.sourceBlockClass(),audit)).getAsJsonArray("candidates").get(0).getAsJsonObject();
        assertFalse(row.get("sourceTileNbtFieldPairAuditPresent").getAsBoolean());
    }
    @Test void legacyOverloadKeepsOptionalNbtEvidenceAbsent(){
        var c=candidate();
        var root=LegacyBlockTileModelPreflightPass.manifest("sha","mod",
                LegacyBlockTileFacingRotationManifestTest.source(),Map.of(),Map.of(),Map.of(),
                Map.of(c.sourceBlockClass(),pivot()));
        assertEquals(0,root.get("sourceTileNbtPairAuditCandidateCount").getAsInt());
        assertFalse(root.get("tileStateSyncProven").getAsBoolean());
    }
    @Test void auditCannotUsePacketHookPresenceToClaimNetworkSync(){
        var c=candidate();var original=evidence(c.tileClass(),true,true);
        boolean packetRejected=false,clientRejected=false;
        try{new LegacyTileNbtPersistenceAnalyzer.Audit(c.tileClass(),original.requiredVisualFields(),
                original.pairedNbtFields(),original.unpairedVisualFields(),true,true,true,false,original.status());}
        catch(IllegalArgumentException expected){packetRejected=true;}
        try{new LegacyTileNbtPersistenceAnalyzer.Audit(c.tileClass(),original.requiredVisualFields(),
                original.pairedNbtFields(),original.unpairedVisualFields(),true,true,false,true,original.status());}
        catch(IllegalArgumentException expected){clientRejected=true;}
        assertTrue(packetRejected);assertTrue(clientRejected);
    }
    @Test void optionalAuditCannotDowngradeConversionStatus(){
        var diagnostics=new DiagnosticCollector();
        diagnostics.info("LFB-CONVERT-BLOCK-TILE-0010",SupportLevel.AUTO,
                "NBT pair evidence absent");
        diagnostics.warning("LFB-CONVERT-BLOCK-TILE-0011",SupportLevel.AUTO,
                "NBT source unprovable");
        assertEquals(ConversionStatus.CONVERTED,diagnostics.status());
    }
}
