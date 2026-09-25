package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RpgTool1ResourceFixPassTest {
    @TempDir
    Path tempDir;

    @Test
    void legacyItemTextureDirectoryAndModelReferencesBecomeModern() throws Exception {
        Path legacyTexture = tempDir.resolve("assets/rpgtool1/textures/items/dark_sword.png");
        Files.createDirectories(legacyTexture.getParent());
        Files.write(legacyTexture, new byte[]{1, 2, 3});

        Path model = tempDir.resolve("assets/rpgtool1/models/item/dark_sword_base.json");
        Files.createDirectories(model.getParent());
        Files.writeString(
                model,
                "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"rpgtool1:items/dark_sword\"}}",
                StandardCharsets.UTF_8
        );

        RpgTool1ResourceFixPass.ResourceFixResult result = RpgTool1ResourceFixPass.fix(tempDir);

        assertEquals(1, result.movedFiles());
        assertEquals(1, result.rewrittenJson());
        assertFalse(Files.exists(tempDir.resolve("assets/rpgtool1/textures/items")));
        assertTrue(Files.isRegularFile(tempDir.resolve("assets/rpgtool1/textures/item/dark_sword.png")));

        try (Reader reader = Files.newBufferedReader(model, StandardCharsets.UTF_8)) {
            JsonObject rewritten = JsonParser.parseReader(reader).getAsJsonObject();
            assertEquals(
                    "rpgtool1:item/dark_sword",
                    rewritten.getAsJsonObject("textures").get("layer0").getAsString()
            );
        }
    }
}
