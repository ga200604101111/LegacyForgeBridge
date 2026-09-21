package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Tag;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Tag("exact-corpus")
class BambooRadialTesrExactTest {
    @TempDir Path tempDir;
    private static final String SHA="bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";

    @Test
    void exactCampfireProvesUnconditionalSevenPartRadialBaseWithoutClaimingConditionalGroups()throws Exception{
        String input=System.getProperty("lfb.exactCorpus.jar");assertNotNull(input);
        Path source=Path.of(input);assertTrue(Files.isRegularFile(source));assertEquals(SHA,Hashing.sha256(source));
        var analysis=new LegacyRadialTesrAnalyzer().analyze(source);
        var rule=analysis.rules().stream().filter(value->value.sourceBlockClass().equals("ruby/bamboo/block/BlockCampfire"))
                .findFirst().orElseThrow(()->new AssertionError("Campfire radial proof missing; skipped="+analysis.skipped()));
        assertEquals("campfire",rule.registryName());
        assertEquals("ruby/bamboo/tileentity/TileEntityCampfire",rule.sourceTileClass());
        assertEquals("ruby/bamboo/render/tileentity/RenderCampfire",rule.sourceRendererClass());
        assertEquals("ruby/bamboo/render/tileentity/ModelCampfire",rule.sourceModelClass());
        assertEquals("bamboo:textures/entitys/campfire.png",rule.texture());
        assertEquals(64,rule.textureWidth());assertEquals(32,rule.textureHeight());
        assertEquals(7,rule.poses().size());assertEquals(44,rule.cuboid().u());assertEquals(22,rule.cuboid().v());
        assertEquals(2,rule.cuboid().width());assertEquals(2,rule.cuboid().height());assertEquals(8,rule.cuboid().depth());
        assertEquals(3,rule.metadataMask());assertEquals(90F,rule.yawDegreesPerMeta(),0.0001F);
        assertEquals(13,rule.modernLightEmission());
        assertEquals(-0.25F,rule.inventoryTranslateY(),0.0001F);
        assertEquals(1.68F,rule.inventoryScale(),0.0001F);
        assertTrue(rule.conditionalModelCalls()>0,"Fish/meat/pot metadata groups must remain explicit until separately adapted");

        var converted=new LegacyConversionEngine().convert(source,tempDir.resolve("converted"),tempDir.resolve("manifests"));
        try(java.util.jar.JarFile jar=new java.util.jar.JarFile(converted.candidateJar().orElseThrow().toFile())){
            JsonObject item=read(jar,"assets/bamboomod/items/campfire.json");
            JsonObject model=item.getAsJsonObject("model");
            assertEquals("minecraft:special",model.get("type").getAsString());
            JsonObject special=model.getAsJsonObject("model");
            assertEquals("legacyforgebridge:radial_model",special.get("type").getAsString());
            assertEquals(-0.25F,special.get("translate_y").getAsFloat(),0.0001F);
            assertEquals(1.68F,special.get("scale").getAsFloat(),0.0001F);
            assertEquals(7,special.getAsJsonArray("poses").size());
        }
    }

    private static JsonObject read(java.util.jar.JarFile jar,String path)throws Exception{
        var entry=jar.getJarEntry(path);assertNotNull(entry,"Missing candidate output "+path);
        try(InputStreamReader reader=new InputStreamReader(jar.getInputStream(entry),StandardCharsets.UTF_8)){
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
