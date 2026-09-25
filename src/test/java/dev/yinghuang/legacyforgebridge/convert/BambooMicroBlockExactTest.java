package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@Tag("exact-corpus")
class BambooMicroBlockExactTest {
    private static final String SHA="bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";

    @Test
    void exactBambooProvesBothMicroBlockContainersWithoutNameBasedProductionRules() throws Exception {
        String input=System.getProperty("lfb.exactCorpus.jar");
        assertNotNull(input,"exact Bamboo corpus is required");
        Path source=Path.of(input);assertTrue(Files.isRegularFile(source));assertEquals(SHA,Hashing.sha256(source));

        var analysis=new LegacyMicroBlockContainerAnalyzer().analyze(source);
        Map<String,LegacyMicroBlockContainerAnalyzer.Rule> rules=analysis.rules().stream()
                .collect(Collectors.toMap(LegacyMicroBlockContainerAnalyzer.Rule::registryName, Function.identity()));
        assertEquals(2,rules.size(),analysis.diagnostics().toString());

        var normal=rules.get("bamboomultiblock");assertNotNull(normal);
        var alpha=rules.get("alphamultiblock");assertNotNull(alpha);
        for(var rule:new LegacyMicroBlockContainerAnalyzer.Rule[]{normal,alpha}){
            assertEquals("ruby/bamboo/tileentity/TileEntityMultiBlock",rule.sourceTileClass());
            assertEquals("ruby/bamboo/render/block/RenderMultiBlock",rule.sourceRendererClass());
            assertEquals("slotsNBT",rule.listNbtKey());
            assertEquals("this.slotLength",rule.sizeNbtKey());
            assertEquals(1,rule.minFieldSize());
            assertEquals(16,rule.maxFieldSize());
            assertEquals(3,rule.fallbackFieldSize());
            assertTrue(rule.dynamicCellCollision());
        }
        assertFalse(normal.translucentPass());
        assertTrue(alpha.translucentPass());
    }
}
