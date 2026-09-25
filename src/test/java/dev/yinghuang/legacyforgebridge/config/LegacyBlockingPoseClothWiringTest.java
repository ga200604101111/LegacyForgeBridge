package dev.yinghuang.legacyforgebridge.config;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class LegacyBlockingPoseClothWiringTest {
    @Test void clothApiAndScreenCompileOnTheRuntimeClasspath() throws Exception {
        assertNotNull(Class.forName("me.shedaniel.clothconfig2.api.ConfigBuilder"));
        assertNotNull(LegacyBlockingPoseConfigScreen.class.getDeclaredMethod(
                "create", net.minecraft.client.gui.screens.Screen.class));
    }

    @Test void metadataRequiresClothAndPublishesOptionalModMenuEntry() throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("fabric.mod.json")) {
            assertNotNull(input);
            var root = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(root.getAsJsonObject("depends").has("cloth-config"));
            assertEquals("dev.yinghuang.legacyforgebridge.config.LegacyForgeBridgeModMenu",
                    root.getAsJsonObject("entrypoints").getAsJsonArray("modmenu").get(0).getAsString());
        }
    }
}
