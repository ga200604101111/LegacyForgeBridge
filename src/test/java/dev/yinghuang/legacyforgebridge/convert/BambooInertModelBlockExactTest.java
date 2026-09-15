package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Tag("exact-corpus")
class BambooInertModelBlockExactTest {
    private static final String BAMBOO_SHA256="bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";
    @Test void exactAndonIsTheInertBlockContainerTopologyAnchor()throws Exception{
        String input=System.getProperty("lfb.exactCorpus.jar");assertNotNull(input);
        Path source=Path.of(input);assertTrue(Files.isRegularFile(source));assertEquals(BAMBOO_SHA256,Hashing.sha256(source));
        var analysis=new LegacyInertModelBlockAnalyzer().analyze(source);
        var rule=analysis.rules().stream().filter(value->value.sourceBlockClass().equals("ruby/bamboo/block/BlockAndon")).findFirst().orElseThrow();
        assertEquals("ruby/bamboo/tileentity/TileEntityAndon",rule.sourceTileClass());assertEquals("Andon",rule.legacyTileId());
        assertEquals(0.2F,rule.bounds().minX());assertEquals(0F,rule.bounds().minY());assertEquals(0.2F,rule.bounds().minZ());assertEquals(0.8F,rule.bounds().maxX());assertEquals(0.9F,rule.bounds().maxY());assertEquals(0.8F,rule.bounds().maxZ());
        assertEquals(0.95F,rule.sourceLightLevel());assertEquals(14,rule.modernLightEmission());assertEquals(1,rule.renderPass());assertEquals(LegacyInertModelBlockAnalyzer.ORIENTATION_PLAYER_YAW_QUADRANT,rule.orientation());
    }
}
