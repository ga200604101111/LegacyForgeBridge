package dev.yinghuang.legacyforgebridge.convert.pass;

import dev.yinghuang.legacyforgebridge.convert.LegacyBlockTileModelPreflight;
import dev.yinghuang.legacyforgebridge.convert.LegacyBlockTileVisualStateAnalyzer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Source state is additional audit data, never a legacy server/client runtime license. */
class LegacyBlockTileVisualStateManifestTest {
    private static LegacyBlockTileModelPreflight.Candidate base() {
        return new LegacyBlockTileModelPreflight.Candidate(
                "unnamed", "foreign/block/Light", "foreign/tile/Light", "Light Tile",
                "foreign/client/Tesr", "foreign/client/Model", "foreign/block/Light",
                "foreign/block/Light", true, true, true, true, 14, true);
    }
    private static LegacyBlockTileVisualStateAnalyzer.Evidence observed(boolean packet) {
        return new LegacyBlockTileVisualStateAnalyzer.Evidence(
                "foreign/block/Light","foreign/tile/Light","foreign/client/Tesr", "foreign/client/Model",
                List.of("yaw"),List.of("delay"),List.of("yaw"),List.of("yaw"),
                List.of("rotationPointY"),true,true,2,true,false,packet,
                packet ? LegacyBlockTileVisualStateAnalyzer.TileSyncAssessment.SOURCE_PACKET_HOOK_PRESENT_BUT_PAYLOAD_UNPROVEN
                        : LegacyBlockTileVisualStateAnalyzer.TileSyncAssessment.RENDER_STATE_SYNC_UNPROVEN,
                false,false,false);
    }
    @Test void sourceStateArraysAreRecordedButNoExecutableBlockRuleIsEmitted() {
        var source=new LegacyBlockTileModelPreflight.Analysis(List.of(base()),List.of(),List.of());
        var root=LegacyBlockTileModelPreflightPass.manifest("sha","unrelated",source,
                Map.of(base().sourceBlockClass(),observed(false)));
        assertEquals(1,root.get("visualStateAuditCandidateCount").getAsInt());
        assertEquals(1,root.get("tileTickVisualDependencyCandidateCount").getAsInt());
        var row=root.getAsJsonArray("candidates").get(0).getAsJsonObject();
        assertTrue(row.get("sourceVisualStateAuditPresent").getAsBoolean());
        assertEquals("yaw",row.getAsJsonArray("rendererTileFieldsReadObserved").get(0).getAsString());
        assertEquals("delay",row.getAsJsonArray("modelAnimationTileFieldsReadObserved").get(0).getAsString());
        assertEquals("rotationPointY",row.getAsJsonArray("modelPivotFieldsWrittenObserved").get(0).getAsString());
        assertEquals(2,row.get("sourceGlRotateCallsObserved").getAsInt());
        assertEquals("RENDER_STATE_SYNC_UNPROVEN",row.get("sourceTileSyncAssessment").getAsString());
        assertFalse(row.get("sourceTileStateSyncProven").getAsBoolean());
        assertFalse(row.get("sourceFacingMapProven").getAsBoolean());
        assertFalse(row.get("sourceAnimationRuntimeWired").getAsBoolean());
        assertFalse(row.get("runtimeReady").getAsBoolean());
        assertFalse(root.get("runtimeWired").getAsBoolean());
        assertFalse(root.has("rules"));
    }
    @Test void packetHookNeverEnablesSynchronizationOrAnimationRuntime() {
        var root=LegacyBlockTileModelPreflightPass.manifest("sha","unrelated",
                new LegacyBlockTileModelPreflight.Analysis(List.of(base()),List.of(),List.of()),
                Map.of(base().sourceBlockClass(),observed(true)));
        var row=root.getAsJsonArray("candidates").get(0).getAsJsonObject();
        assertTrue(row.get("sourceTilePacketHookPresent").getAsBoolean());
        assertFalse(row.get("sourceTileStateSyncProven").getAsBoolean());
        assertEquals("SOURCE_PACKET_HOOK_PRESENT_BUT_PAYLOAD_UNPROVEN",
                row.get("sourceTileSyncAssessment").getAsString());
        assertFalse(root.get("tileStateSyncProven").getAsBoolean());
    }
    @Test void unrelatedIdentityMustNotInjectRendererOrTileMetadata() {
        var irrelevant=new LegacyBlockTileVisualStateAnalyzer.Evidence(
                "foreign/block/Light","other/tile", "foreign/client/Tesr", "foreign/client/Model",
                List.of("secret"),List.of(),List.of(),List.of(),List.of(),
                false,false,0,false,false,false,
                LegacyBlockTileVisualStateAnalyzer.TileSyncAssessment.RENDER_STATE_SYNC_UNPROVEN,
                false,false,false);
        var root=LegacyBlockTileModelPreflightPass.manifest("sha","unrelated",
                new LegacyBlockTileModelPreflight.Analysis(List.of(base()),List.of(),List.of()),
                Map.of(base().sourceBlockClass(),irrelevant));
        var row=root.getAsJsonArray("candidates").get(0).getAsJsonObject();
        assertFalse(row.get("sourceVisualStateAuditPresent").getAsBoolean());
        assertFalse(row.has("rendererTileFieldsReadObserved"));
        assertEquals(0,root.get("visualStateAuditCandidateCount").getAsInt());
    }
    @Test void legacyManifestOverloadRemainsAValidNonExecutableSchema() {
        var root=LegacyBlockTileModelPreflightPass.manifest("sha","unrelated",
                new LegacyBlockTileModelPreflight.Analysis(List.of(base()),List.of(),List.of()));
        assertFalse(root.getAsJsonArray("candidates").get(0).getAsJsonObject()
                .get("sourceVisualStateAuditPresent").getAsBoolean());
        assertFalse(root.get("animationSemanticsProven").getAsBoolean());
    }
}
