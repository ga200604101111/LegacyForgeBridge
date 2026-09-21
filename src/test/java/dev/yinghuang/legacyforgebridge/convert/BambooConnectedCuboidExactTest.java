package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@Tag("exact-corpus")
class BambooConnectedCuboidExactTest {
    private static final String SHA="bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";

    @Test
    void exactBambooProvesAllPillarsAndLiangWithoutNameBasedProductionRules() throws Exception {
        String input=System.getProperty("lfb.exactCorpus.jar");assertNotNull(input,"exact Bamboo corpus is required");
        Path source=Path.of(input);assertTrue(Files.isRegularFile(source));assertEquals(SHA,Hashing.sha256(source));
        var analysis=new LegacyConnectedCuboidRendererAnalyzer().analyze(source);
        Map<String,LegacyConnectedCuboidRendererAnalyzer.Rule> rules=analysis.rules().stream()
                .collect(Collectors.toMap(LegacyConnectedCuboidRendererAnalyzer.Rule::registryName,Function.identity()));
        assertEquals(16,rules.size(),analysis.diagnostics().toString());

        for(String name:new String[]{"thickSakuraPillar","thickOrcPillar","thickSprucePillar","thickBirchPillar"}){
            var r=rules.get(name);assertNotNull(r,name);assertTrue(r.axisLocked());assertTrue(r.sameMetadataOnly());
            assertEquals(.3,r.minWidth(),1e-6);assertEquals(.7,r.maxWidth(),1e-6);
            assertEquals(.2,r.minHeight(),1e-6);assertEquals(.8,r.maxHeight(),1e-6);
            assertTrue(r.connectWood());assertFalse(r.connectFullBlocks());assertEquals("empty",r.collision());
        }
        for(String name:new String[]{"thinSakuraPillar","thinOrcPillar","thinSprucePillar","thinBirchPillar"}){
            var r=rules.get(name);assertNotNull(r,name);assertTrue(r.axisLocked());assertTrue(r.sameMetadataOnly());
            assertEquals(.4,r.minWidth(),1e-6);assertEquals(.6,r.maxWidth(),1e-6);
            assertEquals(.15,r.minHeight(),1e-6);assertEquals(.85,r.maxHeight(),1e-6);
            assertEquals("empty",r.collision());
        }
        for(String name:new String[]{"bambooLiangThick","bambooLiangVLogThick","bambooLiangVLog2Thick","bambooLiangVWoodThick"}){
            var r=rules.get(name);assertNotNull(r,name);assertFalse(r.axisLocked());assertFalse(r.sameMetadataOnly());
            assertEquals(.15,r.minWidth(),1e-6);assertEquals(.85,r.maxWidth(),1e-6);
            assertEquals(.15,r.minHeight(),1e-6);assertEquals(.85,r.maxHeight(),1e-6);
            assertTrue(r.connectFullBlocks());assertTrue(r.connectWood());assertTrue(r.connectRock());assertEquals("full",r.collision());
        }
        for(String name:new String[]{"bambooLiangThin","bambooLiangVLogThin","bambooLiangVLog2Thin","bambooLiangVWoodThin"}){
            var r=rules.get(name);assertNotNull(r,name);assertFalse(r.axisLocked());assertFalse(r.sameMetadataOnly());
            assertEquals(.3,r.minWidth(),1e-6);assertEquals(.7,r.maxWidth(),1e-6);
            assertEquals(.3,r.minHeight(),1e-6);assertEquals(.7,r.maxHeight(),1e-6);
            assertEquals("full",r.collision());
        }
    }
}
