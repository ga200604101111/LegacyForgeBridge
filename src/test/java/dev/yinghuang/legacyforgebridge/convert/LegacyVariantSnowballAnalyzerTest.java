package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LegacyVariantSnowballAnalyzerTest {
    @TempDir Path tempDir;

    @Test void provesMetadataSelectedProjectileAndEnumConstantsButLeavesImpactClosed()throws Exception{
        Path jar=VariantSnowballFixture.write(tempDir.resolve("variant.jar"));
        var analysis=new LegacyVariantSnowballAnalyzer().analyze(jar);
        assertTrue(analysis.skipped().isEmpty(),analysis.skipped().toString());
        assertEquals(1,analysis.rules().size());
        var rule=analysis.rules().getFirst();
        assertEquals("variant_ball",rule.registryName());
        assertEquals("foreign/item/VariantBall",rule.itemClass());
        assertEquals("foreign/entity/VariantProjectile",rule.projectileClass());
        assertEquals("foreign/entity/VariantKind",rule.selectorClass());
        assertEquals("foreign/item/VariantBall",rule.selectorMapOwner());
        assertEquals("KINDS",rule.selectorMapField());
        assertEquals("code",rule.selectorIdGetter());
        assertEquals("power",rule.selectorDamageGetter());
        assertEquals("func_70184_a",rule.impactMethod());
        assertEquals(2,rule.variants().size());
        assertEquals(new LegacyVariantSnowballAnalyzer.Variant("stone",0,2),rule.variants().get(0));
        assertEquals(new LegacyVariantSnowballAnalyzer.Variant("poison",7,0),rule.variants().get(1));
    }
}
