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
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

@Tag("exact-corpus")
class BambooInertModelRuntimeExactTest {
    private static final String BAMBOO_SHA256="bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";
    @TempDir Path tempDir;
    @Test void exactAndonCandidateEmitsBoundedWorldAndInventoryRuntime()throws Exception{
        String input=System.getProperty("lfb.exactCorpus.jar");assertNotNull(input);Path source=Path.of(input);assertTrue(Files.isRegularFile(source));assertEquals(BAMBOO_SHA256,Hashing.sha256(source));
        var result=new LegacyConversionEngine().convert(source,tempDir.resolve("converted"),tempDir.resolve("manifests"));Path candidate=result.candidateJar().orElseThrow();
        try(JarFile jar=new JarFile(candidate.toFile())){
            JsonObject root=read(jar,"legacyforgebridge/inert-model-block-rules.json");JsonObject andon=null;for(var element:root.getAsJsonArray("rules")){JsonObject value=element.getAsJsonObject();if("ruby/bamboo/block/BlockAndon".equals(value.get("sourceBlockClass").getAsString())){andon=value;break;}}assertNotNull(andon);
            assertTrue(andon.get("topologyProofComplete").getAsBoolean());assertTrue(andon.get("presentationProofComplete").getAsBoolean());assertTrue(andon.get("worldPresentationRuntimeComplete").getAsBoolean());assertTrue(andon.get("inventoryPresentationRuntimeComplete").getAsBoolean());assertTrue(andon.get("staticPresentationComplete").getAsBoolean());assertTrue(andon.get("runtimeComplete").getAsBoolean());assertFalse(andon.get("sourcePresentationComplete").getAsBoolean());
            JsonObject presentation=andon.getAsJsonObject("presentation");assertEquals("bamboo:textures/entitys/andon.png",presentation.get("texture").getAsString());assertEquals(7,presentation.getAsJsonArray("cuboids").size());assertEquals(64,presentation.get("modelTextureWidth").getAsInt());assertEquals(32,presentation.get("modelTextureHeight").getAsInt());assertEquals(1.3F,presentation.get("inventoryScale").getAsFloat());
            String id=andon.get("id").getAsString();int colon=id.indexOf(':');String ns=id.substring(0,colon),path=id.substring(colon+1);JsonObject item=read(jar,"assets/"+ns+"/items/"+path+".json");JsonObject model=item.getAsJsonObject("model");assertEquals("minecraft:special",model.get("type").getAsString());JsonObject special=model.getAsJsonObject("model");assertEquals("legacyforgebridge:inert_model",special.get("type").getAsString());assertEquals("bamboo:textures/entitys/andon.png",special.get("texture").getAsString());assertEquals(7,special.getAsJsonArray("cuboids").size());assertEquals(0F,special.get("translate_x").getAsFloat());assertEquals(-.7F,special.get("translate_y").getAsFloat());assertEquals(0F,special.get("translate_z").getAsFloat());assertEquals(1.3F,special.get("scale").getAsFloat());
        }
    }
    private static JsonObject read(JarFile jar,String path)throws Exception{var entry=jar.getJarEntry(path);assertNotNull(entry,"Missing "+path);try(InputStreamReader reader=new InputStreamReader(jar.getInputStream(entry),StandardCharsets.UTF_8)){return JsonParser.parseReader(reader).getAsJsonObject();}}
}
