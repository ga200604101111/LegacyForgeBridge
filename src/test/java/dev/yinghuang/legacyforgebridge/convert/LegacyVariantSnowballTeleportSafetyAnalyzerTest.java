package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LegacyVariantSnowballTeleportSafetyAnalyzerTest {
    @TempDir Path tempDir;

    @Test void provesGroundSearchCollisionLiquidAndSuccessBinding()throws Exception{
        Path jar=VariantSnowballTeleportSafetyFixture.write(tempDir.resolve("safety.jar"));var analysis=new LegacyVariantSnowballTeleportSafetyAnalyzer().analyze(jar);assertEquals(1,analysis.proofs().size());var proof=analysis.proofs().getFirst();
        assertTrue(proof.flooredCoordinatesProven(),proof.blockers().toString());assertTrue(proof.blockExistsGateProven(),proof.blockers().toString());assertTrue(proof.downwardGroundSearchProven(),proof.blockers().toString());assertTrue(proof.groundGuardedRepositionProven(),proof.blockers().toString());assertTrue(proof.collisionEmptyGateProven(),proof.blockers().toString());assertTrue(proof.nonLiquidGateProven(),proof.blockers().toString());assertTrue(proof.successFlagBindingProven(),proof.blockers().toString());assertTrue(proof.gameplaySafetyCoreProven(),proof.blockers().toString());
    }

    @Test void invertedLiquidConditionFailsClosedWithoutErasingIndependentSafetyFacts()throws Exception{
        Path jar=VariantSnowballTeleportSafetyFixture.writeInvertedLiquidGate(tempDir.resolve("bad-liquid.jar"));var proof=new LegacyVariantSnowballTeleportSafetyAnalyzer().analyze(jar).proofs().getFirst();
        assertTrue(proof.flooredCoordinatesProven());assertTrue(proof.blockExistsGateProven());assertTrue(proof.downwardGroundSearchProven());assertTrue(proof.groundGuardedRepositionProven());assertTrue(proof.collisionEmptyGateProven());assertFalse(proof.nonLiquidGateProven());assertTrue(proof.successFlagBindingProven(),"source still writes the same success flag, but under an unsafe liquid condition");assertFalse(proof.gameplaySafetyCoreProven());assertTrue(proof.blockers().contains("non-liquid-success-gate-proof-missing"),proof.blockers().toString());
    }
}
