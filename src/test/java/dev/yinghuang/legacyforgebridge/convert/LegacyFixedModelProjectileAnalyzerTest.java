package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Source-owned ASM fixtures: no external mod names or original mod JAR are special-cased. */
class LegacyFixedModelProjectileAnalyzerTest {
    @TempDir Path tempDir;

    @Test
    void closedFixedRendererRetainsCuboidUvsPivotsTextureAndAxis() throws Exception {
        Path jar = LegacyFixedModelProjectileFixture.jar(tempDir.resolve("static.jar"),
                false, false, false, false, false, false);
        var analysis = new LegacyFixedModelProjectileAnalyzer().analyze(jar,
                LegacyFixedModelProjectileFixture.RENDERER);
        var proof = analysis.proof().orElseThrow(() -> new AssertionError(analysis.diagnostics()));
        assertEquals("foreign/visual/CuboidModel", proof.modelClass());
        assertEquals("foreign:textures/model/static.png", proof.texture());
        assertEquals(32, proof.textureWidth());
        assertEquals(32, proof.textureHeight());
        assertEquals(0.075F, proof.scale(), 0.000001F);
        assertEquals(90F, proof.angle());
        assertEquals(1F, proof.axisX());
        assertEquals(0F, proof.axisY());
        assertEquals(1F, proof.axisZ());
        assertEquals(4, proof.cuboids().size());
        var first = proof.cuboids().getFirst();
        assertEquals("pieceA", first.name());
        assertEquals(0, first.u());
        assertEquals(4, first.v());
        assertEquals(4, first.width());
        assertEquals(2, first.height());
        assertEquals(2, first.depth());
        assertEquals(-1F, first.pivotX());
        assertEquals(7F, first.pivotY());
        assertEquals(3F, first.pivotZ());
    }

    @Test
    void unreachableTileOnlyAnimationDoesNotInvalidateProjectileDraw() throws Exception {
        Path jar = LegacyFixedModelProjectileFixture.jar(tempDir.resolve("tile-animation.jar"),
                true, false, false, false, false, false);
        var proof = new LegacyFixedModelProjectileAnalyzer().analyze(jar,
                LegacyFixedModelProjectileFixture.RENDERER);
        assertTrue(proof.proof().isPresent(), () -> "Unreachable tile-only method rejected: " + proof.diagnostics());
    }

    @Test
    void reachedMutationIsNotMisclassifiedAsFixed() throws Exception {
        assertRejected("reached-mutation", false, true, false, false, false, false);
    }

    @Test
    void dynamicSourceYawCannotPassAsConstantAxisAngle() throws Exception {
        assertRejected("dynamic-angle", false, false, true, false, false, false);
    }

    @Test
    void unsupportedExtraGlStateMustFailClosed() throws Exception {
        assertRejected("extra-gl", false, false, false, true, false, false);
    }

    @Test
    void unrepresentedPartRotationMustFailClosed() throws Exception {
        assertRejected("part-rotation", false, false, false, false, true, false);
    }

    @Test
    void missingTextureMustFailClosed() throws Exception {
        assertRejected("missing-texture", false, false, false, false, false, true);
    }

    @Test
    void modelDrawMustReadTheExactlyConstructedRendererField() throws Exception {
        Path jar=LegacyFixedModelProjectileFixture.jarWithMisboundModelField(
                tempDir.resolve("wrong-model-field.jar"));
        var result=new LegacyFixedModelProjectileAnalyzer().analyze(jar,
                LegacyFixedModelProjectileFixture.RENDERER);
        assertTrue(result.proof().isEmpty(), "A different uninitialized renderer field must not become a model proof");
    }

    @Test
    void modelConstructorCannotWriteUnprovedSourceState() throws Exception {
        Path jar=LegacyFixedModelProjectileFixture.jarWithUnprovedConstructorFieldWrite(
                tempDir.resolve("model-state.jar"));
        var result=new LegacyFixedModelProjectileAnalyzer().analyze(jar,
                LegacyFixedModelProjectileFixture.RENDERER);
        assertTrue(result.proof().isEmpty(), "Unmodelled constructor effects are not an inert fixed cuboid");
    }

    private void assertRejected(String name, boolean unreachable, boolean renderMutation,
                                boolean dynamicRotation, boolean extraCall, boolean constructorRotation,
                                boolean omitTexture) throws Exception {
        Path jar = LegacyFixedModelProjectileFixture.jar(tempDir.resolve(name + ".jar"),
                unreachable, renderMutation, dynamicRotation, extraCall, constructorRotation, omitTexture);
        var analysis = new LegacyFixedModelProjectileAnalyzer().analyze(jar,
                LegacyFixedModelProjectileFixture.RENDERER);
        assertTrue(analysis.proof().isEmpty(), () -> name + " was incorrectly admitted");
        assertFalse(analysis.diagnostics().isEmpty());
    }
}
