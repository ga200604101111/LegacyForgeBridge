package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Tag("exact-corpus")
class BambooGridPotPresentationExactTest {
    private static final String BAMBOO_SHA256 =
            "bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";

    @Test
    void exactBambooProvesMultiPotStoredContentPresentationSurface() throws Exception {
        String input = System.getProperty("lfb.exactCorpus.jar");
        assertNotNull(input, "exact Bamboo corpus is required");
        Path source = Path.of(input);
        assertTrue(Files.isRegularFile(source));
        assertEquals(BAMBOO_SHA256, Hashing.sha256(source));

        var analysis = new LegacyGridPotPresentationAnalyzer().analyze(source);
        var proof = analysis.proofs().stream()
                .filter(value -> "ruby/bamboo/block/BlockMultiPot".equals(value.sourceBlockClass()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Exact Bamboo MultiPot presentation was not proven. skipped="
                        + analysis.skipped() + ", diagnostics=" + analysis.diagnostics()));

        assertEquals("ruby/bamboo/render/block/RenderMultiPot", proof.sourceRendererClass());
        assertEquals("flower_pot", proof.cellCarrierLegacyRegistryName());
        assertTrue(proof.flatInventory());
        assertEquals("bamboo:flower_pot",proof.inventoryTextureName());
        assertEquals(3, proof.gridOffsets().size());
        assertEquals(-0.333F, proof.gridOffsets().get(0), 0.0001F);
        assertEquals(0.0F, proof.gridOffsets().get(1), 0.0001F);
        assertEquals(0.333F, proof.gridOffsets().get(2), 0.0001F);
        assertEquals(0.25F, proof.contentTranslateY(), 0.0001F);
        assertEquals(0.75F, proof.crossedScale(), 0.0001F);
        assertEquals(0.125F, proof.cactusHalfWidth(), 0.0001F);
    }
}
