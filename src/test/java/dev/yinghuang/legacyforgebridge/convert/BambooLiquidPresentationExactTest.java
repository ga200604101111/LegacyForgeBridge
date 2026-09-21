package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Tag("exact-corpus")
class BambooLiquidPresentationExactTest {
    private static final String SHA="bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";

    @Test
    void exactSpaWaterIsProvenAsEmptyCollisionWaterLiquid()throws Exception{
        String input=System.getProperty("lfb.exactCorpus.jar");assertNotNull(input);
        Path source=Path.of(input);assertTrue(Files.isRegularFile(source));assertEquals(SHA,Hashing.sha256(source));
        var analysis=new LegacyLiquidBlockAnalyzer().analyze(source);
        var rule=analysis.rules().stream().filter(value->value.sourceClass().equals("ruby/bamboo/block/BlockSpaWater"))
                .findFirst().orElseThrow(()->new AssertionError("SpaWater liquid proof missing; skipped="+analysis.skipped()));
        assertEquals("spa_water",rule.registryName());
        assertEquals(LegacyLiquidBlockAnalyzer.Kind.WATER,rule.kind());
        assertEquals(4,rule.renderType());assertTrue(rule.collisionEmpty());assertTrue(rule.nonOpaque());assertTrue(rule.nonNormal());
        assertEquals(8D/9D,rule.height(0),0.000001D);assertEquals(1D/9D,rule.height(7),0.000001D);
    }
}
