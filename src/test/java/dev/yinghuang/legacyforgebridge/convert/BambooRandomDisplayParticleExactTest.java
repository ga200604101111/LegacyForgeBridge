package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Tag("exact-corpus")
class BambooRandomDisplayParticleExactTest {
    private static final String SHA="bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";

    @Test
    void exactCampfireProvesSmokeAndFlameAtSourceOffsets()throws Exception{
        String input=System.getProperty("lfb.exactCorpus.jar");assertNotNull(input);
        Path source=Path.of(input);assertTrue(Files.isRegularFile(source));assertEquals(SHA,Hashing.sha256(source));
        var analysis=new LegacyRandomDisplayParticleAnalyzer().analyze(source);
        var rule=analysis.rules().stream().filter(value->value.sourceBlockClass().equals("ruby/bamboo/block/BlockCampfire"))
                .findFirst().orElseThrow(()->new AssertionError("Campfire particle proof missing; skipped="+analysis.skipped()));
        assertEquals("campfire",rule.registryName());assertEquals(java.util.List.of("smoke","flame"),rule.particles());
        assertEquals(.5F,rule.centerX(),.0001F);assertEquals(.2F,rule.centerY(),.0001F);assertEquals(.5F,rule.centerZ(),.0001F);
        assertEquals(.2F,rule.spreadX(),.0001F);assertEquals(.2F,rule.spreadZ(),.0001F);
    }
}
