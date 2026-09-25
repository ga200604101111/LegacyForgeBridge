package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("exact-corpus")
class BambooBlockSilkConstantFalseExactTest {
    private static final String BAMBOO_SHA256 =
            "bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";
    private static final Set<String> NEW_STATIC_SELF_DROP_INPUTS = Set.of(
            "singleTexDeco",
            "thickSakuraPillar", "thinSakuraPillar",
            "thickOrcPillar", "thinOrcPillar",
            "thickSprucePillar", "thinSprucePillar",
            "thickBirchPillar", "thinBirchPillar",
            "delude_width", "delude_height"
    );

    @Test
    void exactBambooConstantFalseRenderOverridesProveSilkDisabled() throws Exception {
        String input = System.getProperty("lfb.exactCorpus.jar");
        assertNotNull(input, "exact Bamboo corpus is required");
        Path source = Path.of(input);
        assertTrue(Files.isRegularFile(source));
        assertEquals(BAMBOO_SHA256, Hashing.sha256(source));

        var analysis = new LegacyBlockSilkTouchAnalyzer().analyze(source);
        assertEquals(63, analysis.proofs().size());
        assertTrue(analysis.diagnostics().isEmpty(), analysis.diagnostics().toString());
        assertEquals(31, analysis.proofs().stream().filter(LegacyBlockSilkTouchAnalyzer.Proof::eligibilityProofComplete).count());
        assertEquals(26, analysis.proofs().stream()
                .filter(LegacyBlockSilkTouchAnalyzer.Proof::eligibilityProofComplete)
                .filter(proof -> Boolean.FALSE.equals(proof.silkEligible()))
                .count());

        var byName = analysis.proofs().stream().collect(Collectors.toMap(
                LegacyBlockSilkTouchAnalyzer.Proof::registryName, value -> value));
        for (String name : NEW_STATIC_SELF_DROP_INPUTS) {
            var proof = byName.get(name);
            assertNotNull(proof, name);
            assertTrue(proof.eligibilityProofComplete(), () -> name + ": " + proof.eligibilityReasons());
            assertEquals(Boolean.FALSE, proof.silkEligible(), name);
        }
    }
}
