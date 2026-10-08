package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Renamed Java 7 source fixtures prove only a dynamic TESR angle operand, never sync. */
class LegacyTileDynamicYawAnalyzerTest {
    @TempDir Path tempDir;
    private LegacyTileDynamicYawAnalyzer.Analysis inspect(LegacyTileDynamicYawFixture.Form form)throws Exception {
        Path jar=LegacyTileDynamicYawFixture.jar(tempDir.resolve(form.name()+".jar"),form);
        var face=new LegacyTileFacingRotationAnalyzer().analyze(jar,
                LegacyTileDynamicYawFixture.TILE,LegacyTileDynamicYawFixture.RENDER).proof();
        if(face.isEmpty())return new LegacyTileDynamicYawAnalyzer.Analysis(java.util.Optional.empty(),
                List.of("Facing source absent"));
        return new LegacyTileDynamicYawAnalyzer().analyze(jar,
                LegacyTileDynamicYawFixture.TILE,LegacyTileDynamicYawFixture.RENDER,face.get());
    }
    private LegacyTileDynamicYawAnalyzer.Proof proven(LegacyTileDynamicYawFixture.Form form)throws Exception {
        var r=inspect(form);return r.proof().orElseThrow(()->new AssertionError(r.diagnostics()));
    }
    private void rejected(LegacyTileDynamicYawFixture.Form form)throws Exception {
        var r=inspect(form);
        assertTrue(r.proof().isEmpty(),form+" must not have executable/source yaw proof");
    }
    @Test void directSourceIntYawIsExactThirdGlYAngle()throws Exception {
        var p=proven(LegacyTileDynamicYawFixture.Form.EXACT_INT);
        assertEquals("currentYaw",p.sourceFieldName());
        assertEquals(LegacyTileDynamicYawFixture.TILE,p.sourceFieldOwner());
        assertEquals("I",p.sourceFieldDescriptor());
        assertEquals(LegacyTileDynamicYawAnalyzer.Operand.INT_FIELD_TO_FLOAT,p.operand());
        assertTrue(p.unconditionalSourceRotation());
        assertFalse(p.tileFieldSyncProven());
        assertFalse(p.runtimeWired());
    }
    @Test void directSourceFloatFieldCanBeProven()throws Exception {
        var p=proven(LegacyTileDynamicYawFixture.Form.FLOAT_FIELD);
        assertEquals("F",p.sourceFieldDescriptor());
        assertEquals(LegacyTileDynamicYawAnalyzer.Operand.FLOAT_FIELD,p.operand());
    }
    @Test void exactImmediateFstoreFloadAliasIsStillBoundToSourceField()throws Exception {
        assertEquals("currentYaw",proven(LegacyTileDynamicYawFixture.Form.LOCAL_ALIAS).sourceFieldName());
    }
    @Test void sourceTypedCastIsAcceptedOnlyForExactTileType()throws Exception {
        proven(LegacyTileDynamicYawFixture.Form.SOURCE_CAST);
        rejected(LegacyTileDynamicYawFixture.Form.BAD_EXTRA_CAST);
    }
    @Test void differentLegalSourceFieldNameDoesNotRequireAWhitelist()throws Exception {
        assertEquals("anotherField",proven(LegacyTileDynamicYawFixture.Form.RENAMED_SOURCE_FIELD).sourceFieldName());
    }
    @Test void srgMetadataMethodAndSourceYawStillJoin()throws Exception {
        proven(LegacyTileDynamicYawFixture.Form.LEGACY_SRG);
    }
    @Test void dynamicAngleDoesNotPermitOtherGlAxes()throws Exception {
        rejected(LegacyTileDynamicYawFixture.Form.WRONG_Y_AXIS);
    }
    @Test void arithmeticModificationIsNotOneDirectSourceField()throws Exception {
        rejected(LegacyTileDynamicYawFixture.Form.ARITHMETIC_ANGLE);
        rejected(LegacyTileDynamicYawFixture.Form.NEGATIVE_ANGLE);
    }
    @Test void unrelatedFieldOwnerCannotClaimSourceTile()throws Exception {
        rejected(LegacyTileDynamicYawFixture.Form.WRONG_FIELD_OWNER);
    }
    @Test void sourceRenderParameterMustBeReceiver()throws Exception {
        rejected(LegacyTileDynamicYawFixture.Form.WRONG_RECEIVER);
    }
    @Test void longFieldCannotBeConflatedWithIntYaw()throws Exception {
        rejected(LegacyTileDynamicYawFixture.Form.WRONG_FIELD_TYPE);
    }
    @Test void missingDeclarationDoesNotAllowGetfield()throws Exception {
        rejected(LegacyTileDynamicYawFixture.Form.UNDECLARED_FIELD);
    }
    @Test void staticFieldsDoNotRepresentTileInstanceYaw()throws Exception {
        rejected(LegacyTileDynamicYawFixture.Form.STATIC_FIELD);
    }
    @Test void conditionalGlYawIsNotUnconditionalTransform()throws Exception {
        rejected(LegacyTileDynamicYawFixture.Form.CONDITIONAL_RENDER);
    }
    @Test void fourthGlRotateIsOutsideSupportedFixedFacingPlusYawFamily()throws Exception {
        rejected(LegacyTileDynamicYawFixture.Form.EXTRA_SOURCE_ROTATION);
        rejected(LegacyTileDynamicYawFixture.Form.TWO_YAW_ROTATIONS);
    }
    @Test void arbitraryInvokeOpcodeCannotProveGlCall()throws Exception {
        rejected(LegacyTileDynamicYawFixture.Form.WRONG_INVOKE_OPCODE);
    }
    @Test void noYRotationCannotBeInventedFromStaticMetadata()throws Exception {
        rejected(LegacyTileDynamicYawFixture.Form.NO_DYNAMIC_YAW);
    }
    @Test void staleOrForgedFacingProofIsBlocked()throws Exception {
        Path jar=LegacyTileDynamicYawFixture.jar(tempDir.resolve("safe.jar"),LegacyTileDynamicYawFixture.Form.EXACT_INT);
        var actual=new LegacyTileFacingRotationAnalyzer().analyze(jar,
                LegacyTileDynamicYawFixture.TILE,LegacyTileDynamicYawFixture.RENDER).proof().orElseThrow();
        var forged=new LegacyTileFacingRotationAnalyzer.Proof(actual.sourceTileClass(),
                actual.sourceRendererClass(),actual.drawMethod(),actual.drawDescriptor(),
                7,actual.facing0to15(),actual.additionalSourceGlRotationPresent(),false);
        var result=new LegacyTileDynamicYawAnalyzer().analyze(jar,
                LegacyTileDynamicYawFixture.TILE,LegacyTileDynamicYawFixture.RENDER,forged);
        assertTrue(result.proof().isEmpty());
    }
    @Test void mismatchedSourceIdentityIsBlocked()throws Exception {
        Path jar=LegacyTileDynamicYawFixture.jar(tempDir.resolve("other.jar"),LegacyTileDynamicYawFixture.Form.EXACT_INT);
        var actual=new LegacyTileFacingRotationAnalyzer().analyze(jar,
                LegacyTileDynamicYawFixture.TILE,LegacyTileDynamicYawFixture.RENDER).proof().orElseThrow();
        assertTrue(new LegacyTileDynamicYawAnalyzer().analyze(jar,
                LegacyTileDynamicYawFixture.TILE,"foreign/renderer/Other",actual).proof().isEmpty());
    }
}
