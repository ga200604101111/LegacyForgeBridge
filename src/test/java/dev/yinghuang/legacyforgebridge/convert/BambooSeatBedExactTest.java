package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Tag("exact-corpus")
class BambooSeatBedExactTest {
    private static final String BAMBOO_SHA256 =
            "bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";

    @Test
    void exactBambooHutonIsRecognizedAsTheBoundedBedSeatFamily() throws Exception {
        String input = System.getProperty("lfb.exactCorpus.jar");
        assertNotNull(input, "exact Bamboo corpus is required");
        Path source = Path.of(input);
        assertTrue(Files.isRegularFile(source));
        assertEquals(BAMBOO_SHA256, Hashing.sha256(source));

        LegacySeatBedAnalyzer.Analysis analysis = new LegacySeatBedAnalyzer().analyze(source);
        assertTrue(analysis.diagnostics().isEmpty(), analysis.diagnostics().toString());
        LegacySeatBedAnalyzer.Rule huton = analysis.rules().stream()
                .filter(rule -> "bamboohuton".equals(rule.registryName()))
                .findFirst().orElseThrow(() -> new AssertionError("bamboohuton not proven: " + analysis.skipped()));

        assertEquals("huton", huton.placementItemRegistryName());
        assertEquals("ruby/bamboo/block/BlockHuton", huton.sourceBlockClass());
        assertEquals("ruby/bamboo/item/ItemHuton", huton.sourcePlacementItemClass());
        assertEquals("ruby/bamboo/tileentity/TileEntityHuton", huton.sourceTileClass());
        assertEquals("ruby/bamboo/entity/EntityDummyChair", huton.sourceSeatRuntimeClass());
        assertTrue(huton.sourceSeatEntityClass().startsWith("ruby/bamboo/tileentity/TileEntityHuton$"));
        assertTrue(huton.coreSourceProofComplete());
        assertTrue(huton.timeAccelerationSourceProven());
        assertTrue(huton.specialPresentationRequired());
    }
}
