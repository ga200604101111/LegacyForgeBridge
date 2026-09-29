package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Tag("exact-corpus")
class BambooCombatItemExactTest {
    private static final String SHA="bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";

    @Test
    void exactBambooSwordIsDiscoveredFromGenericLegacyWeaponBehavior()throws Exception{
        String input=System.getProperty("lfb.exactCorpus.jar");assertNotNull(input);
        Path source=Path.of(input);assertTrue(Files.isRegularFile(source));assertEquals(SHA,Hashing.sha256(source));

        var analysis=new LegacyCombatItemAnalyzer().analyze(source);
        var rule=analysis.rules().stream()
                .filter(value->"ruby/bamboo/item/ItemBambooSword".equals(value.sourceClass()))
                .findFirst().orElseThrow(()->new AssertionError("Bamboo sword was not admitted by generic combat analysis; skipped="+analysis.skipped()));

        assertEquals(LegacyCombatItemAnalyzer.Kind.SWORD,rule.kind());
        assertEquals(200,rule.durability());
        assertEquals(4.0F,rule.attackDamage(),0.0001F);
    }

    @Test
    void exactBambooPickaxeKeepsVanillaToolDamageAndCustomDurability()throws Exception{
        String input=System.getProperty("lfb.exactCorpus.jar");assertNotNull(input);
        Path source=Path.of(input);assertTrue(Files.isRegularFile(source));assertEquals(SHA,Hashing.sha256(source));

        var analysis=new LegacyCombatItemAnalyzer().analyze(source);
        var rule=analysis.rules().stream()
                .filter(value->"ruby/bamboo/item/ItemBambooPickaxe".equals(value.sourceClass()))
                .findFirst().orElseThrow(()->new AssertionError("Bamboo pickaxe tool attribute proof missing; skipped="+analysis.skipped()));

        assertEquals(LegacyCombatItemAnalyzer.Kind.TOOL,rule.kind());
        assertEquals(10000,rule.durability());
        assertEquals(5.0F,rule.attackDamage(),0.0001F);
    }

    @Test
    void exactBambooBowPreservesSourcePullStageTiming()throws Exception{
        String input=System.getProperty("lfb.exactCorpus.jar");assertNotNull(input);
        Path source=Path.of(input);assertTrue(Files.isRegularFile(source));assertEquals(SHA,Hashing.sha256(source));

        var analysis=new LegacyCombatItemAnalyzer().analyze(source);
        var rule=analysis.rules().stream()
                .filter(value->"ruby/bamboo/item/ItemBambooBow".equals(value.sourceClass()))
                .findFirst().orElseThrow(()->new AssertionError("Bamboo bow pull timing proof missing; skipped="+analysis.skipped()));

        assertEquals(LegacyCombatItemAnalyzer.Kind.BOW,rule.kind());
        assertEquals(300,rule.durability());
        assertEquals(3,rule.pullStages());
        assertEquals("bamboo:bamboobow_pull_",rule.pullTexturePrefix());
        assertEquals(java.util.List.of(1,26,40),rule.pullStageMinTicks());
    }
}
