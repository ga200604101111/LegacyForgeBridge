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
import java.util.Set;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end exact-corpus guard for the presentation failures reproduced on a live 1.7.10 Bamboo
 * server: visible furniture spawn contracts and final custom block-model ownership.
 */
@Tag("exact-corpus")
class BambooLivePresentationExactTest {
    private static final String SHA="bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";
    @TempDir Path tempDir;

    @Test
    void exactCandidateOwnsDoorTrayShootBambooAndAllConnectedPillarsAndBeams() throws Exception {
        String input=System.getProperty("lfb.exactCorpus.jar");assertNotNull(input,"exact Bamboo corpus is required");
        Path source=Path.of(input);assertTrue(Files.isRegularFile(source));assertEquals(SHA,Hashing.sha256(source));

        var result=new LegacyConversionEngine().convert(source,tempDir.resolve("converted"),tempDir.resolve("manifests"));
        Path candidate=result.candidateJar().orElseThrow();
        try(JarFile jar=new JarFile(candidate.toFile())){
            assertParent(jar,"assets/bamboomod/models/block/blockbambooshoot.json","minecraft:block/cross");
            assertParent(jar,"assets/bamboomod/models/block/bamboosingle.json","minecraft:block/cross");
            assertParent(jar,"assets/bamboomod/models/block/bamboo2.json","minecraft:block/crop");

            JsonObject visible=read(jar,"legacyforgebridge/visible-entity-rules.json");
            assertEquals(4,visible.getAsJsonArray("rules").size(),visible.toString());
            JsonObject door=find(visible,"legacyRegistryName","Syouzi");
            assertEquals(2,door.get("legacyNumericId").getAsInt());
            JsonObject doorTypes=door.getAsJsonObject("watcherTypes");
            assertEquals(0,doorTypes.get("17").getAsInt());
            assertEquals(0,doorTypes.get("18").getAsInt());
            assertEquals(0,doorTypes.get("19").getAsInt());
            assertEquals(1,doorTypes.get("20").getAsInt());
            assertEquals(0,doorTypes.get("22").getAsInt());
            assertEquals(0,doorTypes.get("23").getAsInt());

            JsonObject tray=find(visible,"legacyRegistryName","Obon");
            assertEquals(12,tray.get("legacyNumericId").getAsInt());
            assertEquals(17,tray.get("itemWatcherBase").getAsInt());
            assertEquals(5,tray.get("itemWatcherCount").getAsInt());
            JsonObject trayTypes=tray.getAsJsonObject("watcherTypes");
            for(int index=17;index<=21;index++)assertEquals(5,trayTypes.get(String.valueOf(index)).getAsInt(),"tray watcher "+index);

            JsonObject geometry=read(jar,"legacyforgebridge/block-geometry.json");
            JsonObject blocks=geometry.getAsJsonObject("blocks");
            assertSimpleShape(blocks,"bamboomod:blockbambooshoot",new double[]{.3,0,.3,.7,.5,.7});
            assertSimpleShape(blocks,"bamboomod:bamboosingle",new double[]{.125,0,.125,.875,1,.875});
            assertSimpleShape(blocks,"bamboomod:bamboo2",new double[]{.125,0,.125,.875,1,.875});
            Set<String> connected=Set.of(
                    "thicksakurapillar","thickorcpillar","thicksprucepillar","thickbirchpillar",
                    "thinsakurapillar","thinorcpillar","thinsprucepillar","thinbirchpillar",
                    "bambooliangthick","bambooliangvlogthick","bambooliangvlog2thick","bambooliangvwoodthick",
                    "bambooliangthin","bambooliangvlogthin","bambooliangvlog2thin","bambooliangvwoodthin");
            for(String path:connected){
                String id="bamboomod:"+path;assertTrue(blocks.has(id),id+" missing connected geometry");
                assertEquals("connected_cuboid",blocks.getAsJsonObject(id).get("family").getAsString(),id);
                JsonObject model=read(jar,"assets/bamboomod/models/block/"+path+".json");
                assertFalse(model.has("parent")&&"minecraft:block/magenta_glazed_terracotta".equals(model.get("parent").getAsString()),id);
                assertTrue(model.has("elements"),id+" final connected model has no cuboid elements");
            }
        }
    }

    private static void assertSimpleShape(JsonObject blocks,String id,double[] expected){
        assertTrue(blocks.has(id),id+" missing simple geometry");
        JsonObject rule=blocks.getAsJsonObject(id);assertEquals("box",rule.get("family").getAsString(),id);
        JsonObject variant=rule.getAsJsonObject("variants").getAsJsonObject("0");
        assertEquals("empty",variant.get("collision").getAsString(),id);
        var bounds=variant.getAsJsonArray("bounds");assertEquals(6,bounds.size(),id);
        for(int i=0;i<6;i++)assertEquals(expected[i],bounds.get(i).getAsDouble(),0.0001,id+" bound "+i);
    }

    private static void assertParent(JarFile jar,String path,String expected)throws Exception{
        JsonObject model=read(jar,path);assertEquals(expected,model.get("parent").getAsString(),path);
    }
    private static JsonObject find(JsonObject root,String key,String value){
        for(JsonElement element:root.getAsJsonArray("rules")){
            JsonObject object=element.getAsJsonObject();
            if(object.has(key)&&value.equals(object.get(key).getAsString()))return object;
        }
        fail("Missing rule "+key+"="+value);return null;
    }
    private static JsonObject read(JarFile jar,String path)throws Exception{
        var entry=jar.getJarEntry(path);assertNotNull(entry,"Missing candidate output "+path);
        try(InputStreamReader reader=new InputStreamReader(jar.getInputStream(entry),StandardCharsets.UTF_8)){
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
