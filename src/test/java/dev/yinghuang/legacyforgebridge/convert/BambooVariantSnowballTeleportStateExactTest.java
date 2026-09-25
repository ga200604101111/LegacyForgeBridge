package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Tag("exact-corpus")
class BambooVariantSnowballTeleportStateExactTest {
    private static final String BAMBOO_SHA256="bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";

    @Test void exactBambooProvesEnderTeleportStateSkeleton()throws Exception{
        String input=System.getProperty("lfb.exactCorpus.jar");assertNotNull(input,"exact Bamboo corpus is required");Path source=Path.of(input);assertTrue(Files.isRegularFile(source));assertEquals(BAMBOO_SHA256,Hashing.sha256(source));
        var analysis=new LegacyVariantSnowballTeleportStateAnalyzer().analyze(source);
        var proof=analysis.proofs().stream().filter(p->"snowball".equals(p.registryName())&&"ender".equals(p.enumField())).findFirst().orElseThrow(()->new AssertionError("Bamboo ender teleport state skeleton not found: "+analysis.proofs()));
        assertEquals(6,proof.selectorId());assertTrue(proof.savedPositionProven(),proof.blockers().toString());assertTrue(proof.candidateAssignmentProven(),proof.blockers().toString());assertTrue(proof.guardedRollbackFalseProven(),proof.blockers().toString());assertTrue(proof.successTrueReturnProven(),proof.blockers().toString());assertTrue(proof.stateSkeletonProven(),proof.blockers().toString());
    }
}
