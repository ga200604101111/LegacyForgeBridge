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
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

@Tag("exact-corpus")
class BambooHeldVisibilityExactTest {
    private static final String SHA="bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";
    @TempDir Path tempDir;

    @Test
    void exactFoxfireUsesHeldOwnBlockVisibilityWithCrossedVisibleModel()throws Exception{
        String input=System.getProperty("lfb.exactCorpus.jar");assertNotNull(input,"exact Bamboo corpus is required");
        Path source=Path.of(input);assertTrue(Files.isRegularFile(source));assertEquals(SHA,Hashing.sha256(source));

        var result=new LegacyConversionEngine().convert(source,tempDir.resolve("converted"),tempDir.resolve("manifests"));
        try(JarFile jar=new JarFile(result.candidateJar().orElseThrow().toFile())){
            JsonObject sidecar=read(jar,"legacyforgebridge/held-item-visibility.json");
            JsonObject rule=find(sidecar,"id","bamboomod:kitunebi");
            assertEquals(8,rule.get("visibleOrMask").getAsInt());
            assertEquals(7,rule.get("hiddenAndMask").getAsInt());
            assertTrue(rule.get("clientMetadataToggleRuntime").getAsBoolean());

            JsonObject visible=read(jar,"assets/bamboomod/models/block/kitunebi_lfb_visible_cross.json");
            assertEquals("minecraft:block/cross",visible.get("parent").getAsString());
            assertTrue(visible.getAsJsonObject("textures").has("cross"));

            JsonObject hidden=read(jar,"assets/bamboomod/models/block/kitunebi_lfb_hidden.json");
            assertEquals("minecraft:block/block",hidden.get("parent").getAsString());

            JsonObject states=read(jar,"assets/bamboomod/blockstates/kitunebi.json").getAsJsonObject("variants");
            assertEquals("bamboomod:block/kitunebi_lfb_hidden",
                    states.getAsJsonObject("legacy_meta=0").get("model").getAsString());
            assertEquals("bamboomod:block/kitunebi_lfb_visible_cross",
                    states.getAsJsonObject("legacy_meta=8").get("model").getAsString());
        }
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
