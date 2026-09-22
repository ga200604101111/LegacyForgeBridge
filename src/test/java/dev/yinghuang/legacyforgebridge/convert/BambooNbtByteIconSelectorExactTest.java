package dev.yinghuang.legacyforgebridge.convert;

import com.google.gson.JsonArray;
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
class BambooNbtByteIconSelectorExactTest {
    private static final String SHA="bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";
    @TempDir Path tempDir;

    @Test
    void exactBambooPickaxeUsesSourceNbtByteToSelectSevenRegisteredIcons()throws Exception{
        String input=System.getProperty("lfb.exactCorpus.jar");assertNotNull(input);
        Path source=Path.of(input);assertTrue(Files.isRegularFile(source));assertEquals(SHA,Hashing.sha256(source));

        var analysis=new LegacyNbtByteIconSelectorAnalyzer().analyze(source);
        var rule=analysis.rules().stream()
                .filter(value->"ruby/bamboo/item/ItemBambooPickaxe".equals(value.sourceClass()))
                .findFirst().orElseThrow(()->new AssertionError("Bamboo pickaxe NBT icon selector proof missing; skipped="+analysis.skipped()));
        assertEquals("bamboopickaxe",rule.registryName());
        assertEquals("iconNum",rule.nbtKey());
        assertEquals("bamboo:pickaxe_",rule.texturePrefix());
        assertEquals(7,rule.variants());assertEquals(0,rule.defaultIndex());

        var converted=new LegacyConversionEngine().convert(source,tempDir.resolve("converted"),tempDir.resolve("manifests"));
        try(JarFile jar=new JarFile(converted.candidateJar().orElseThrow().toFile())){
            JsonObject sidecar=read(jar,"legacyforgebridge/nbt-byte-icon-selectors.json");
            JsonObject emitted=null;
            for(JsonElement element:sidecar.getAsJsonArray("rules")){
                JsonObject candidate=element.getAsJsonObject();
                if("bamboomod:bamboopickaxe".equals(candidate.get("id").getAsString())){emitted=candidate;break;}
            }
            assertNotNull(emitted,sidecar.toString());
            assertEquals("iconNum",emitted.get("nbtKey").getAsString());
            assertEquals(7,emitted.get("variantCount").getAsInt());
            JsonArray itemModels=emitted.getAsJsonArray("itemModels");
            assertEquals(7,itemModels.size());
            assertTrue(emitted.get("runtimeComplete").getAsBoolean());

            JsonObject defaultItem=read(jar,"assets/bamboomod/items/bamboopickaxe.json");
            assertEquals("bamboomod:item/bamboopickaxe_lfb_nbt_0",
                    defaultItem.getAsJsonObject("model").get("model").getAsString());
            for(int i=0;i<7;i++){
                assertEquals("bamboomod:lfb_nbt/bamboopickaxe/"+i,itemModels.get(i).getAsString());
                JsonObject itemDefinition=read(jar,"assets/bamboomod/items/lfb_nbt/bamboopickaxe/"+i+".json");
                JsonObject boundaryModel=itemDefinition.getAsJsonObject("model");
                assertNotNull(boundaryModel,"variant "+i+" item boundary missing model node");
                assertEquals("minecraft:model",boundaryModel.get("type").getAsString());
                assertEquals("bamboomod:item/bamboopickaxe_lfb_nbt_"+i,boundaryModel.get("model").getAsString());

                JsonObject model=read(jar,"assets/bamboomod/models/item/bamboopickaxe_lfb_nbt_"+i+".json");
                assertEquals("minecraft:item/generated",model.get("parent").getAsString());
                assertTrue(model.getAsJsonObject("textures").get("layer0").getAsString().contains("pickaxe_"+i)
                                ||model.getAsJsonObject("textures").get("layer0").getAsString().contains("lfb_legacy"),
                        "variant "+i+" texture="+model);
            }
        }
    }

    private static JsonObject read(JarFile jar,String path)throws Exception{
        var entry=jar.getJarEntry(path);assertNotNull(entry,"Missing candidate output "+path);
        try(InputStreamReader reader=new InputStreamReader(jar.getInputStream(entry),StandardCharsets.UTF_8)){
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
