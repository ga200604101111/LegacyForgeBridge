package dev.yinghuang.legacyforgebridge.convert;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

@Tag("exact-corpus")
class BambooRadialTesrExactTest {
    @TempDir Path tempDir;
    private static final String SHA="bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";

    @Test
    void exactCampfireProvesBaseAndAllMetadataSelectedWorldGroups()throws Exception{
        String input=System.getProperty("lfb.exactCorpus.jar");assertNotNull(input);
        Path source=Path.of(input);assertTrue(Files.isRegularFile(source));assertEquals(SHA,Hashing.sha256(source));

        var analysis=new LegacyRadialTesrAnalyzer().analyze(source);
        var base=analysis.rules().stream().filter(value->value.sourceBlockClass().equals("ruby/bamboo/block/BlockCampfire"))
                .findFirst().orElseThrow(()->new AssertionError("Campfire radial proof missing; skipped="+analysis.skipped()));
        assertEquals("campfire",base.registryName());
        assertEquals("ruby/bamboo/tileentity/TileEntityCampfire",base.sourceTileClass());
        assertEquals("ruby/bamboo/render/tileentity/RenderCampfire",base.sourceRendererClass());
        assertEquals("ruby/bamboo/render/tileentity/ModelCampfire",base.sourceModelClass());
        assertEquals("bamboo:textures/entitys/campfire.png",base.texture());
        assertEquals(64,base.textureWidth());assertEquals(32,base.textureHeight());
        assertEquals(7,base.poses().size());assertEquals(44,base.cuboid().u());assertEquals(22,base.cuboid().v());
        assertEquals(2,base.cuboid().width());assertEquals(2,base.cuboid().height());assertEquals(8,base.cuboid().depth());
        assertEquals(3,base.metadataMask());assertEquals(90F,base.yawDegreesPerMeta(),0.0001F);
        assertEquals(13,base.modernLightEmission());
        assertEquals(-0.25F,base.inventoryTranslateY(),0.0001F);
        assertEquals(1.68F,base.inventoryScale(),0.0001F);
        assertEquals(5,base.conditionalModelCalls());

        var conditionalAnalysis=new LegacyRadialConditionalAnalyzer().analyze(source,analysis.rules());
        var conditional=conditionalAnalysis.rules().stream()
                .filter(value->value.sourceBlockClass().equals("ruby/bamboo/block/BlockCampfire"))
                .findFirst().orElseThrow(()->new AssertionError("Campfire conditional proof missing; skipped="+conditionalAnalysis.skipped()));
        assertEquals(2,conditional.metadataShift());assertEquals(3,conditional.selectorMask());assertEquals(5,conditional.coveredModelCalls());
        assertEquals(java.util.List.of(1,2,3),conditional.groups().stream().map(LegacyRadialConditionalAnalyzer.Group::selectorValue).toList());

        var fish=conditional.groups().stream().filter(group->group.selectorValue()==1).findFirst().orElseThrow();
        assertEquals(1,fish.parts().size());var fishPart=fish.parts().getFirst();
        assertNull(fishPart.animation());assertEquals(0,fishPart.cuboid().u());assertEquals(17,fishPart.cuboid().v());
        assertEquals(3,fishPart.cuboid().width());assertEquals(13,fishPart.cuboid().height());assertEquals(0,fishPart.cuboid().depth());
        assertEquals(10F,fishPart.cuboid().pivotY(),0.0001F);assertEquals(-0.34906584F,fishPart.poses().getFirst().xRot(),0.0001F);
        assertEquals(4,fishPart.poses().size());
        assertEquals((float)(Math.PI/4D),fishPart.poses().get(0).yRot(),0.0001F);
        assertEquals((float)(3D*Math.PI/4D),fishPart.poses().get(1).yRot(),0.0001F);

        var meat=conditional.groups().stream().filter(group->group.selectorValue()==2).findFirst().orElseThrow();
        assertEquals(4,meat.parts().size());
        var rotating=meat.parts().stream().filter(part->part.animation()!=null).toList();
        assertEquals(2,rotating.size(),"Meat and its child bone must share the source-proven rotation");
        for(var part:rotating){
            assertEquals(LegacyRadialConditionalAnalyzer.Axis.X,part.animation().axis());
            assertEquals(1F,part.animation().degreesPerTick(),0.0001F);
            assertEquals(360,part.animation().periodTicks());assertTrue(part.animation().randomizedPhase());
            assertEquals(11F,part.cuboid().pivotY(),0.0001F);
        }
        var meatBody=rotating.stream().filter(part->part.cuboid().width()==10).findFirst().orElseThrow();
        assertEquals(6,meatBody.cuboid().height());assertEquals(6,meatBody.cuboid().depth());
        var bone=rotating.stream().filter(part->part.cuboid().width()==16).findFirst().orElseThrow();
        assertEquals(1,bone.cuboid().height());assertEquals(1,bone.cuboid().depth());

        var pot=conditional.groups().stream().filter(group->group.selectorValue()==3).findFirst().orElseThrow();
        assertEquals(4,pot.parts().size());
        var potBody=pot.parts().stream().filter(part->part.cuboid().width()==10&&part.cuboid().depth()==10).findFirst().orElseThrow();
        assertEquals(8,potBody.cuboid().height());

        var converted=new LegacyConversionEngine().convert(source,tempDir.resolve("converted"),tempDir.resolve("manifests"));
        try(java.util.jar.JarFile jar=new java.util.jar.JarFile(converted.candidateJar().orElseThrow().toFile())){
            JsonObject item=read(jar,"assets/bamboomod/items/campfire.json");
            JsonObject model=item.getAsJsonObject("model");
            assertEquals("minecraft:special",model.get("type").getAsString());
            JsonObject special=model.getAsJsonObject("model");
            assertEquals("legacyforgebridge:radial_model",special.get("type").getAsString());
            assertEquals(-0.25F,special.get("translate_y").getAsFloat(),0.0001F);
            assertEquals(1.68F,special.get("scale").getAsFloat(),0.0001F);
            assertEquals(7,special.getAsJsonArray("poses").size(),"Inventory/held source path must stay wood-base only");

            JsonObject sidecar=read(jar,"legacyforgebridge/radial-tesr-conditional-rules.json");
            JsonObject emitted=null;
            for(JsonElement element:sidecar.getAsJsonArray("rules")){
                JsonObject candidate=element.getAsJsonObject();
                if(candidate.get("id").getAsString().equals("bamboomod:campfire")){emitted=candidate;break;}
            }
            assertNotNull(emitted);assertTrue(emitted.get("conditionalPresentationRuntimeComplete").getAsBoolean());
            assertEquals(2,emitted.get("metadataShift").getAsInt());assertEquals(3,emitted.get("selectorMask").getAsInt());
            assertEquals(5,emitted.get("coveredModelCalls").getAsInt());assertEquals(3,emitted.getAsJsonArray("groups").size());
        }
    }

    private static JsonObject read(java.util.jar.JarFile jar,String path)throws Exception{
        var entry=jar.getJarEntry(path);assertNotNull(entry,"Missing candidate output "+path);
        try(InputStreamReader reader=new InputStreamReader(jar.getInputStream(entry),StandardCharsets.UTF_8)){
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
