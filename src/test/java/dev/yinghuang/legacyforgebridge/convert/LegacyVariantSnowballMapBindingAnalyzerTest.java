package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LegacyVariantSnowballMapBindingAnalyzerTest {
    @TempDir Path tempDir;

    @Test void provesCanonicalEnumIdToSameSelectorPopulationLoop()throws Exception{
        Path jar=VariantSnowballMapBindingFixture.write(tempDir.resolve("bound.jar"));
        var analysis=new LegacyVariantSnowballMapBindingAnalyzer().analyze(jar);
        assertEquals(1,analysis.proofs().size());
        var proof=analysis.proofs().getFirst();
        assertEquals("variant_ball",proof.registryName());
        assertEquals("foreign/item/VariantBall",proof.mapOwner());
        assertEquals("KINDS",proof.mapField());
        assertTrue(proof.bindingProven(),proof.blocker());
        assertNull(proof.blocker());
    }

    @Test void crossMappedPopulationFailsClosed()throws Exception{
        Path jar=VariantSnowballMapBindingFixture.writeCrossMapped(tempDir.resolve("crossed.jar"));
        var analysis=new LegacyVariantSnowballMapBindingAnalyzer().analyze(jar);
        assertEquals(1,analysis.proofs().size());
        var proof=analysis.proofs().getFirst();
        assertFalse(proof.bindingProven());
        assertNotNull(proof.blocker());
        assertTrue(proof.blocker().contains("map.put(selector.getId(), selector)"),proof.blocker());
    }
}
