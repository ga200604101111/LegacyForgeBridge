package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Tag("exact-corpus")
class BambooVariantSnowballSelectorEffectExactTest {
    private static final String BAMBOO_SHA256="bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";

    @Test void exactBambooProvesDirtySnowballSelectorEffectDispatch()throws Exception{
        String input=System.getProperty("lfb.exactCorpus.jar");assertNotNull(input,"exact Bamboo corpus is required");Path source=Path.of(input);assertTrue(Files.isRegularFile(source));assertEquals(BAMBOO_SHA256,Hashing.sha256(source));
        var analysis=new LegacyVariantSnowballSelectorEffectAnalyzer().analyze(source);
        var proof=analysis.proofs().stream().filter(p->"snowball".equals(p.registryName())).findFirst().orElseThrow(()->new AssertionError("Bamboo dirty snowball selector effects not found: "+analysis.proofs()));
        assertEquals("ruby/bamboo/entity/EntityDirtySnowball",proof.projectileClass());assertTrue(proof.selectorDispatchProven(),proof.blockers().toString());assertTrue(proof.selectorEffectEdgesProven(),proof.blockers().toString());assertEquals(3,proof.potionBranchesProven());
        assertPotion(proof,"poison",8,"poison",30,3);assertPotion(proof,"confusion",7,"confusion",200,1);assertPotion(proof,"heal",9,"regeneration",50,1);
        var ender=proof.effects().stream().filter(e->"ender".equals(e.enumField())).findFirst().orElseThrow();assertEquals(6,ender.selectorId());assertEquals("CUSTOM_HELPER_UNCOMPILED",ender.impactEffect());assertNotNull(ender.helperMethod());
        for(String ordinary:new String[]{"stone","ice","iron","gold","diamond","compress"})assertEquals("NONE",proof.effects().stream().filter(e->ordinary.equals(e.enumField())).findFirst().orElseThrow().impactEffect());
        assertFalse(proof.selectorSpecificImpactSemanticsComplete(),"Ender teleport internals are intentionally still uncompiled");
    }

    private static void assertPotion(LegacyVariantSnowballSelectorEffectAnalyzer.Proof proof,String field,int id,String potion,int duration,int amplifier){
        var effect=proof.effects().stream().filter(e->field.equals(e.enumField())).findFirst().orElseThrow();assertEquals(id,effect.selectorId());assertEquals("POTION",effect.impactEffect());assertEquals(potion,effect.potion());assertEquals(duration,effect.duration());assertEquals(amplifier,effect.amplifier());
    }
}
