package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Tag;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

@Tag("exact-corpus")
class BambooSpaUnitPresentationExactTest {
    @TempDir Path tempDir;
    private static final String SHA="bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";

    @Test
    void exactSpaUnitRetainsStandardContainerPresentationAndMetadataPistonIcons()throws Exception{
        String input=System.getProperty("lfb.exactCorpus.jar");assertNotNull(input);
        Path source=Path.of(input);assertTrue(Files.isRegularFile(source));assertEquals(SHA,Hashing.sha256(source));

        var registry=new LegacyRegistryAnalyzer().analyze(source);
        var registration=registry.blocks().stream()
                .filter(value->"ruby/bamboo/block/BlockSpaUnit".equals(value.implementationClass()))
                .findFirst().orElseThrow();
        var result=new LegacyIconTableAnalyzer().analyze(source,registry.registrations()).stream()
                .filter(value->value.registryName().equals(registration.registryName()))
                .findFirst().orElseThrow();

        assertEquals(16,result.variants().size(),result.limitation());
        var off=result.variants().get(0);
        var on=result.variants().get(8);
        assertEquals(0,off.renderType());assertEquals(0,on.renderType());
        assertEquals(6,off.faceIcons().size());assertEquals(6,on.faceIcons().size());
        assertEquals("minecraft:block/piston_top",off.faceIcons().get(1));
        assertEquals("minecraft:block/piston_inner",on.faceIcons().get(1));
        for(int side:new int[]{0,2,3,4,5}){
            assertEquals("minecraft:block/piston_top",off.faceIcons().get(side));
            assertEquals("minecraft:block/piston_top",on.faceIcons().get(side));
        }

        var converted=new LegacyConversionEngine().convert(source,tempDir.resolve("converted"),tempDir.resolve("manifests"));
        try(JarFile jar=new JarFile(converted.candidateJar().orElseThrow().toFile())){
            JsonObject content=read(jar,"legacyforgebridge/converted-content.json");
            JsonObject block=null;
            for(var element:content.getAsJsonArray("blocks")){
                JsonObject candidate=element.getAsJsonObject();
                if("ruby/bamboo/block/BlockSpaUnit".equals(candidate.has("sourceClass")?candidate.get("sourceClass").getAsString():"")){
                    block=candidate;break;
                }
            }
            assertNotNull(block,"SpaUnit missing from converted content");
            String id=block.get("id").getAsString();String[] split=id.split(":",2);assertEquals(2,split.length);
            JsonObject states=read(jar,"assets/"+split[0]+"/blockstates/"+split[1]+".json").getAsJsonObject("variants");
            String offModel=states.getAsJsonObject("legacy_meta=0").get("model").getAsString();
            String onModel=states.getAsJsonObject("legacy_meta=8").get("model").getAsString();
            assertNotEquals(offModel,onModel,"SpaUnit bit-8 visual states must not collapse to one model");
            JsonObject offJson=readModel(jar,offModel),onJson=readModel(jar,onModel);
            assertEquals("minecraft:block/piston_top",offJson.getAsJsonObject("textures").get("up").getAsString());
            assertEquals("minecraft:block/piston_inner",onJson.getAsJsonObject("textures").get("up").getAsString());
            for(String face:new String[]{"down","north","south","west","east"}){
                assertEquals("minecraft:block/piston_top",offJson.getAsJsonObject("textures").get(face).getAsString(),face);
                assertEquals("minecraft:block/piston_top",onJson.getAsJsonObject("textures").get(face).getAsString(),face);
            }
        }
    }

    private static JsonObject readModel(JarFile jar,String id)throws Exception{
        String[] split=id.split(":",2);assertEquals(2,split.length);
        return read(jar,"assets/"+split[0]+"/models/"+split[1]+".json");
    }
    private static JsonObject read(JarFile jar,String path)throws Exception{
        var entry=jar.getJarEntry(path);assertNotNull(entry,"Missing candidate output "+path);
        try(InputStreamReader reader=new InputStreamReader(jar.getInputStream(entry),StandardCharsets.UTF_8)){
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
