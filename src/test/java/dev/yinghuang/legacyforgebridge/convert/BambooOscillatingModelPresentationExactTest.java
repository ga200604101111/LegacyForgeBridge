package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Tag("exact-corpus")
class BambooOscillatingModelPresentationExactTest {
    private static final String BAMBOO_SHA256="bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";

    @Test
    void exactManekiWorldPresentationIsSourceProvenWithoutInventoryOverclaim() throws Exception {
        String input=System.getProperty("lfb.exactCorpus.jar");
        assertNotNull(input,"Run exactCorpusTest with the checksum-pinned Bamboo 2.6.8.5 JAR");
        Path source=Path.of(input);assertTrue(Files.isRegularFile(source));assertEquals(BAMBOO_SHA256,Hashing.sha256(source));
        var topology=new LegacyOscillatingModelBlockAnalyzer().analyze(source);
        var rule=topology.rules().stream().filter(value->value.sourceBlockClass().equals("ruby/bamboo/block/BlockManeki")).findFirst().orElseThrow();
        var analysis=new LegacyOscillatingModelPresentationAnalyzer().analyze(source,rule);
        assertTrue(analysis.diagnostics().isEmpty(),analysis.diagnostics().toString());
        var p=analysis.presentation().orElseThrow();
        assertEquals("ruby/bamboo/render/tileentity/RenderManeki",p.sourceRendererClass());
        assertEquals("MManeki",p.clientTileId());
        assertEquals("ruby/bamboo/render/tileentity/ModelManeki",p.sourceModelClass());
        assertEquals("bamboo:textures/entitys/maneki.png",p.texture());
        assertEquals(64,p.imageWidth());assertEquals(32,p.imageHeight());
        assertEquals(64,p.modelTextureWidth());assertEquals(32,p.modelTextureHeight());
        assertEquals(4,p.parts().size());assertEquals("hand",p.animatedPartField());
        assertEquals(.0625F,p.modelScale());
        assertEquals(.5F,p.translateX());assertEquals(.5F,p.translateY());assertEquals(.5F,p.translateZ());
        assertEquals(3,p.metadataMask());assertEquals(90F,p.yawDegreesPerMeta());assertEquals(180F,p.yawOffsetDegrees());
        assertTrue(p.whiteColor());assertTrue(p.dynamicAngleUsesDegrees());assertFalse(p.inventoryPresentationProven());

        var body=p.parts().stream().filter(part->part.field().equals("body")).findFirst().orElseThrow();
        assertEquals(0,body.u());assertEquals(0,body.v());assertEquals(-3.5F,body.x());assertEquals(-8F,body.y());assertEquals(-3.5F,body.z());assertEquals(7,body.width());assertEquals(12,body.height());assertEquals(7,body.depth());assertEquals(0F,body.pivotX());assertEquals(-4F,body.pivotY());assertEquals(0F,body.pivotZ());assertFalse(body.mirror());assertEquals(0F,body.baseXRot());assertEquals(0F,body.baseYRot());assertEquals((float)Math.PI,body.baseZRot());assertFalse(body.animated());
        var left=p.parts().stream().filter(part->part.field().equals("earL")).findFirst().orElseThrow();assertTrue(left.mirror());assertEquals((float)Math.PI,left.baseZRot());assertFalse(left.animated());
        var hand=p.parts().stream().filter(part->part.field().equals("hand")).findFirst().orElseThrow();
        assertEquals(29,hand.u());assertEquals(8,hand.v());assertEquals(-1F,hand.x());assertEquals(-1F,hand.y());assertEquals(-1F,hand.z());assertEquals(2,hand.width());assertEquals(7,hand.height());assertEquals(2,hand.depth());assertEquals(4.5F,hand.pivotX());assertEquals(-1F,hand.pivotY());assertEquals(0F,hand.pivotZ());assertTrue(hand.mirror());assertEquals(0F,hand.baseXRot());assertEquals((float)Math.PI,hand.baseYRot());assertEquals(0F,hand.baseZRot());assertTrue(hand.animated());
    }
}
