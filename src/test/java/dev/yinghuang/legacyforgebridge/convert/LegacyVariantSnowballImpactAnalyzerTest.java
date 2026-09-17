package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LegacyVariantSnowballImpactAnalyzerTest {
    @TempDir Path tempDir;

    @Test void provesBoundedCommonImpactShell()throws Exception{
        Path jar=VariantSnowballImpactFixture.write(tempDir.resolve("impact.jar"));
        var analysis=new LegacyVariantSnowballImpactAnalyzer().analyze(jar);
        assertEquals(1,analysis.proofs().size());var proof=analysis.proofs().getFirst();
        assertEquals("variant_ball",proof.registryName());assertTrue(proof.selectorNullGuardProven(),proof.blockers().toString());
        assertTrue(proof.selectorBaseDamageAttackProven(),proof.blockers().toString());assertTrue(proof.snowballPoofLoopProven(),proof.blockers().toString());
        assertTrue(proof.serverTerminationProven(),proof.blockers().toString());assertTrue(proof.commonImpactSemanticsProven(),proof.blockers().toString());assertTrue(proof.blockers().isEmpty());
    }

    @Test void wrongParticleCountFailsClosedWithoutDiscardingIndependentFacts()throws Exception{
        Path jar=VariantSnowballImpactFixture.writeWrongParticleCount(tempDir.resolve("seven.jar"));
        var proof=new LegacyVariantSnowballImpactAnalyzer().analyze(jar).proofs().getFirst();
        assertTrue(proof.selectorNullGuardProven());assertTrue(proof.selectorBaseDamageAttackProven());assertFalse(proof.snowballPoofLoopProven());assertTrue(proof.serverTerminationProven());
        assertFalse(proof.commonImpactSemanticsProven());assertTrue(proof.blockers().contains("eight-snowballpoof-self-position-loop-not-proven"));
    }

    @Test void missingServerGateFailsClosed()throws Exception{
        Path jar=VariantSnowballImpactFixture.writeMissingServerGate(tempDir.resolve("ungated.jar"));
        var proof=new LegacyVariantSnowballImpactAnalyzer().analyze(jar).proofs().getFirst();
        assertTrue(proof.selectorNullGuardProven());assertTrue(proof.selectorBaseDamageAttackProven());assertTrue(proof.snowballPoofLoopProven());assertFalse(proof.serverTerminationProven());
        assertFalse(proof.commonImpactSemanticsProven());assertTrue(proof.blockers().contains("server-side-projectile-termination-not-proven"));
    }
}
