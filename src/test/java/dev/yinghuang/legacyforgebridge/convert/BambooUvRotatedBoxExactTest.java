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
class BambooUvRotatedBoxExactTest {
    private static final String SHA="bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";
    @TempDir Path tempDir;

    @Test
    void exactBroomProvesMetadataQuarterTurnRendererAndOwnsRotatedWorldModels() throws Exception {
        String input=System.getProperty("lfb.exactCorpus.jar");assertNotNull(input,"exact Bamboo corpus is required");
        Path source=Path.of(input);assertTrue(Files.isRegularFile(source));assertEquals(SHA,Hashing.sha256(source));

        var uv=new LegacyUvRotatedBoxAnalyzer().analyze(source).rules().stream()
                .filter(rule->rule.registryName().equals("blobkbroom")).findFirst().orElseThrow();
        assertEquals(2,uv.metadataShift());
        assertEquals(1,uv.quarterTurns(4));
        assertEquals(2,uv.quarterTurns(8));
        assertEquals(3,uv.quarterTurns(12));

        var result=new LegacyConversionEngine().convert(source,tempDir.resolve("converted"),tempDir.resolve("manifests"));
        try(JarFile jar=new JarFile(result.candidateJar().orElseThrow().toFile())){
            JsonObject geometry=read(jar,"legacyforgebridge/block-geometry.json");
            JsonObject broom=geometry.getAsJsonObject("blocks").getAsJsonObject("bamboomod:blobkbroom");
            assertNotNull(broom,geometry.toString());assertEquals("box",broom.get("family").getAsString());

            assertRotation(read(jar,"assets/bamboomod/models/block/lfb_geometry/blobkbroom/4.json"),90);
            assertRotation(read(jar,"assets/bamboomod/models/block/lfb_geometry/blobkbroom/8.json"),180);
            assertRotation(read(jar,"assets/bamboomod/models/block/lfb_geometry/blobkbroom/12.json"),270);
        }
    }

    private static void assertRotation(JsonObject model,int expected){
        var elements=model.getAsJsonArray("elements");assertFalse(elements.isEmpty(),model.toString());
        for(var raw:elements){
            JsonObject faces=raw.getAsJsonObject().getAsJsonObject("faces");
            for(var entry:faces.entrySet())assertEquals(expected,entry.getValue().getAsJsonObject().get("rotation").getAsInt(),entry.getKey());
        }
    }
    private static JsonObject read(JarFile jar,String path)throws Exception{
        var entry=jar.getJarEntry(path);assertNotNull(entry,"Missing candidate output "+path);
        try(InputStreamReader reader=new InputStreamReader(jar.getInputStream(entry),StandardCharsets.UTF_8)){
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
