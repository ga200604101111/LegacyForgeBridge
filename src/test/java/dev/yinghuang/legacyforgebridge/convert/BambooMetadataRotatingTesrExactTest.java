package dev.yinghuang.legacyforgebridge.convert;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Tag("exact-corpus")
class BambooMetadataRotatingTesrExactTest {
    @TempDir Path tempDir;
    private static final String SHA="bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";

    @Test
    void exactMillStoneUsesMetadataAsClientRotationSpeed()throws Exception{
        String input=System.getProperty("lfb.exactCorpus.jar");assertNotNull(input);
        Path source=Path.of(input);assertTrue(Files.isRegularFile(source));assertEquals(SHA,Hashing.sha256(source));

        var analysis=new LegacyMetadataRotatingTesrAnalyzer().analyze(source);
        var rule=analysis.rules().stream().filter(value->value.sourceBlockClass().equals("ruby/bamboo/block/BlockMillStone"))
                .findFirst().orElseThrow(()->new AssertionError("MillStone metadata-rotating proof missing; skipped="+analysis.skipped()));

        assertEquals("bambooMillStone",rule.registryName());
        assertEquals("ruby/bamboo/tileentity/TileEntityMillStone",rule.sourceTileClass());
        assertEquals("MillStone",rule.legacyTileId());
        assertEquals("ruby/bamboo/render/tileentity/RenderMillStone",rule.sourceRendererClass());
        assertEquals("ruby/bamboo/render/tileentity/ModelMillStone",rule.sourceModelClass());
        assertEquals("bamboo:textures/entitys/millstone.png",rule.texture());
        assertEquals(64,rule.textureWidth());assertEquals(64,rule.textureHeight());
        assertEquals(2,rule.cuboids().size());assertEquals("bottm",rule.animatedPart());
        var bottom=rule.cuboids().stream().filter(c->c.field().equals("bottm")).findFirst().orElseThrow();
        assertEquals(0,bottom.u());assertEquals(25,bottom.v());assertEquals(-8F,bottom.x(),0.0001F);
        assertEquals(0F,bottom.y(),0.0001F);assertEquals(-8F,bottom.z(),0.0001F);
        assertEquals(16,bottom.width());assertEquals(8,bottom.height());assertEquals(16,bottom.depth());assertTrue(bottom.mirror());
        var top=rule.cuboids().stream().filter(c->c.field().equals("top")).findFirst().orElseThrow();
        assertEquals(0,top.u());assertEquals(0,top.v());assertEquals(-8F,top.y(),0.0001F);assertTrue(top.mirror());
        assertEquals(.0625F,rule.modelScale(),0.0001F);
        assertEquals(.5F,rule.translateX(),0.0001F);assertEquals(.5F,rule.translateY(),0.0001F);assertEquals(.5F,rule.translateZ(),0.0001F);
        assertEquals(15,rule.metadataMask());assertEquals(1F,rule.degreesPerMetadataPerTick(),0.0001F);
        assertTrue(rule.inventoryStaticZeroAngle());

        var converted=new LegacyConversionEngine().convert(source,tempDir.resolve("converted"),tempDir.resolve("manifests"));
        assertNotEquals(dev.yinghuang.legacyforgebridge.convert.api.ConversionStatus.FAILED,converted.status(),converted.diagnostics().toString());
        try(java.util.jar.JarFile jar=new java.util.jar.JarFile(converted.candidateJar().orElseThrow().toFile())){
            JsonObject sidecar=read(jar,"legacyforgebridge/metadata-rotating-tesr-rules.json");
            JsonObject emitted=sidecar.getAsJsonArray("rules").get(0).getAsJsonObject();
            assertEquals("bamboomod:bamboomillstone",emitted.get("id").getAsString());
            assertTrue(emitted.get("runtimeComplete").getAsBoolean());
            assertEquals("bottm",emitted.get("animatedPart").getAsString());
            assertEquals(1F,emitted.get("degreesPerMetadataPerTick").getAsFloat(),0.0001F);

            JsonObject item=read(jar,"assets/bamboomod/items/bamboomillstone.json");
            JsonObject model=item.getAsJsonObject("model");assertEquals("minecraft:special",model.get("type").getAsString());
            assertEquals("legacyforgebridge:metadata_rotating_model",model.getAsJsonObject("model").get("type").getAsString());
        }
    }

    private static JsonObject read(java.util.jar.JarFile jar,String path)throws Exception{
        var entry=jar.getJarEntry(path);assertNotNull(entry,"Missing candidate output "+path);
        try(InputStreamReader reader=new InputStreamReader(jar.getInputStream(entry),StandardCharsets.UTF_8)){
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
