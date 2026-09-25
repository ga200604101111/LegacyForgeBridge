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
class BambooGridPotNegativeRenderClassificationExactTest {
    private static final String BAMBOO_SHA256="bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";

    @Test
    void exactBambooPlantsExposeKnownNonInsertionRenderTypeSix() throws Exception {
        String input=System.getProperty("lfb.exactCorpus.jar");assertNotNull(input,"exact Bamboo corpus is required");
        Path source=Path.of(input);assertTrue(Files.isRegularFile(source));assertEquals(BAMBOO_SHA256,Hashing.sha256(source));
        var analysis=new LegacyRegisteredBlockRenderTypeAnalyzer().analyze(source);
        Map<String,LegacyRegisteredBlockRenderTypeAnalyzer.Rule> byName=analysis.rules().stream()
                .collect(Collectors.toMap(LegacyRegisteredBlockRenderTypeAnalyzer.Rule::registryName, Function.identity(),(a,b)->a));
        for(String name:new String[]{"ricePlant","beanPlant","tomatoPlant"}){
            var rule=byName.get(name);assertNotNull(rule,name+" render identity");
            assertTrue(rule.renderIdentity().isConstant(6),name+" should remain outside GridPot positive render set");
        }
    }
}
