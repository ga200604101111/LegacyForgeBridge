package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** No mod-specific names/IDs; synthetic renamed TESR source code only. */
class LegacyTileFacingRotationAnalyzerTest {
    @TempDir Path tempDir;

    private LegacyTileFacingRotationAnalyzer.Analysis inspect(LegacyTileFacingRotationFixture.Form form)
            throws Exception {
        var jar=LegacyTileFacingRotationFixture.jar(tempDir.resolve(form.name()+".jar"),form);
        return new LegacyTileFacingRotationAnalyzer().analyze(jar,
                LegacyTileFacingRotationFixture.TILE,LegacyTileFacingRotationFixture.RENDERER);
    }
    private LegacyTileFacingRotationAnalyzer.Proof proof(LegacyTileFacingRotationFixture.Form form)
            throws Exception {
        var a=inspect(form);
        return a.proof().orElseThrow(()->new AssertionError(a.diagnostics()));
    }
    private void blocked(LegacyTileFacingRotationFixture.Form form) throws Exception {
        var a=inspect(form);
        assertTrue(a.proof().isEmpty(),form.name()+" unexpectedly source-proven");
        assertFalse(a.diagnostics().isEmpty());
    }
    @Test void provesSixDistinctSourceFacingAnglesFromBoundedBytecodeBranches() throws Exception {
        var p=proof(LegacyTileFacingRotationFixture.Form.SIX_FACING);
        assertEquals(16,p.facing0to15().size());
        var poses=p.facing0to15();
        assertEquals(new LegacyTileFacingRotationAnalyzer.Facing(0,90,0),poses.get(0));
        assertEquals(new LegacyTileFacingRotationAnalyzer.Facing(1,90,-90),poses.get(1));
        assertEquals(new LegacyTileFacingRotationAnalyzer.Facing(2,90,90),poses.get(2));
        assertEquals(new LegacyTileFacingRotationAnalyzer.Facing(3,90,0),poses.get(3));
        assertEquals(new LegacyTileFacingRotationAnalyzer.Facing(4,90,180),poses.get(4));
        assertEquals(new LegacyTileFacingRotationAnalyzer.Facing(5,0,0),poses.get(5));
        assertEquals(new LegacyTileFacingRotationAnalyzer.Facing(6,180,0),poses.get(6));
        assertEquals(new LegacyTileFacingRotationAnalyzer.Facing(9,90,0),poses.get(9));
        assertEquals(-1,p.sourceMetadataMask());
        assertFalse(p.runtimeWired());
        assertTrue(p.additionalSourceGlRotationPresent());
    }
    @Test void explicitMetadataMaskSevenPreservesHighBitOrientations() throws Exception {
        var p=proof(LegacyTileFacingRotationFixture.Form.MASK7);
        assertEquals(7,p.sourceMetadataMask());
        assertEquals(p.facing0to15().get(1).rotationZDegrees(),p.facing0to15().get(9).rotationZDegrees());
        assertEquals(p.facing0to15().get(6).rotationXDegrees(),p.facing0to15().get(14).rotationXDegrees());
    }
    @Test void srgTileMetadataGetterIsRecognizedWithoutAnyModWhitelist() throws Exception {
        assertEquals(90F,proof(LegacyTileFacingRotationFixture.Form.SRG).facing0to15().get(3).rotationXDegrees());
    }
    @Test void missingTypedDelegationCastIsNotAProvenSourceTile() throws Exception {
        blocked(LegacyTileFacingRotationFixture.Form.MISSING_CAST);
    }
    @Test void multipleSourceHelpersMustNotBeMerged() throws Exception {
        blocked(LegacyTileFacingRotationFixture.Form.TWO_HELPERS);
    }
    @Test void metadataWasNeverReadFromTile() throws Exception {
        blocked(LegacyTileFacingRotationFixture.Form.NO_META);
    }
    @Test void metadataGetterOnWrongReceiverIsNotSourceOwned() throws Exception {
        blocked(LegacyTileFacingRotationFixture.Form.WRONG_METADATA_RECEIVER);
    }
    @Test void dynamicRendererAxisCannotBecomeFixedFacingProof() throws Exception {
        blocked(LegacyTileFacingRotationFixture.Form.DYNAMIC_AXIS);
    }
    @Test void tickDrivenAngleOverwriteCannotBeAssumedFixed() throws Exception {
        blocked(LegacyTileFacingRotationFixture.Form.DYNAMIC_ANGLE);
    }
    @Test void oneRotationAndUnrelatedYawCannotMakeTwoFaceTransforms() throws Exception {
        blocked(LegacyTileFacingRotationFixture.Form.NO_Z_ROTATION);
    }
    @Test void unexpectedOpenGlSideEffectBeforeFaceRotationsFailsClosed() throws Exception {
        blocked(LegacyTileFacingRotationFixture.Form.UNEXPECTED_GL_CALL);
    }
    @Test void unrecognizedControlFlowExpressionMustBeRejected() throws Exception {
        blocked(LegacyTileFacingRotationFixture.Form.UNKNOWN_COMPARISON);
    }
    @Test void sourceMetadataLocalOverwriteIsNotHarmless() throws Exception {
        blocked(LegacyTileFacingRotationFixture.Form.CHANGED_METADATA);
    }
    @Test void metadataReadingDoesNotImplyOrientationDependence() throws Exception {
        blocked(LegacyTileFacingRotationFixture.Form.NO_POSE_VARIATION);
    }
    @Test void rotationAfterFirstGlCallInvalidatesUnconditionalMap() throws Exception {
        blocked(LegacyTileFacingRotationFixture.Form.CHANGED_ANGLE_AFTER_X);
    }
    @Test void arbitraryNonTileSourceDoesNotSatisfyTileProof() throws Exception {
        blocked(LegacyTileFacingRotationFixture.Form.BAD_TILE_PARENT);
    }
    @Test void unknownSourceCallInFacingPreludeFailsClosed() throws Exception {
        blocked(LegacyTileFacingRotationFixture.Form.WORLD_CALL_IN_PROLOGUE);
    }
    @Test void duplicateGetBlockMetadataCannotBeSilentlyAssumedSingle() throws Exception {
        blocked(LegacyTileFacingRotationFixture.Form.MULTIPLE_METADATA_CALLS);
    }
    @Test void badGlInvokeOpcodeCannotStandInForActualSourceRotation() throws Exception {
        blocked(LegacyTileFacingRotationFixture.Form.BAD_ROTATE_OPCODE);
    }
    @Test void tileHierarchyMayBeInheritedThroughSourceOwnedClasses() throws Exception {
        assertEquals(-90F,proof(LegacyTileFacingRotationFixture.Form.SOURCE_TILE_SUPERCLASS)
                .facing0to15().get(1).rotationZDegrees());
    }
    @Test void unresolvableSuperclassCannotClaimTileAncestry() throws Exception {
        blocked(LegacyTileFacingRotationFixture.Form.MISSING_TILE_SUPERCLASS);
    }
    @Test void delegateWrapperCannotRotateBeforeTheProvenFaceMap() throws Exception {
        blocked(LegacyTileFacingRotationFixture.Form.ENTRY_SIDE_EFFECT);
    }
    @Test void sourceFacingMetadataLocalMutationCannotPass() throws Exception {
        blocked(LegacyTileFacingRotationFixture.Form.IINC_METADATA);
    }
    @Test void nonTesrSourceClassCannotClaimTesrFixedFacing() throws Exception {
        blocked(LegacyTileFacingRotationFixture.Form.BAD_RENDERER_PARENT);
    }
}
