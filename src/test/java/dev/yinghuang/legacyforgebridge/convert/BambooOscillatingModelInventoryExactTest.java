package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Tag("exact-corpus")
class BambooOscillatingModelInventoryExactTest {
    private static final String BAMBOO_SHA256="bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";

    @Test
    void exactManekiInventoryRouteTargetsTheSameRenderer() throws Exception {
        String input=System.getProperty("lfb.exactCorpus.jar");assertNotNull(input);
        Path source=Path.of(input);assertTrue(Files.isRegularFile(source));assertEquals(BAMBOO_SHA256,Hashing.sha256(source));
        var topology=new LegacyOscillatingModelBlockAnalyzer().analyze(source);
        var rule=topology.rules().stream().filter(value->value.sourceBlockClass().equals("ruby/bamboo/block/BlockManeki")).findFirst().orElseThrow();
        var world=new LegacyOscillatingModelPresentationAnalyzer().analyze(source,rule).presentation().orElseThrow();
        var analysis=new LegacyOscillatingModelInventoryAnalyzer().analyze(source,rule,world);
        assertTrue(analysis.diagnostics().isEmpty(),analysis.diagnostics().toString());
        var proof=analysis.proof().orElseThrow();
        assertEquals("ruby/bamboo/CustomRenderHandler",proof.handlerClass());
        assertEquals("manekiUID",proof.uidField());
        assertEquals("ruby/bamboo/CustomRenderHandler$Render3DInInventory",proof.routeClass());
        assertEquals("customRenderInvMap",proof.inventoryMapField());
        assertEquals("ruby/bamboo/render/block/RenderInvManeki",proof.inventoryRendererClass());
        assertEquals("ruby/bamboo/render/tileentity/RenderManeki",proof.sourceRendererClass());
        assertEquals(180F,proof.yawDegrees());
        assertEquals(-.25F,proof.translateY());
        assertEquals(1F,proof.scale());
        assertEquals(0F,proof.dynamicAngleDegrees());
    }
}
