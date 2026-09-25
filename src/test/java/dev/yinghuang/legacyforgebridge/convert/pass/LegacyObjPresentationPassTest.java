package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyObjPresentationPassTest {
    @TempDir
    Path tempDir;

    @Test
    void fractionalAlphaMarksItemAndWearableObjPresentationTranslucent() throws Exception {
        Path staging = tempDir.resolve("staging");
        Path texture = staging.resolve("assets/example/textures/model.png");
        Files.createDirectories(texture.getParent());
        BufferedImage image = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFFFFFFFF);
        image.setRGB(1, 0, 0x80FFFFFF);
        ImageIO.write(image, "png", texture.toFile());

        Path itemFile = staging.resolve("assets/example/items/model.json");
        Files.createDirectories(itemFile.getParent());
        JsonObject special = new JsonObject();
        special.addProperty("type", "legacyforgebridge:obj");
        special.addProperty("model", "example:textures/model.obj");
        special.addProperty("texture", "example:textures/model.png");
        JsonObject model = new JsonObject();
        model.addProperty("type", "minecraft:special");
        model.add("model", special);
        JsonObject itemRoot = new JsonObject();
        itemRoot.add("model", model);
        Files.writeString(itemFile, itemRoot.toString(), StandardCharsets.UTF_8);

        JsonObject part = new JsonObject();
        part.addProperty("model", "example:textures/model.obj");
        JsonArray textures = new JsonArray();
        textures.add("example:textures/model.png");
        part.add("textures", textures);
        JsonArray parts = new JsonArray();
        parts.add(part);
        JsonObject equipment = new JsonObject();
        equipment.add("parts", parts);
        JsonObject contentItem = new JsonObject();
        contentItem.addProperty("id", "example:model");
        contentItem.add("equipmentRender", equipment);
        JsonArray contentItems = new JsonArray();
        contentItems.add(contentItem);
        JsonObject content = new JsonObject();
        content.add("items", contentItems);
        Path manifest = staging.resolve("legacyforgebridge/converted-content.json");
        Files.createDirectories(manifest.getParent());
        Files.writeString(manifest, content.toString(), StandardCharsets.UTF_8);

        new LegacyObjPresentationPass().apply(context(staging));

        try (Reader reader = Files.newBufferedReader(itemFile, StandardCharsets.UTF_8)) {
            JsonObject converted = JsonParser.parseReader(reader).getAsJsonObject();
            assertTrue(converted.getAsJsonObject("model").getAsJsonObject("model")
                    .get("translucent").getAsBoolean());
        }
        try (Reader reader = Files.newBufferedReader(manifest, StandardCharsets.UTF_8)) {
            JsonObject converted = JsonParser.parseReader(reader).getAsJsonObject();
            assertTrue(converted.getAsJsonArray("items").get(0).getAsJsonObject()
                    .getAsJsonObject("equipmentRender").get("translucent").getAsBoolean());
        }
    }

    private ConversionContext context(Path staging) {
        LegacyModMetadata metadata = new LegacyModMetadata(
                "ExampleLegacy.jar",
                "mcmod.info",
                List.of(new LegacyModMetadata.ModEntry("example", "Example", "1.0", "1.7.10", List.of()))
        );
        LegacyJarAnalyzer.Analysis analysis = new LegacyJarAnalyzer.Analysis(
                "ExampleLegacy.jar", 0, 0, true, true, 0, 0, 0, 0,
                Set.of(), Set.of(), Set.of()
        );
        return new ConversionContext(
                tempDir.resolve("ExampleLegacy.jar"), staging, tempDir.resolve("candidate.jar"),
                "abc", 1L, metadata, analysis, new DiagnosticCollector(), "test"
        );
    }
}
