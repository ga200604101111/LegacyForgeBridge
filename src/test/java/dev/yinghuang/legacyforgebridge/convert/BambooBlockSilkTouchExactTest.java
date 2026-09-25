package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("exact-corpus")
class BambooBlockSilkTouchExactTest {
    private static final String BAMBOO_SHA256 =
            "bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";

    @Test
    void exactBambooScopedSubtypeMutationsNoLongerGloballyPoisonSilkProof() throws Exception {
        String input = System.getProperty("lfb.exactCorpus.jar");
        assertNotNull(input, "exact Bamboo corpus is required");
        Path source = Path.of(input);
        assertTrue(Files.isRegularFile(source));
        assertEquals(BAMBOO_SHA256, Hashing.sha256(source));

        var analysis = new LegacyBlockSilkTouchAnalyzer().analyze(source);
        assertEquals(63, analysis.proofs().size());
        assertEquals(5, analysis.proofs().stream()
                .filter(LegacyBlockSilkTouchAnalyzer.Proof::eligibilityProofComplete).count());
        assertEquals(7, analysis.proofs().stream()
                .filter(LegacyBlockSilkTouchAnalyzer.Proof::stackedItemProofComplete).count());

        List<LegacyBlockSilkTouchAnalyzer.Proof> fullyProven = analysis.proofs().stream()
                .filter(value -> value.eligibilityProofComplete() && value.stackedItemProofComplete())
                .toList();
        assertEquals(1, fullyProven.size());
        var moss = fullyProven.getFirst();
        assertEquals("bambooMoss", moss.registryName());
        assertEquals(Boolean.TRUE, moss.silkEligible());
        assertEquals(0, moss.stackedLegacyDamage());
    }
}
