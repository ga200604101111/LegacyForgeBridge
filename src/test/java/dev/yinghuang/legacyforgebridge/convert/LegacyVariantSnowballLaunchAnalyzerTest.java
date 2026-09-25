package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LegacyVariantSnowballLaunchAnalyzerTest {
    @TempDir Path tempDir;

    @Test void provesBoundedLegacyLaunchShell()throws Exception{
        Path jar=VariantSnowballLaunchFixture.write(tempDir.resolve("launch.jar"));var analysis=new LegacyVariantSnowballLaunchAnalyzer().analyze(jar);assertEquals(1,analysis.proofs().size());var proof=analysis.proofs().getFirst();
        assertTrue(proof.creativeConsumptionGuardProven(),proof.blockers().toString());assertTrue(proof.serverOnlyLaunchGateProven(),proof.blockers().toString());assertTrue(proof.legacyBowSoundProven(),proof.blockers().toString());assertTrue(proof.metadataProjectileSpawnInsideGateProven(),proof.blockers().toString());assertTrue(proof.originalStackReturnProven(),proof.blockers().toString());assertTrue(proof.itemUseSemanticsComplete(),proof.blockers().toString());
    }

    @Test void missingCreativeGuardFailsOnlyConsumptionAndAggregate()throws Exception{
        var proof=new LegacyVariantSnowballLaunchAnalyzer().analyze(VariantSnowballLaunchFixture.writeMissingCreativeGuard(tempDir.resolve("creative.jar"))).proofs().getFirst();assertFalse(proof.creativeConsumptionGuardProven());assertTrue(proof.serverOnlyLaunchGateProven());assertTrue(proof.legacyBowSoundProven());assertTrue(proof.metadataProjectileSpawnInsideGateProven());assertTrue(proof.originalStackReturnProven());assertFalse(proof.itemUseSemanticsComplete());
    }

    @Test void missingServerGateFailsServerSoundAndContainedSpawn()throws Exception{
        var proof=new LegacyVariantSnowballLaunchAnalyzer().analyze(VariantSnowballLaunchFixture.writeMissingServerGate(tempDir.resolve("server.jar"))).proofs().getFirst();assertTrue(proof.creativeConsumptionGuardProven());assertFalse(proof.serverOnlyLaunchGateProven());assertFalse(proof.legacyBowSoundProven());assertFalse(proof.metadataProjectileSpawnInsideGateProven());assertTrue(proof.originalStackReturnProven());assertFalse(proof.itemUseSemanticsComplete());
    }

    @Test void wrongSoundKeepsServerAndSpawnFactsButFailsClosed()throws Exception{
        var proof=new LegacyVariantSnowballLaunchAnalyzer().analyze(VariantSnowballLaunchFixture.writeWrongSound(tempDir.resolve("sound.jar"))).proofs().getFirst();assertTrue(proof.creativeConsumptionGuardProven());assertTrue(proof.serverOnlyLaunchGateProven());assertFalse(proof.legacyBowSoundProven());assertTrue(proof.metadataProjectileSpawnInsideGateProven());assertTrue(proof.originalStackReturnProven());assertFalse(proof.itemUseSemanticsComplete());
    }
}
