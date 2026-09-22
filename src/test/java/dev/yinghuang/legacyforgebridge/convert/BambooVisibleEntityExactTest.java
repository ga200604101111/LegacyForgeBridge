package dev.yinghuang.legacyforgebridge.convert;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.compat.LegacyVisibleEntityRegistry;
import org.junit.jupiter.api.io.TempDir;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

@Tag("exact-corpus")
class BambooVisibleEntityExactTest {
    @TempDir Path tempDir;
    private static final String SHA="bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";

    @Test void exactVisibleFurnitureAndDoorRenderersAreSourceProven()throws Exception{
        String input=System.getProperty("lfb.exactCorpus.jar");assertNotNull(input,"exact Bamboo corpus is required");
        Path source=Path.of(input);assertTrue(Files.isRegularFile(source));assertEquals(SHA,Hashing.sha256(source));
        var analysis=new LegacyVisibleEntityPresentationAnalyzer().analyze(source);
        Map<String,LegacyVisibleEntityPresentationAnalyzer.Rule> rules=analysis.rules().stream()
                .collect(Collectors.toMap(LegacyVisibleEntityPresentationAnalyzer.Rule::registryName,Function.identity()));
        assertEquals(4,rules.size(),analysis.diagnostics().toString());

        var door=rules.get("Syouzi");assertNotNull(door);assertEquals(LegacyVisibleEntityPresentationAnalyzer.Adapter.SLIDE_PANEL,door.adapter());
        assertEquals(2,door.legacyNumericId());assertEquals(1F,door.width());assertEquals(2F,door.height());
        assertEquals(17,door.watcherIndices().get("direction"));assertEquals(18,door.watcherIndices().get("mirror"));assertEquals(20,door.watcherIndices().get("texture"));
        assertEquals(Map.of(17,0,18,0,19,0,20,1,22,0,23,0),door.watcherTypes());
        assertEquals(6,door.textureVariants().size());assertEquals("bamboo:textures/entitys/husuma.png",door.textureVariants().getFirst().texture());
        assertFalse(door.textureVariants().getFirst().translucent());assertTrue(door.textureVariants().get(2).translucent());
        assertTrue(door.physicalCollision());assertTrue(door.playerAttackRemoves());
        assertEquals(1,door.parts().size());assertEquals(16,door.parts().getFirst().width());assertEquals(32,door.parts().getFirst().height());assertEquals(2,door.parts().getFirst().depth());

        var cushion=rules.get("Zabuton");assertNotNull(cushion);assertEquals(LegacyVisibleEntityPresentationAnalyzer.Adapter.TINTED_CUSHION,cushion.adapter());
        assertEquals(19,cushion.legacyNumericId());assertEquals(1F,cushion.width());assertEquals(.125F,cushion.height());
        assertEquals(16,cushion.watcherIndices().get("color"));assertEquals(16,cushion.palette().size());assertEquals(0xFFFFFF,cushion.palette().get(15));
        assertEquals("bamboo:textures/entitys/zabuton.png",cushion.fixedTexture());assertTrue(cushion.physicalCollision());assertTrue(cushion.playerAttackRemoves());assertEquals(14,cushion.parts().getFirst().width());assertEquals(2,cushion.parts().getFirst().height());

        var tray=rules.get("Obon");assertNotNull(tray);assertEquals(LegacyVisibleEntityPresentationAnalyzer.Adapter.TRAY_ITEMS,tray.adapter());
        assertEquals(12,tray.legacyNumericId());assertEquals(1F,tray.width());assertEquals(.25F,tray.height());
        assertEquals(17,tray.itemWatcherBase());assertEquals(5,tray.itemWatcherCount());assertEquals(9,tray.parts().size());
        for(int index=17;index<=21;index++)assertEquals(5,tray.watcherTypes().get(index),index+" ItemStack watcher");
        assertEquals("bamboo:textures/entitys/obon.png",tray.fixedTexture());assertTrue(tray.physicalCollision());assertTrue(tray.playerAttackRemoves());

        var thrown=rules.get("ThrowZabuton");assertNotNull(thrown);
        assertEquals(LegacyVisibleEntityPresentationAnalyzer.Adapter.TINTED_CUSHION,thrown.adapter());
        assertFalse(thrown.physicalCollision());assertFalse(thrown.playerAttackRemoves());

        var converted=new LegacyConversionEngine().convert(source,tempDir.resolve("converted"),tempDir.resolve("manifests"));
        try(JarFile jar=new JarFile(converted.candidateJar().orElseThrow().toFile())){
            JsonObject sidecar=read(jar,"legacyforgebridge/visible-entity-rules.json");
            assertEquals(4,sidecar.getAsJsonArray("rules").size(),sidecar.toString());
            String legacyModId=sidecar.get("legacyModId").getAsString();
            for(JsonElement element:sidecar.getAsJsonArray("rules")){
                JsonObject value=element.getAsJsonObject();
                assertNotNull(LegacyVisibleEntityRegistry.validateCandidateRule(value,legacyModId),
                        value.get("legacyRegistryName").getAsString());
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
