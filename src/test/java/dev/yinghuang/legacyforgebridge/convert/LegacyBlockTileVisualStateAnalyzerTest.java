package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Source-only tests using arbitrary class names; no mod dispatch or original mod JAR. */
class LegacyBlockTileVisualStateAnalyzerTest {
    @TempDir Path tempDir;
    private LegacyBlockTileVisualStateAnalyzer.Analysis inspect(LegacyBlockTileVisualStateFixture.Form mode)
            throws Exception {
        Path jar=LegacyBlockTileVisualStateFixture.jar(tempDir.resolve(mode.name()+".jar"),mode);
        return new LegacyBlockTileVisualStateAnalyzer().analyze(
                jar,List.of(LegacyBlockTileVisualStateFixture.candidate()));
    }
    private LegacyBlockTileVisualStateAnalyzer.Evidence proof(LegacyBlockTileVisualStateFixture.Form mode)
            throws Exception {
        var analysis=inspect(mode);
        assertTrue(analysis.byBlockSourceClass().containsKey(LegacyBlockTileVisualStateFixture.BLOCK));
        return analysis.byBlockSourceClass().get(LegacyBlockTileVisualStateFixture.BLOCK);
    }
    @Test void observesTickDrivenStateAndReachableModelPivotAnimation() throws Exception {
        var source=proof(LegacyBlockTileVisualStateFixture.Form.FULL);
        assertEquals(List.of("yaw"),source.rendererTileFieldsRead());
        assertEquals(List.of("delay"),source.modelAnimationTileFieldsRead());
        assertEquals(List.of("yaw"),source.tileTickFieldsWritten());
        assertEquals(List.of("yaw"),source.tickDrivenRenderFields());
        assertEquals(List.of("rotationPointY"),source.modelPivotFieldsWritten());
        assertTrue(source.modelAnimationCallObserved());
        assertFalse(source.tileStateSyncProven());
        assertFalse(source.animationRuntimeWired());
    }
    @Test void metadataAndGLRotationOnlyEstablishSourceObservations() throws Exception {
        var p=proof(LegacyBlockTileVisualStateFixture.Form.FULL);
        assertTrue(p.tileMetadataLookupObserved());
        assertEquals(1,p.sourceGlRotateCalls());
        assertTrue(p.negativeScaleObserved());
        assertFalse(p.rotationMapProven());
    }
    @Test void missingTileTickMeansVisualFieldsAreNotTickDrivenBySource() throws Exception {
        var p=proof(LegacyBlockTileVisualStateFixture.Form.NO_TICK);
        assertTrue(p.tileTickFieldsWritten().isEmpty());
        assertTrue(p.tickDrivenRenderFields().isEmpty());
        assertFalse(p.tileStateSyncProven());
    }
    @Test void unrelatedTickFieldMustNotBeConflatedWithRendererYaw() throws Exception {
        var p=proof(LegacyBlockTileVisualStateFixture.Form.TICK_WRITES_OTHER);
        assertEquals(List.of("other"),p.tileTickFieldsWritten());
        assertTrue(p.tickDrivenRenderFields().isEmpty());
    }
    @Test void unreachableModelMutationDoesNotCountAsRenderedAnimation() throws Exception {
        var p=proof(LegacyBlockTileVisualStateFixture.Form.DRAW_WITHOUT_ANIMATION);
        assertFalse(p.modelAnimationCallObserved());
        assertTrue(p.modelAnimationTileFieldsRead().isEmpty());
        assertTrue(p.modelPivotFieldsWritten().isEmpty());
    }
    @Test void presentModelAnimationMethodDoesNotMeanItWasCalled() throws Exception {
        var p=proof(LegacyBlockTileVisualStateFixture.Form.UNCALLED_MODEL_PIVOT);
        assertFalse(p.modelAnimationCallObserved());
        assertTrue(p.modelPivotFieldsWritten().isEmpty());
    }
    @Test void emptyModelAnimationHasNoPivotOrTileFieldDependencies() throws Exception {
        var p=proof(LegacyBlockTileVisualStateFixture.Form.UNREACHABLE_ANIMATION);
        assertTrue(p.modelAnimationCallObserved());
        assertTrue(p.modelPivotFieldsWritten().isEmpty());
        assertTrue(p.modelAnimationTileFieldsRead().isEmpty());
    }
    @Test void aPacketHookIsNotProofOfNetworkDelivery() throws Exception {
        var p=proof(LegacyBlockTileVisualStateFixture.Form.PACKET_HOOK);
        assertTrue(p.sourceTilePacketHookPresent());
        assertEquals(LegacyBlockTileVisualStateAnalyzer.TileSyncAssessment
                .SOURCE_PACKET_HOOK_PRESENT_BUT_PAYLOAD_UNPROVEN,p.tileSyncAssessment());
        assertFalse(p.tileStateSyncProven());
    }
    @Test void nbtSaveAndLoadAreNotAClientSyncContract() throws Exception {
        var p=proof(LegacyBlockTileVisualStateFixture.Form.NBT_ONLY);
        assertTrue(p.sourceNbtHooksPresent());
        assertFalse(p.sourceTilePacketHookPresent());
        assertEquals(LegacyBlockTileVisualStateAnalyzer.TileSyncAssessment.RENDER_STATE_SYNC_UNPROVEN,
                p.tileSyncAssessment());
    }
    @Test void noBlockMetadataOrGLUsageDoesNotInventSixFaceOrientation() throws Exception {
        var p=proof(LegacyBlockTileVisualStateFixture.Form.NO_METADATA_OR_GL);
        assertFalse(p.tileMetadataLookupObserved());
        assertEquals(0,p.sourceGlRotateCalls());
        assertFalse(p.negativeScaleObserved());
    }
    @Test void noReadableSourceTileFieldsIsNotSyncProven() throws Exception {
        var p=proof(LegacyBlockTileVisualStateFixture.Form.NO_TILE_FIELD_READS);
        assertEquals(List.of("delay"),p.modelAnimationTileFieldsRead());
        assertEquals(List.of(),p.rendererTileFieldsRead());
        assertFalse(p.tileStateSyncProven());
    }
    @Test void bogusFieldOwnerOrNameDoesNotCreateSourceOwnedRead() throws Exception {
        var p=proof(LegacyBlockTileVisualStateFixture.Form.UNDECLARED_TILE_FIELD);
        assertTrue(p.rendererTileFieldsRead().isEmpty());
        assertEquals(List.of("delay"),p.modelAnimationTileFieldsRead());
    }
    @Test void absentRendererDrawCannotBeAudited() throws Exception {
        var a=inspect(LegacyBlockTileVisualStateFixture.Form.NO_DRAW);
        assertTrue(a.byBlockSourceClass().isEmpty());
        assertFalse(a.diagnostics().isEmpty());
    }
    @Test void wrongModelOrBlockClassCannotBeInvented() throws Exception {
        var original=LegacyBlockTileVisualStateFixture.candidate();
        var wrong=new LegacyBlockTileModelPreflight.Candidate(
                original.registryName(),"other/AbsentBlock",original.tileClass(),original.tileRegistryId(),
                original.rendererClass(),original.modelClass(),original.hasTileEntityOwner(),
                original.createTileEntityOwner(),true,true,true,true,14,true);
        var jar=LegacyBlockTileVisualStateFixture.jar(tempDir.resolve("missing.jar"),
                LegacyBlockTileVisualStateFixture.Form.FULL);
        var a=new LegacyBlockTileVisualStateAnalyzer().analyze(jar,List.of(wrong));
        assertTrue(a.byBlockSourceClass().isEmpty());
    }
    @Test void repeatedSourceBlockCandidateIsDiagnosedRatherThanSilentlyDoubled() throws Exception {
        var jar=LegacyBlockTileVisualStateFixture.jar(tempDir.resolve("dup.jar"),
                LegacyBlockTileVisualStateFixture.Form.FULL);
        var c=LegacyBlockTileVisualStateFixture.candidate();
        var a=new LegacyBlockTileVisualStateAnalyzer().analyze(jar,List.of(c,c));
        assertEquals(1,a.byBlockSourceClass().size());
        assertFalse(a.diagnostics().isEmpty());
    }
    @Test void unboundedSourceCandidatesFailClosed() throws Exception {
        var jar=LegacyBlockTileVisualStateFixture.jar(tempDir.resolve("max.jar"),
                LegacyBlockTileVisualStateFixture.Form.FULL);
        var list=new ArrayList<LegacyBlockTileModelPreflight.Candidate>();
        for(int i=0;i<513;i++)list.add(LegacyBlockTileVisualStateFixture.candidate());
        assertThrows(IllegalArgumentException.class, () ->
                new LegacyBlockTileVisualStateAnalyzer().analyze(jar,list));
    }
}
