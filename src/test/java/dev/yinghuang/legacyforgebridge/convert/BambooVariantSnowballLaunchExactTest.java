package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Tag("exact-corpus")
class BambooVariantSnowballLaunchExactTest {
    private static final String BAMBOO_SHA256="bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";

    @Test void exactBambooProvesDirtySnowballLaunchShell()throws Exception{
        String input=System.getProperty("lfb.exactCorpus.jar");assertNotNull(input,"exact Bamboo corpus is required");Path source=Path.of(input);assertTrue(Files.isRegularFile(source));assertEquals(BAMBOO_SHA256,Hashing.sha256(source));
        var analysis=new LegacyVariantSnowballLaunchAnalyzer().analyze(source);var proof=analysis.proofs().stream().filter(p->"snowball".equals(p.registryName())).findFirst().orElseThrow(()->new AssertionError("Bamboo dirty snowball launch family not found: "+analysis.proofs()));
        assertEquals("ruby/bamboo/item/ItemDirtySnowball",proof.itemClass());assertEquals("ruby/bamboo/entity/EntityDirtySnowball",proof.projectileClass());assertTrue(proof.creativeConsumptionGuardProven(),proof.blockers().toString());assertTrue(proof.serverOnlyLaunchGateProven(),proof.blockers().toString());assertTrue(proof.legacyBowSoundProven(),proof.blockers().toString());assertTrue(proof.metadataProjectileSpawnInsideGateProven(),proof.blockers().toString());assertTrue(proof.originalStackReturnProven(),proof.blockers().toString());assertTrue(proof.itemUseSemanticsComplete(),proof.blockers().toString());
    }
}
