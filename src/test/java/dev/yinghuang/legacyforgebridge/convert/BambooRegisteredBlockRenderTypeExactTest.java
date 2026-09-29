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
class BambooRegisteredBlockRenderTypeExactTest {
    private static final String BAMBOO_SHA256="bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";

    @Test
    void exactBambooResolvesConstructorBoundCoordinateCrossRenderIdentities() throws Exception {
        String input=System.getProperty("lfb.exactCorpus.jar");assertNotNull(input,"exact Bamboo corpus is required");
        Path source=Path.of(input);assertTrue(Files.isRegularFile(source));assertEquals(BAMBOO_SHA256,Hashing.sha256(source));
        var analysis=new LegacyRegisteredBlockRenderTypeAnalyzer().analyze(source);
        Map<String,LegacyRegisteredBlockRenderTypeAnalyzer.Rule> byName=analysis.rules().stream()
                .collect(Collectors.toMap(LegacyRegisteredBlockRenderTypeAnalyzer.Rule::registryName,Function.identity(),(a,b)->a));
        var bamboo=byName.get("bamboo");assertNotNull(bamboo,"delegated bamboo render identity");
        assertTrue(bamboo.renderIdentity().isConstant(6),"BambooMod:bamboo must retain source render type 6/CROP");
        for(String name:new String[]{"bamboosingle","bamboo2"}){
            var rule=byName.get(name);assertNotNull(rule,name+" render identity");
            assertEquals("ruby/bamboo/CustomRenderHandler",rule.renderIdentity().fieldOwner(),name);
            assertEquals("coordinateCrossUID",rule.renderIdentity().fieldName(),name);
            assertNull(rule.renderIdentity().constant(),name);
        }
    }

    @Test
    void exactBambooProvesSimpleCrossCropRendererFamilies() throws Exception {
        String input=System.getProperty("lfb.exactCorpus.jar");assertNotNull(input,"exact Bamboo corpus is required");
        Path source=Path.of(input);assertTrue(Files.isRegularFile(source));assertEquals(BAMBOO_SHA256,Hashing.sha256(source));
        var analysis=new LegacySimpleBlockRendererAnalyzer().analyze(source);
        Map<String,LegacySimpleBlockRendererAnalyzer.Rule> byName=analysis.rules().stream()
                .collect(Collectors.toMap(LegacySimpleBlockRendererAnalyzer.Rule::registryName,Function.identity(),(a,b)->a));
        var shoot=byName.get("blockbambooshoot");assertEquals(LegacySimpleBlockRendererAnalyzer.Mode.CROSS,shoot.mode());
        assertTrue(shoot.flatInventory(),"Bamboo shoot render-id handler explicitly disables 3D inventory rendering");
        assertEquals(LegacySimpleBlockRendererAnalyzer.RenderOffset.XYZ,shoot.renderOffset());
        assertNotNull(shoot.bounds());assertEquals(.3F,shoot.bounds().minX(),0.0001F);assertEquals(.5F,shoot.bounds().maxY(),0.0001F);
        assertEquals(.7F,shoot.bounds().maxZ(),0.0001F);assertTrue(shoot.emptyCollision());
        var single=byName.get("bamboosingle");assertEquals(LegacySimpleBlockRendererAnalyzer.Mode.CROSS,single.mode());
        assertEquals(LegacySimpleBlockRendererAnalyzer.RenderOffset.XYZ,single.renderOffset());
        assertNotNull(single.bounds());assertEquals(.125F,single.bounds().minX(),0.0001F);assertEquals(.875F,single.bounds().maxZ(),0.0001F);assertTrue(single.emptyCollision());
        var bamboo2=byName.get("bamboo2");assertEquals(LegacySimpleBlockRendererAnalyzer.Mode.CROP,bamboo2.mode());
        assertEquals(LegacySimpleBlockRendererAnalyzer.RenderOffset.XYZ,bamboo2.renderOffset());
        assertNotNull(bamboo2.bounds());assertEquals(.125F,bamboo2.bounds().minX(),0.0001F);assertEquals(.875F,bamboo2.bounds().maxX(),0.0001F);assertTrue(bamboo2.emptyCollision());
        assertEquals(LegacySimpleBlockRendererAnalyzer.Mode.META_ZERO_CROP_ELSE_STANDARD,byName.get("singleTexDeco").mode());
        assertEquals(LegacySimpleBlockRendererAnalyzer.Mode.HELD_ITEM_CROSS,byName.get("kitunebi").mode());
    }

    @Test
    void exactBambooProvesForgeInventory2dScopeInsteadOfTextureNameHeuristics() throws Exception {
        String input=System.getProperty("lfb.exactCorpus.jar");assertNotNull(input,"exact Bamboo corpus is required");
        Path source=Path.of(input);assertTrue(Files.isRegularFile(source));assertEquals(BAMBOO_SHA256,Hashing.sha256(source));
        var analysis=new LegacyBlockInventoryRenderModeAnalyzer().analyze(source);
        Map<String,LegacyBlockInventoryRenderModeAnalyzer.Mode> byName=analysis.rules().stream()
                .collect(Collectors.toMap(LegacyBlockInventoryRenderModeAnalyzer.Rule::registryName,
                        LegacyBlockInventoryRenderModeAnalyzer.Rule::mode,(a,b)->a));
        assertEquals(LegacyBlockInventoryRenderModeAnalyzer.Mode.FLAT_2D,byName.get("bambooPanel"));
        assertEquals(LegacyBlockInventoryRenderModeAnalyzer.Mode.FLAT_2D,byName.get("bamboosingle"));
        assertEquals(LegacyBlockInventoryRenderModeAnalyzer.Mode.THREE_D,byName.get("andon"));
        assertEquals(LegacyBlockInventoryRenderModeAnalyzer.Mode.THREE_D,byName.get("campfire"));
    }

    @Test
    void exactBambooProvesHeldOwnBlockItemFoxfireVisibility() throws Exception {
        String input=System.getProperty("lfb.exactCorpus.jar");assertNotNull(input,"exact Bamboo corpus is required");
        Path source=Path.of(input);assertTrue(Files.isRegularFile(source));assertEquals(BAMBOO_SHA256,Hashing.sha256(source));
        var rules=new LegacyHeldItemVisibilityAnalyzer().analyze(source).rules();
        var rule=rules.stream().filter(value->value.registryName().equals("kitunebi")).findFirst().orElseThrow();
        assertEquals(8,rule.visibleOrMask());assertEquals(7,rule.hiddenAndMask());
    }
}
