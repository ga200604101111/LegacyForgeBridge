package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Tag("exact-corpus")
class BambooInertModelPresentationExactTest {
    private static final String BAMBOO_SHA256="bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";
    @Test void exactAndonStaticTesrGeometryIsSourceProven()throws Exception{
        String input=System.getProperty("lfb.exactCorpus.jar");assertNotNull(input);Path source=Path.of(input);assertTrue(Files.isRegularFile(source));assertEquals(BAMBOO_SHA256,Hashing.sha256(source));
        var topology=new LegacyInertModelBlockAnalyzer().analyze(source);var rule=topology.rules().stream().filter(v->v.sourceBlockClass().equals("ruby/bamboo/block/BlockAndon")).findFirst().orElseThrow();
        var analysis=new LegacyInertModelPresentationAnalyzer().analyze(source,rule);assertTrue(analysis.diagnostics().isEmpty(),analysis.diagnostics().toString());var p=analysis.presentation().orElseThrow();
        assertEquals("ruby/bamboo/render/tileentity/RenderAndon",p.sourceRendererClass());assertEquals("ruby/bamboo/render/tileentity/ModelAndon",p.sourceModelClass());assertEquals("bamboo:textures/entitys/andon.png",p.texture());assertEquals(128,p.imageWidth());assertEquals(64,p.imageHeight());assertEquals(64,p.modelTextureWidth());assertEquals(32,p.modelTextureHeight());assertEquals(7,p.cuboids().size());assertEquals(.0625F,p.modelScale());assertEquals(.5F,p.translateX());assertEquals(0F,p.translateY());assertEquals(.5F,p.translateZ());assertEquals(3,p.metadataMask());assertEquals(90F,p.yawDegreesPerMeta());assertTrue(p.inventoryTransformProven());assertEquals(-.7F,p.inventoryTranslateY());assertEquals(1.3F,p.inventoryScale());
        var body=p.cuboids().stream().filter(c->c.field().equals("box")).findFirst().orElseThrow();assertEquals(0,body.u());assertEquals(0,body.v());assertEquals(-3F,body.x());assertEquals(0F,body.y());assertEquals(-3F,body.z());assertEquals(6,body.width());assertEquals(10,body.height());assertEquals(6,body.depth());assertEquals(4F,body.pivotY());
    }
}
