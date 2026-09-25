package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Tag("exact-corpus")
class BambooVariantSnowballExactTest {
    private static final String BAMBOO_SHA256="bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";

    @Test void exactBambooProvesDirtySnowballSelectorAndDamageTable()throws Exception{
        String input=System.getProperty("lfb.exactCorpus.jar");assertNotNull(input,"exact Bamboo corpus is required");Path source=Path.of(input);assertTrue(Files.isRegularFile(source));assertEquals(BAMBOO_SHA256,Hashing.sha256(source));
        var analysis=new LegacyVariantSnowballAnalyzer().analyze(source);
        var rule=analysis.rules().stream().filter(r->"snowball".equals(r.registryName())).findFirst().orElseThrow(()->new AssertionError("Bamboo dirty snowball family not proven: "+analysis.skipped()));
        assertEquals("ruby/bamboo/item/ItemDirtySnowball",rule.itemClass());assertEquals("ruby/bamboo/entity/EntityDirtySnowball",rule.projectileClass());assertEquals("ruby/bamboo/entity/EnumDirtySnowball",rule.selectorClass());
        assertEquals(10,rule.variants().size());
        int[] damage={1,2,3,8,6,1,0,0,0,0};for(int i=0;i<damage.length;i++){assertEquals(i,rule.variants().get(i).id());assertEquals(damage[i],rule.variants().get(i).damage());}
    }
}
