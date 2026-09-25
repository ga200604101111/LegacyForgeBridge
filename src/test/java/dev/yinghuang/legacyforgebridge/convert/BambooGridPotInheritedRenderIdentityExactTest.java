package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Exact Bamboo guard for the 1.7.10 platform-base render identity fallback. */
@Tag("exact-corpus")
class BambooGridPotInheritedRenderIdentityExactTest {
    private static final String BAMBOO_SHA256 =
            "bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";

    @Test
    void exactBambooMossInheritsVanillaBlockRenderTypeZero() throws Exception {
        String input = System.getProperty("lfb.exactCorpus.jar");
        assertNotNull(input, "exact Bamboo corpus is required");
        Path source = Path.of(input);
        assertTrue(Files.isRegularFile(source));
        assertEquals(BAMBOO_SHA256, Hashing.sha256(source));

        var analysis = new LegacyRegisteredBlockRenderTypeAnalyzer().analyze(source);
        var moss = analysis.rules().stream()
                .filter(rule -> "ruby/bamboo/block/BlockMoss".equals(rule.sourceBlockClass()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Exact Bamboo BlockMoss render identity was not proven: " + analysis.diagnostics()));

        assertEquals("bambooMoss", moss.registryName());
        assertTrue(moss.renderIdentity().isConstant(0), moss.renderIdentity().toString());
    }
}
