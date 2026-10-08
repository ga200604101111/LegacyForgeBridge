package dev.yinghuang.legacyforgebridge.convert.pass;

import dev.yinghuang.legacyforgebridge.convert.LegacyBlockTileModelPreflight;
import dev.yinghuang.legacyforgebridge.convert.LegacyTileFacingRotationAnalyzer;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Static per-metadata orientation data is source evidence, not a TileEntity runtime bridge. */
class LegacyBlockTileFacingRotationManifestTest {
    static LegacyBlockTileModelPreflight.Candidate candidate() {
        return new LegacyBlockTileModelPreflight.Candidate(
                "renamed_lamp","foreign/block/Lamp","foreign/tile/Lamp","Renamed Lamp",
                "foreign/client/Tesr","foreign/client/Model","foreign/block/Lamp",
                "foreign/block/Lamp",true,true,true,true,14,true);
    }
    static LegacyTileFacingRotationAnalyzer.Proof proof(String tile, String renderer) {
        List<LegacyTileFacingRotationAnalyzer.Facing> facing=new ArrayList<>();
        for(int i=0;i<16;i++)facing.add(new LegacyTileFacingRotationAnalyzer.Facing(
                i,i==5?0:i==6?180:90,i==1?-90:i==2?90:i==4?180:0));
        return new LegacyTileFacingRotationAnalyzer.Proof(tile,renderer,
                "sourceDraw","(L"+tile+";DDDF)V",-1,facing,true,false);
    }
    static LegacyBlockTileModelPreflight.Analysis source() {
        return new LegacyBlockTileModelPreflight.Analysis(List.of(candidate()),List.of(),List.of());
    }
    @Test void sourceRotationsAreStoredFor16MetadataButRuntimeIsNotEnabled() {
        var map=Map.of(candidate().sourceBlockClass(),proof(candidate().tileClass(),candidate().rendererClass()));
        var json=LegacyBlockTileModelPreflightPass.manifest("sha","foreign",source(),Map.of(),map);
        assertEquals(1,json.get("sourceStaticFacingMapCandidateCount").getAsInt());
        var item=json.getAsJsonArray("candidates").get(0).getAsJsonObject();
        assertTrue(item.get("sourceFacingMapProven").getAsBoolean());
        assertEquals(-1,item.get("sourceFacingMetadataMask").getAsInt());
        var states=item.getAsJsonArray("sourceFacingMap0to15");
        assertEquals(16,states.size());
        assertEquals(5,states.get(5).getAsJsonObject().get("legacyMetadata").getAsInt());
        assertEquals(0,states.get(5).getAsJsonObject().get("sourceRotationXDegrees").getAsInt());
        assertEquals(-90,states.get(1).getAsJsonObject().get("sourceRotationZDegrees").getAsInt());
        assertTrue(item.get("sourceAdditionalGlRotationPresent").getAsBoolean());
        assertFalse(item.get("sourceFacingRendererRuntimeWired").getAsBoolean());
        assertFalse(item.get("runtimeReady").getAsBoolean());
        assertFalse(json.get("runtimeWired").getAsBoolean());
        assertFalse(json.get("tileStateSyncProven").getAsBoolean());
        assertFalse(json.has("rules"));
    }
    @Test void absentSourceRotationProofKeepsOldManifestFalse() {
        var json=LegacyBlockTileModelPreflightPass.manifest("sha","foreign",source());
        assertEquals(0,json.get("sourceStaticFacingMapCandidateCount").getAsInt());
        var row=json.getAsJsonArray("candidates").get(0).getAsJsonObject();
        assertFalse(row.get("sourceFacingMapProven").getAsBoolean());
        assertFalse(row.has("sourceFacingMap0to15"));
        assertFalse(row.get("runtimeReady").getAsBoolean());
    }
    @Test void anotherTileIdentityCannotInjectUnrelatedFacingData() {
        var foreign=proof("foreign/tile/OtherTile",candidate().rendererClass());
        var json=LegacyBlockTileModelPreflightPass.manifest("sha","foreign",source(),Map.of(),
                Map.of(candidate().sourceBlockClass(),foreign));
        assertEquals(0,json.get("sourceStaticFacingMapCandidateCount").getAsInt());
        assertFalse(json.getAsJsonArray("candidates").get(0).getAsJsonObject()
                .get("sourceFacingMapProven").getAsBoolean());
    }
    @Test void anotherRendererIdentityCannotInjectUnrelatedFacingData() {
        var foreign=proof(candidate().tileClass(),"foreign/client/OtherTESR");
        var json=LegacyBlockTileModelPreflightPass.manifest("sha","foreign",source(),Map.of(),
                Map.of(candidate().sourceBlockClass(),foreign));
        assertEquals(0,json.get("sourceStaticFacingMapCandidateCount").getAsInt());
        assertFalse(json.getAsJsonArray("candidates").get(0).getAsJsonObject()
                .get("sourceFacingMapProven").getAsBoolean());
    }
    @Test void missingOrOutOfOrderMetadataMapIsRejectedAtProofConstruction() {
        assertThrows(IllegalArgumentException.class,()->new LegacyTileFacingRotationAnalyzer.Proof(
                "tile","renderer","draw","(Ltile;DDDF)V",-1,List.of(),false,false));
        List<LegacyTileFacingRotationAnalyzer.Facing> wrong=new ArrayList<>(proof("tile","renderer").facing0to15());
        wrong.set(0,new LegacyTileFacingRotationAnalyzer.Facing(7,90,0));
        assertThrows(IllegalArgumentException.class,()->new LegacyTileFacingRotationAnalyzer.Proof(
                "tile","renderer","draw","(Ltile;DDDF)V",-1,wrong,false,false));
    }
    @Test void sourceFacingProofCannotClaimExecutableClientRenderer() {
        var poses=proof("tile","renderer").facing0to15();
        assertThrows(IllegalArgumentException.class,()->new LegacyTileFacingRotationAnalyzer.Proof(
                "tile","renderer","draw","(Ltile;DDDF)V",-1,poses,false,true));
    }
}
