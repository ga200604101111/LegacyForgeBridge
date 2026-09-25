package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Tag("exact-corpus")
class BambooProjectilePresentationExactTest {
    private static final String SHA="bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";

    @Test
    void exactBambooSpearAndFirecrackerAreAdmittedByGenericProjectileProof()throws Exception{
        String input=System.getProperty("lfb.exactCorpus.jar");assertNotNull(input);
        Path source=Path.of(input);assertTrue(Files.isRegularFile(source));assertEquals(SHA,Hashing.sha256(source));

        var analysis=new LegacyProjectilePresentationAnalyzer().analyze(source);

        var spear=analysis.rules().stream().filter(rule->rule.sourceClass().equals("ruby/bamboo/entity/EntityBambooSpear"))
                .findFirst().orElseThrow(()->new AssertionError("Bamboo spear projectile presentation proof missing; skipped="+analysis.skipped()));
        assertEquals("BSpear",spear.registryName());assertEquals(4,spear.legacyNumericId());
        assertEquals(LegacyProjectilePresentationAnalyzer.BaseFamily.ARROW,spear.baseFamily());
        assertEquals(LegacyProjectilePresentationAnalyzer.Adapter.ORIENTED_ITEM,spear.adapter());
        assertEquals("ruby/bamboo/item/ItemBambooSpear",spear.sourceItemClass());
        assertEquals("bamboospear",spear.sourceItemRegistryName());
        assertEquals(.5F,spear.width(),0.0001F);assertEquals(.5F,spear.height(),0.0001F);
        assertEquals("bamboo:textures/entitys/bamboospear.png",spear.fixedTexture());
        assertEquals(-1,spear.metadataWatcherIndex());

        var firecracker=analysis.rules().stream().filter(rule->rule.sourceClass().equals("ruby/bamboo/entity/EntityFirecracker"))
                .findFirst().orElseThrow(()->new AssertionError("Firecracker projectile presentation proof missing; skipped="+analysis.skipped()));
        assertEquals("FileCracker",firecracker.registryName());assertEquals(6,firecracker.legacyNumericId());
        assertEquals(LegacyProjectilePresentationAnalyzer.BaseFamily.THROWABLE,firecracker.baseFamily());
        assertEquals(LegacyProjectilePresentationAnalyzer.Adapter.THROWN_ITEM,firecracker.adapter());
        assertEquals("ruby/bamboo/item/ItemFirecracker",firecracker.sourceItemClass());
        assertEquals("firecracker",firecracker.sourceItemRegistryName());
        assertEquals(1F,firecracker.width(),0.0001F);assertEquals(1F,firecracker.height(),0.0001F);
        assertEquals(15,firecracker.metadataWatcherIndex());assertEquals(0,firecracker.metadataWatcherWireType());
        assertEquals(-1,firecracker.metadataOffset());assertEquals(0,firecracker.defaultItemMetadata());
    }
}
