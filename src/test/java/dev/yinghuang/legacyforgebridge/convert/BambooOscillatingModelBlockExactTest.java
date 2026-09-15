package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Tag("exact-corpus")
class BambooOscillatingModelBlockExactTest {
    private static final String BAMBOO_SHA256="bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";
    @Test void exactManekiClientOscillatorIsSourceProven()throws Exception{
        String input=System.getProperty("lfb.exactCorpus.jar");assertNotNull(input);Path source=Path.of(input);assertTrue(Files.isRegularFile(source));assertEquals(BAMBOO_SHA256,Hashing.sha256(source));
        var analysis=new LegacyOscillatingModelBlockAnalyzer().analyze(source);var rule=analysis.rules().stream().filter(value->value.sourceBlockClass().equals("ruby/bamboo/block/BlockManeki")).findFirst().orElseThrow();
        assertEquals("ruby/bamboo/tileentity/TileEntityManeki",rule.sourceTileClass());assertEquals("Maneki",rule.legacyTileId());assertArrayEquals(new int[]{2,1,0,3},rule.placementMetaByYawQuadrant());assertTrue(rule.fullCube());assertEquals(0,rule.lightEmission());assertTrue(rule.nonOpaque());assertTrue(rule.nonNormalRender());assertEquals(1,rule.renderPass());assertEquals(90,rule.randomInitialBound());assertEquals(.7F,rule.stepDegrees());assertEquals(0F,rule.lowerBoundDegrees());assertEquals(45F,rule.upperBoundDegrees());assertTrue(rule.clientOnly());assertFalse(rule.nbtPersistent());
    }
}
