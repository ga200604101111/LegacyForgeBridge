package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Tag("exact-corpus")
class BambooGridPotSymbolicInsertionExactTest {
    private static final String BAMBOO_SHA256="bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";

    @Test
    void exactBambooPreservesCoordinateCrossAsOneSymbolicIdentityAcrossPredicateAndRegisteredBlock() throws Exception {
        String input=System.getProperty("lfb.exactCorpus.jar");assertNotNull(input,"exact Bamboo corpus is required");
        Path source=Path.of(input);assertTrue(Files.isRegularFile(source));assertEquals(BAMBOO_SHA256,Hashing.sha256(source));
        var grid=new LegacyGridPotBlockAnalyzer().analyze(source);assertEquals(1,grid.rules().size(),grid.skipped().toString());
        String key="ruby/bamboo/CustomRenderHandler#coordinateCrossUID";
        assertTrue(grid.rules().getFirst().contentInsertionSymbolicRenderFields().contains(key));
        var render=new LegacyRegisteredBlockRenderTypeAnalyzer().analyze(source);
        assertTrue(render.rules().stream().anyMatch(rule->"ruby/bamboo/block/BlockBambooShoot".equals(rule.sourceBlockClass())
                &&"ruby/bamboo/CustomRenderHandler".equals(rule.renderIdentity().fieldOwner())
                &&"coordinateCrossUID".equals(rule.renderIdentity().fieldName())),
                "Exact Bamboo bamboo-shoot block must retain the same symbolic coordinateCrossUID identity used by MultiPot");
    }
}
