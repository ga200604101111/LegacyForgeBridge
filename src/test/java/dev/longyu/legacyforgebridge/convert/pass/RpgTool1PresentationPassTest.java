package dev.longyu.legacyforgebridge.convert.pass;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.longyu.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.longyu.legacyforgebridge.convert.api.ConversionContext;
import dev.longyu.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.longyu.legacyforgebridge.convert.api.LegacyModMetadata;
import dev.longyu.legacyforgebridge.convert.profile.RpgTool1Profile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RpgTool1PresentationPassTest {
    @TempDir
    Path tempDir;

    @Test
    void emitsGenericCreativeTabAndEquipmentRenderDefinitions() throws Exception {
        Path staging = tempDir.resolve("staging");
        Path manifest = staging.resolve("legacyforgebridge/converted-content.json");
        Files.createDirectories(manifest.getParent());

        JsonObject root = new JsonObject();
        JsonArray items = new JsonArray();
        items.add(item("rpgtool1:dark_sword", "sword"));
        items.add(item("rpgtool1:wing01", "wing"));
        items.add(item("rpgtool1:buff2_3", "circle"));
        root.add("items", items);
        Files.writeString(manifest, root.toString(), StandardCharsets.UTF_8);

        RpgTool1PresentationPass pass = new RpgTool1PresentationPass();
        pass.apply(context(staging));

        JsonObject converted;
        try (Reader reader = Files.newBufferedReader(manifest, StandardCharsets.UTF_8)) {
            converted = JsonParser.parseReader(reader).getAsJsonObject();
        }

        JsonObject tab = converted.getAsJsonArray("creativeTabs").get(0).getAsJsonObject();
        assertEquals("rpgtool1:main", tab.get("id").getAsString());
        assertEquals("RPGTool1", tab.get("title").getAsString());
        assertEquals("rpgtool1:dark_sword", tab.get("icon").getAsString());
        assertEquals(3, tab.getAsJsonArray("items").size());
        for (var element : converted.getAsJsonArray("items")) {
            assertEquals("rpgtool1:main", element.getAsJsonObject().get("creativeTab").getAsString());
        }

        JsonObject wingRender = converted.getAsJsonArray("items").get(1).getAsJsonObject()
                .getAsJsonObject("equipmentRender");
        assertNotNull(wingRender);
        assertEquals(2, wingRender.getAsJsonArray("parts").size());
        assertEquals(
                "rpgtool1:textures/wings/left_wing01.obj",
                wingRender.getAsJsonArray("parts").get(0).getAsJsonObject().get("model").getAsString()
        );

        JsonObject circleRender = converted.getAsJsonArray("items").get(2).getAsJsonObject()
                .getAsJsonObject("equipmentRender");
        assertNotNull(circleRender);
        assertEquals(
                "rpgtool1:textures/circle/buff2.obj",
                circleRender.getAsJsonArray("parts").get(0).getAsJsonObject().get("model").getAsString()
        );
    }

    private static JsonObject item(String id, String kind) {
        JsonObject item = new JsonObject();
        item.addProperty("id", id);
        item.addProperty("kind", kind);
        return item;
    }

    private ConversionContext context(Path staging) {
        LegacyModMetadata metadata = new LegacyModMetadata(
                "RPGTool1-1.1-1.7.10.jar",
                "mcmod.info",
                List.of(new LegacyModMetadata.ModEntry("rpgtool1", "RPGTool1", "1.0", "1.7.10", List.of()))
        );
        LegacyJarAnalyzer.Analysis analysis = new LegacyJarAnalyzer.Analysis(
                "RPGTool1-1.1-1.7.10.jar",
                53,
                0,
                true,
                true,
                29,
                104,
                0,
                1,
                Set.of("cpw/mods/fml/common/Mod"),
                Set.of(),
                Set.of("org/lwjgl/opengl/GL11")
        );
        return new ConversionContext(
                tempDir.resolve("RPGTool1-1.1-1.7.10.jar"),
                staging,
                tempDir.resolve("candidate.jar"),
                RpgTool1Profile.CORPUS_SHA256,
                14_556_748L,
                metadata,
                analysis,
                new DiagnosticCollector(),
                "rpgtool1-1.7.10"
        );
    }
}
