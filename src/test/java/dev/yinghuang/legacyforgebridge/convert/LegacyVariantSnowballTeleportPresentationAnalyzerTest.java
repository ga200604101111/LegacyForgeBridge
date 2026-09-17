package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LegacyVariantSnowballTeleportPresentationAnalyzerTest {
    @TempDir Path tempDir;

    @Test void provesExactSuccessOnlyPortalPresentation()throws Exception{
        Path jar=VariantSnowballTeleportPresentationFixture.write(tempDir.resolve("presentation.jar"));var analysis=new LegacyVariantSnowballTeleportPresentationAnalyzer().analyze(jar);assertEquals(1,analysis.proofs().size());var proof=analysis.proofs().getFirst();
        assertTrue(proof.portalParticleLoopProven(),proof.blockers().toString());assertTrue(proof.interpolationProven(),proof.blockers().toString());assertTrue(proof.randomizationProven(),proof.blockers().toString());assertTrue(proof.originPortalSoundProven(),proof.blockers().toString());assertTrue(proof.entityPortalSoundProven(),proof.blockers().toString());assertTrue(proof.successReturnAfterPresentationProven(),proof.blockers().toString());assertTrue(proof.presentationProven(),proof.blockers().toString());
    }

    @Test void wrongParticleCountFailsClosed()throws Exception{
        Path jar=VariantSnowballTeleportPresentationFixture.writeWrongParticleCount(tempDir.resolve("count.jar"));var proof=new LegacyVariantSnowballTeleportPresentationAnalyzer().analyze(jar).proofs().getFirst();assertFalse(proof.portalParticleLoopProven());assertFalse(proof.presentationProven());assertTrue(proof.blockers().contains("exact-128-portal-particle-loop-not-proven"),proof.blockers().toString());
    }

    @Test void wrongEntitySoundKeepsParticleFactsButClosesPresentation()throws Exception{
        Path jar=VariantSnowballTeleportPresentationFixture.writeWrongEntitySound(tempDir.resolve("sound.jar"));var proof=new LegacyVariantSnowballTeleportPresentationAnalyzer().analyze(jar).proofs().getFirst();assertTrue(proof.portalParticleLoopProven(),proof.blockers().toString());assertTrue(proof.interpolationProven());assertTrue(proof.randomizationProven());assertTrue(proof.originPortalSoundProven());assertFalse(proof.entityPortalSoundProven());assertFalse(proof.presentationProven());assertTrue(proof.blockers().contains("entity-path-portal-sound-not-proven"),proof.blockers().toString());
    }
}
