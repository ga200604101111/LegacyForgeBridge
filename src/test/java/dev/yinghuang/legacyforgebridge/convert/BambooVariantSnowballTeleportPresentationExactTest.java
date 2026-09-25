package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Tag("exact-corpus")
class BambooVariantSnowballTeleportPresentationExactTest {
    private static final String BAMBOO_SHA256="bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";

    @Test void exactBambooProvesEnderTeleportPortalPresentation()throws Exception{
        String input=System.getProperty("lfb.exactCorpus.jar");assertNotNull(input,"exact Bamboo corpus is required");Path source=Path.of(input);assertTrue(Files.isRegularFile(source));assertEquals(BAMBOO_SHA256,Hashing.sha256(source));
        var analysis=new LegacyVariantSnowballTeleportPresentationAnalyzer().analyze(source);var proof=analysis.proofs().stream().filter(p->"snowball".equals(p.registryName())&&"ender".equals(p.enumField())).findFirst().orElseThrow(()->new AssertionError("Bamboo Ender teleport presentation not found: "+analysis.proofs()));
        assertEquals(6,proof.selectorId());assertTrue(proof.portalParticleLoopProven(),proof.blockers().toString());assertTrue(proof.interpolationProven(),proof.blockers().toString());assertTrue(proof.randomizationProven(),proof.blockers().toString());assertTrue(proof.originPortalSoundProven(),proof.blockers().toString());assertTrue(proof.entityPortalSoundProven(),proof.blockers().toString());assertTrue(proof.successReturnAfterPresentationProven(),proof.blockers().toString());assertTrue(proof.presentationProven(),proof.blockers().toString());
    }
}
