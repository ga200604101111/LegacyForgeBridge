package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** Renamed synthetic Java 1.7 bytecode; no Twilight Forest string is a selector. */
class LegacyTilePivotAnimationAnalyzerTest {
    @TempDir Path folder;
    private LegacyTilePivotAnimationAnalyzer.Analysis scan(LegacyTilePivotAnimationFixture.Form form) throws Exception {
        Path jar=LegacyTilePivotAnimationFixture.jar(folder.resolve(form.name()+".jar"),form);
        return new LegacyTilePivotAnimationAnalyzer().analyze(jar,
                LegacyTilePivotAnimationFixture.TILE,
                LegacyTilePivotAnimationFixture.RENDERER,
                LegacyTilePivotAnimationFixture.MODEL);
    }
    @Test void fourCausalAnimatedPartsAndZeroGuardAreSourceProven() throws Exception {
        var result=scan(LegacyTilePivotAnimationFixture.Form.FIXED_FOUR);
        var p=result.proof().orElseThrow(()->new AssertionError(result.diagnostics()));
        assertEquals(4,p.parts().size());
        assertEquals("yawDelay",p.guardTileField());
        assertEquals("animate",p.modelAnimationMethod());
        assertEquals(Set.of("actualYaw","desiredYaw"),Set.copyOf(p.parts().getFirst().sourceTileFields()));
        for(var part:p.parts()){
            assertEquals(7f,part.restPivotY());
            assertTrue(part.usesPartialTick());
            assertTrue(part.sourceSineClampAndPriorPivot());
        }
        assertFalse(p.clientAnimationRuntimeWired());
        assertFalse(p.packetPayloadProven());
    }
    @Test void proofRecordRejectsPretendNetworkOrRuntime() {
        var p=new LegacyTilePivotAnimationAnalyzer.Part("wing","rotationPointY",7f,
                java.util.List.of("actualYaw"),true,true);
        for(boolean synced:java.util.List.of(false,true)) {
            boolean blocked=false;
            try {new LegacyTilePivotAnimationAnalyzer.Proof("tile","renderer","model","animate",
                    "yawDelay",java.util.List.of(p),synced,!synced);}
            catch(IllegalArgumentException forbidden){blocked=true;}
            assertTrue(blocked,"An unproven source proof must never grant client sync/runtime");
        }
    }
    @Test void mutationSourceTickDoesNotGrantClientPacketSync() throws Exception {
        var p=scan(LegacyTilePivotAnimationFixture.Form.REMOTE_TICK_ONLY).proof().orElseThrow();
        assertFalse(p.packetPayloadProven());
        assertFalse(p.clientAnimationRuntimeWired());
    }
    @Test void renamedSrgPivotFieldIsValid() throws Exception {
        var p=scan(LegacyTilePivotAnimationFixture.Form.SOURCE_SRG_PIVOT).proof().orElseThrow();
        assertEquals(4,p.parts().size());
        assertEquals("field_78797_d",p.parts().getFirst().pivotField());
    }
    @Test void missingModelClassIsRejected() throws Exception {reject(LegacyTilePivotAnimationFixture.Form.MISSING_MODEL);}
    @Test void missingTileClassIsRejected() throws Exception {reject(LegacyTilePivotAnimationFixture.Form.MISSING_TILE);}
    @Test void drawWithoutSourceModelAnimatorIsRejected() throws Exception {reject(LegacyTilePivotAnimationFixture.Form.WRONG_RENDERER_CALL);}
    @Test void twoModelAnimationCallsAreAmbiguous() throws Exception {reject(LegacyTilePivotAnimationFixture.Form.DOUBLE_RENDERER_CALL);}
    @Test void missingTileFieldIsRejected() throws Exception {reject(LegacyTilePivotAnimationFixture.Form.UNKNOWN_TILE_FIELD);}
    @Test void foreignFieldOwnerCannotSupplyTileState() throws Exception {reject(LegacyTilePivotAnimationFixture.Form.FIELD_OWNED_BY_OTHER_CLASS);}
    @Test void staticFieldCannotSupplyTileInstanceState() throws Exception {reject(LegacyTilePivotAnimationFixture.Form.TILE_FIELD_STATIC);}
    @Test void unknownSineFunctionCannotBeAssumedVanillaMath() throws Exception {reject(LegacyTilePivotAnimationFixture.Form.NO_SINE);}
    @Test void missingClampCannotBeAssumedSafe() throws Exception {reject(LegacyTilePivotAnimationFixture.Form.NO_CLAMP);}
    @Test void missingPartialTicksIsNotSameSourceAnimationFamily() throws Exception {reject(LegacyTilePivotAnimationFixture.Form.NO_PARTIAL);}
    @Test void invertedGuardDoesNotAdmitZeroGateFamily() throws Exception {reject(LegacyTilePivotAnimationFixture.Form.INVERTED_GUARD);}
    @Test void guardFromOtherObjectCannotBeAssumedTileField() throws Exception {reject(LegacyTilePivotAnimationFixture.Form.DIFFERENT_GUARD_RECEIVER);}
    @Test void animatorPartMustBeActuallyConstructed() throws Exception {reject(LegacyTilePivotAnimationFixture.Form.PART_FIELD_UNCONSTRUCTED);}
    @Test void writesToAnotherPivotAxisAreNotCovered() throws Exception {reject(LegacyTilePivotAnimationFixture.Form.PIVOT_X_WRITE);}
    @Test void dynamicOffsetMustUseSameModelPart() throws Exception {reject(LegacyTilePivotAnimationFixture.Form.WRONG_PART_RECEIVER);}
    @Test void duplicatedInitialPivotResetsAreRejected() throws Exception {reject(LegacyTilePivotAnimationFixture.Form.DUPLICATE_PIVOT_RESET);}
    @Test void duplicatedDynamicPivotWritesAreRejected() throws Exception {reject(LegacyTilePivotAnimationFixture.Form.EXTRA_DYNAMIC_PIVOT);}
    @Test void unknownStateHelperCannotBeReplayed() throws Exception {reject(LegacyTilePivotAnimationFixture.Form.UNKNOWN_FLOAT_HELPER);}
    @Test void nonlinearExpressionMustDependOnSourceTileFields() throws Exception {reject(LegacyTilePivotAnimationFixture.Form.NO_DYNAMIC_TILE_FIELD);}
    @Test void renderedAnimationMustUseOriginalPartialTickArgument() throws Exception {reject(LegacyTilePivotAnimationFixture.Form.WRONG_PARTIAL_CALL);}
    @Test void extraAnimatorBranchIsOutsideSingleZeroGuardFamily() throws Exception {reject(LegacyTilePivotAnimationFixture.Form.EXTRA_ANIMATOR_BRANCH);}
    @Test void rendererMustUseTheSourceTileArgument() throws Exception {reject(LegacyTilePivotAnimationFixture.Form.WRONG_MODEL_CALL_ARG);}
    @Test void rendererModelMustBeConstructed() throws Exception {reject(LegacyTilePivotAnimationFixture.Form.NO_RENDERER_MODEL_ALLOCATION);}
    @Test void clampNeedsLiteralZeroNotArbitraryBound() throws Exception {reject(LegacyTilePivotAnimationFixture.Form.WRONG_CLAMP_BOUND);}
    @Test void rendererNewButNullAssignmentIsNotModelOwnership() throws Exception {reject(LegacyTilePivotAnimationFixture.Form.UNCONSTRUCTED_RENDERER_MODEL_FIELD);}
    @Test void partFieldAssignedNullIsNotSourceConstruction() throws Exception {reject(LegacyTilePivotAnimationFixture.Form.NULL_PART_FIELD);}
    @Test void sineMustReadSourceTileStateNotJustPartialTime() throws Exception {reject(LegacyTilePivotAnimationFixture.Form.ANIMATOR_SINE_WITH_CONSTANT_INPUT);}
    @Test void extraOffsetAfterTheSineClampIsNotTheProvenOriginalExpression() throws Exception {reject(LegacyTilePivotAnimationFixture.Form.EXTRA_FLOAT_OFFSET);}
    @Test void unrelatedServerSideCallInsideAnimatorCannotBeSilentlyReplayed() throws Exception {reject(LegacyTilePivotAnimationFixture.Form.UNKNOWN_SIDE_EFFECT_CALL);}
    @Test void nonexistentSourceIdentityMustFailClosed() throws Exception {
        Path jar=LegacyTilePivotAnimationFixture.jar(folder.resolve("identity.jar"),
                LegacyTilePivotAnimationFixture.Form.FIXED_FOUR);
        assertTrue(new LegacyTilePivotAnimationAnalyzer().analyze(jar,"nope/tile",LegacyTilePivotAnimationFixture.RENDERER,
                LegacyTilePivotAnimationFixture.MODEL).proof().isEmpty());
    }
    private void reject(LegacyTilePivotAnimationFixture.Form form) throws Exception {
        var result=scan(form);
        assertTrue(result.proof().isEmpty(),form+" incorrectly admitted: "+result.proof());
        assertFalse(result.diagnostics().isEmpty());
    }
}
