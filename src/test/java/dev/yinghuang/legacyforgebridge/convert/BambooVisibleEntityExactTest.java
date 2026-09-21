package dev.yinghuang.legacyforgebridge.convert;

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
        assertEquals(1,door.parts().size());assertEquals(16,door.parts().getFirst().width());assertEquals(32,door.parts().getFirst().height());assertEquals(2,door.parts().getFirst().depth());

        var cushion=rules.get("Zabuton");assertNotNull(cushion);assertEquals(LegacyVisibleEntityPresentationAnalyzer.Adapter.TINTED_CUSHION,cushion.adapter());
        assertEquals(19,cushion.legacyNumericId());assertEquals(1F,cushion.width());assertEquals(.125F,cushion.height());
        assertEquals(16,cushion.watcherIndices().get("color"));assertEquals(16,cushion.palette().size());assertEquals(0xFFFFFF,cushion.palette().get(15));
        assertEquals("bamboo:textures/entitys/zabuton.png",cushion.fixedTexture());assertEquals(14,cushion.parts().getFirst().width());assertEquals(2,cushion.parts().getFirst().height());

        var tray=rules.get("Obon");assertNotNull(tray);assertEquals(LegacyVisibleEntityPresentationAnalyzer.Adapter.TRAY_ITEMS,tray.adapter());
        assertEquals(12,tray.legacyNumericId());assertEquals(1F,tray.width());assertEquals(.25F,tray.height());
        assertEquals(17,tray.itemWatcherBase());assertEquals(5,tray.itemWatcherCount());assertEquals(9,tray.parts().size());
        for(int index=17;index<=21;index++)assertEquals(5,tray.watcherTypes().get(index),index+" ItemStack watcher");
        assertEquals("bamboo:textures/entitys/obon.png",tray.fixedTexture());

        var thrown=rules.get("ThrowZabuton");assertNotNull(thrown);
        assertEquals(LegacyVisibleEntityPresentationAnalyzer.Adapter.TINTED_CUSHION,thrown.adapter());
    }
}
