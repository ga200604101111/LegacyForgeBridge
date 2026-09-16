package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checksum-pinned guard for the first generic nine-cell grid-pot family admitted from Bamboo. */
@Tag("exact-corpus")
class BambooGridPotExactTest {
    private static final String BAMBOO_SHA256 =
            "bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";

    @Test
    void exactBambooProvesNineCellGridPotCoreWithoutOpeningLegacyRenderPredicate() throws Exception {
        String input = System.getProperty("lfb.exactCorpus.jar");
        assertNotNull(input, "exact Bamboo corpus is required");
        Path source = Path.of(input);
        assertTrue(Files.isRegularFile(source));
        assertEquals(BAMBOO_SHA256, Hashing.sha256(source));

        var analysis = new LegacyGridPotBlockAnalyzer().analyze(source);
        assertTrue(analysis.diagnostics().isEmpty(), analysis.diagnostics().toString());
        assertEquals(1, analysis.rules().size(), analysis.skipped().toString());
        var rule = analysis.rules().getFirst();
        assertEquals("bambooMultiPot", rule.registryName());
        assertEquals("ruby/bamboo/block/BlockMultiPot", rule.sourceBlockClass());
        assertEquals("ruby/bamboo/item/ItemMultiPot", rule.sourceItemBlockClass());
        assertEquals("ruby/bamboo/tileentity/TileEntityMultiPot", rule.sourceTileClass());
        assertEquals("BambooMultiPot", rule.legacyTileId());
        assertEquals(9, rule.cells());
        assertEquals(3, rule.gridWidth());
        assertEquals(0.01F, rule.baseHeight());
        assertEquals(0.375F, rule.cellHeight());
        assertTrue(rule.placementCreatesCell());
        assertTrue(rule.emptyHandRemovalProven());
        assertTrue(rule.selfItemAddsCellProven());
        assertTrue(rule.breakDropsEveryEnabledCell());
        assertTrue(rule.normalBlockDropDisabled());
        assertTrue(rule.persistenceProven());
        assertTrue(rule.dynamicCellShapeProven());
        assertTrue(rule.nonOpaqueProven());
        assertTrue(rule.contentInsertionPredicateProven());
    }
}
