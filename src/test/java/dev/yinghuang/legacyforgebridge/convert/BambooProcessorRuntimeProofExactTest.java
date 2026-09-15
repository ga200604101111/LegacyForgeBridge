package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exact external-corpus proof for MillStone runtime-only semantics. */
@Tag("exact-corpus")
class BambooProcessorRuntimeProofExactTest {
    private static final String BAMBOO_SHA256="bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";

    @Test
    void exactMillStoneProvesExtractionAndEnergyContract()throws Exception{
        String input=System.getProperty("lfb.exactCorpus.jar");
        assertNotNull(input,"Run exactCorpusTest with the checksum-pinned Bamboo 2.6.8.5 JAR");
        Path source=Path.of(input);assertTrue(Files.isRegularFile(source));assertEquals(BAMBOO_SHA256,Hashing.sha256(source));
        var topology=new LegacySingleInputProcessorAnalyzer().analyze(source);
        assertEquals(1,topology.rules().size(),topology.skipped().toString());
        var proof=new LegacySingleInputProcessorRuntimeAnalyzer().analyze(source,topology.rules().getFirst());
        assertTrue(proof.complete(),proof.diagnostics().toString());
        assertTrue(proof.sidedExtractionProven());
        assertTrue(proof.legacyEnergyApiPresent());
        assertEquals(100,proof.minUseEnergy());
        assertEquals(500,proof.maxUseEnergy());
        assertEquals("innerEnergy",proof.energyNbtKey());
        assertTrue(proof.energyAccelerationProven());
        assertEquals(6,LegacySingleInputProcessorRuntimeAnalyzer.sourceProgressStep(10,500,100));
        assertEquals(-100,LegacySingleInputProcessorRuntimeAnalyzer.sourceEnergyAfterStep(10,500,100));
    }
}
