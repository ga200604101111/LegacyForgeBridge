package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Tag("exact-corpus")
class BambooGridPotNegativeBranchExactTest {
    private static final String BAMBOO_SHA256="bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";

    @Test
    void exactBambooProvesMultiPotNegativeFallbackBranch() throws Exception {
        String input=System.getProperty("lfb.exactCorpus.jar");assertNotNull(input,"exact Bamboo corpus is required");
        Path source=Path.of(input);assertTrue(Files.isRegularFile(source));assertEquals(BAMBOO_SHA256,Hashing.sha256(source));
        var analysis=new LegacyGridPotNegativeBranchAnalyzer().analyze(source);
        assertTrue(analysis.diagnostics().isEmpty(),analysis.diagnostics().toString());
        assertEquals(1,analysis.proofs().size());
        var proof=analysis.proofs().getFirst();
        assertEquals("bambooMultiPot",proof.registryName());
        assertEquals("ruby/bamboo/block/BlockMultiPot",proof.sourceBlockClass());
        assertTrue(proof.negativeBranchProven(),proof.blockers().toString());
        assertTrue(proof.blockers().isEmpty());
    }
}
