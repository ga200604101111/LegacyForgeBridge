package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Tag("exact-corpus")
class BambooVariantSnowballImpactExactTest {
    private static final String BAMBOO_SHA256="bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";

    @Test void exactBambooProvesDirtySnowballCommonImpactShell()throws Exception{
        String input=System.getProperty("lfb.exactCorpus.jar");assertNotNull(input,"exact Bamboo corpus is required");Path source=Path.of(input);assertTrue(Files.isRegularFile(source));assertEquals(BAMBOO_SHA256,Hashing.sha256(source));
        var analysis=new LegacyVariantSnowballImpactAnalyzer().analyze(source);
        var proof=analysis.proofs().stream().filter(p->"snowball".equals(p.registryName())).findFirst().orElseThrow(()->new AssertionError("Bamboo dirty snowball impact family not found: "+analysis.proofs()));
        assertEquals("ruby/bamboo/entity/EntityDirtySnowball",proof.projectileClass());assertEquals("ruby/bamboo/entity/EnumDirtySnowball",proof.selectorClass());
        assertTrue(proof.selectorNullGuardProven(),proof.blockers().toString());assertTrue(proof.selectorBaseDamageAttackProven(),proof.blockers().toString());
        assertTrue(proof.snowballPoofLoopProven(),proof.blockers().toString());assertTrue(proof.serverTerminationProven(),proof.blockers().toString());assertTrue(proof.commonImpactSemanticsProven(),proof.blockers().toString());
    }
}
