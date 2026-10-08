package dev.yinghuang.legacyforgebridge.convert.pass;

import dev.yinghuang.legacyforgebridge.convert.LegacyBlockTileModelPreflight;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Guards against claiming a source-only block/tile renderer as a playable block. */
class LegacyBlockTileModelPreflightPassTest {
    @Test void modelTileLinkNeverCreatesExecutableRuntimeRule() {
        var block=new LegacyBlockTileModelPreflight.Candidate(
                "renamed_wall_light", "renamed/block/Visual", "renamed/tile/VisualState",
                "Renamed Display", "renamed/client/RenderState", "renamed/client/VisualModel",
                "renamed/block/BaseVisual", "renamed/block/Visual",
                true,true,true,true,14,true);
        var source=new LegacyBlockTileModelPreflight.Analysis(
                List.of(block),List.of(),List.of());
        var manifest=LegacyBlockTileModelPreflightPass.manifest("sha-fixture", "renamed", source);
        assertEquals(1,manifest.get("schemaVersion").getAsInt());
        assertEquals("sha-fixture",manifest.get("sourceSha256").getAsString());
        assertTrue(manifest.get("sourceOnly").getAsBoolean());
        assertFalse(manifest.get("runtimeWired").getAsBoolean());
        assertFalse(manifest.get("blockEntityRuntimeWired").getAsBoolean());
        assertFalse(manifest.get("animationSemanticsProven").getAsBoolean());
        assertFalse(manifest.get("tileStateSyncProven").getAsBoolean());
        assertFalse(manifest.has("rules"));
        assertEquals(1,manifest.get("sourceCandidates").getAsInt());
        var row=manifest.getAsJsonArray("candidates").get(0).getAsJsonObject();
        assertEquals("renamed_wall_light",row.get("legacyBlockRegistryName").getAsString());
        assertEquals("renamed/block/BaseVisual",row.get("hasTileEntityDeclaredBy").getAsString());
        assertEquals(14,row.get("sourceConstantLight").getAsInt());
        assertTrue(row.get("sourceQuantityDroppedZeroProven").getAsBoolean());
        assertTrue(row.get("rendererCallsTileModelMethod").getAsBoolean());
        assertFalse(row.get("runtimeReady").getAsBoolean());
    }

    @Test void missingLightEvidenceIsOmittedRatherThanInvented() {
        var block=new LegacyBlockTileModelPreflight.Candidate(
                "unknown", "block/Unknown", "tile/Unknown", "Unknown",
                "renderer/Unknown", "model/Unknown", "block/Unknown", "block/Unknown",
                false,false,false,false,null,false);
        var source=new LegacyBlockTileModelPreflight.Analysis(List.of(block),List.of(),List.of());
        var row=LegacyBlockTileModelPreflightPass.manifest("sha","unknown",source)
                .getAsJsonArray("candidates").get(0).getAsJsonObject();
        assertFalse(row.has("sourceConstantLight"));
        assertFalse(row.get("runtimeReady").getAsBoolean());
    }
}
