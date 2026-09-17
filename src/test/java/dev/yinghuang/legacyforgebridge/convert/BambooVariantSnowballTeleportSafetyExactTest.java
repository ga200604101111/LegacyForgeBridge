package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Tag("exact-corpus")
class BambooVariantSnowballTeleportSafetyExactTest {
    private static final String BAMBOO_SHA256="bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";

    @Test void exactBambooProvesEnderTeleportGameplaySafetyCore()throws Exception{
        String input=System.getProperty("lfb.exactCorpus.jar");assertNotNull(input,"exact Bamboo corpus is required");Path source=Path.of(input);assertTrue(Files.isRegularFile(source));assertEquals(BAMBOO_SHA256,Hashing.sha256(source));
        var analysis=new LegacyVariantSnowballTeleportSafetyAnalyzer().analyze(source);var proof=analysis.proofs().stream().filter(p->"snowball".equals(p.registryName())&&"ender".equals(p.enumField())).findFirst().orElseThrow(()->new AssertionError("Bamboo Ender teleport safety core not found: "+analysis.proofs()));
        assertEquals(6,proof.selectorId());assertTrue(proof.flooredCoordinatesProven(),proof.blockers().toString());assertTrue(proof.blockExistsGateProven(),proof.blockers().toString());assertTrue(proof.downwardGroundSearchProven(),proof.blockers().toString());assertTrue(proof.groundGuardedRepositionProven(),proof.blockers().toString());assertTrue(proof.collisionEmptyGateProven(),proof.blockers().toString());assertTrue(proof.nonLiquidGateProven(),proof.blockers().toString());assertTrue(proof.successFlagBindingProven(),proof.blockers().toString());assertTrue(proof.gameplaySafetyCoreProven(),proof.blockers().toString());
    }
}
