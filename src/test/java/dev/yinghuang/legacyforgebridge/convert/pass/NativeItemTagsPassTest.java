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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeItemTagsPassTest {
    @TempDir
    Path tempDir;

    @Test
    void neutralToolKindsBecomeNativeMinecraftTagsAcrossUnrelatedNamespaces() throws Exception {
        Path staging = tempDir.resolve("staging");
        Files.createDirectories(staging.resolve("legacyforgebridge"));
        Files.createDirectories(staging.resolve("data/minecraft/tags/item"));

        Files.writeString(
                staging.resolve("legacyforgebridge/converted-content.json"),
                """
                {
                  "items": [
                    {"id":"alchemy:moon_blade","kind":"sword"},
                    {"id":"astronomy:star_blade","kind":"sword"},
                    {"id":"alchemy:ore_hammer","kind":"axe"},
                    {"id":"alchemy:dust","kind":"item"}
                  ]
                }
                """,
                StandardCharsets.UTF_8
        );
        Files.writeString(
                staging.resolve("data/minecraft/tags/item/swords.json"),
                "{\"replace\":false,\"values\":[\"existing:blade\"]}",
                StandardCharsets.UTF_8
        );

        LegacyModMetadata metadata = new LegacyModMetadata(
                "fixture.jar",
                "test",
                List.of(new LegacyModMetadata.ModEntry("alchemy", "Alchemy", "1.0", "1.7.10", List.of()))
        );
        LegacyJarAnalyzer.Analysis analysis = new LegacyJarAnalyzer.Analysis(
                "fixture.jar", 0, 0, false, false,
                0, 0, 0, 0,
                Set.of(), Set.of(), Set.of()
        );
        ConversionContext context = new ConversionContext(
                tempDir.resolve("fixture.jar"),
                staging,
                tempDir.resolve("candidate.jar"),
                "sha",
                0L,
                metadata,
                analysis,
                new DiagnosticCollector(),
                "generic-test"
        );

        new NativeItemTagsPass().apply(context);

        JsonObject swords = JsonParser.parseString(Files.readString(
                staging.resolve("data/minecraft/tags/item/swords.json"),
                StandardCharsets.UTF_8
        )).getAsJsonObject();
        JsonArray swordValues = swords.getAsJsonArray("values");
        assertEquals(3, swordValues.size());
        assertTrue(contains(swordValues, "existing:blade"));
        assertTrue(contains(swordValues, "alchemy:moon_blade"));
        assertTrue(contains(swordValues, "astronomy:star_blade"));
        assertFalse(contains(swordValues, "alchemy:dust"));

        JsonObject axes = JsonParser.parseString(Files.readString(
                staging.resolve("data/minecraft/tags/item/axes.json"),
                StandardCharsets.UTF_8
        )).getAsJsonObject();
        assertTrue(contains(axes.getAsJsonArray("values"), "alchemy:ore_hammer"));
    }

    private static boolean contains(JsonArray array, String value) {
        for (var element : array) {
            if (element.isJsonPrimitive() && value.equals(element.getAsString())) {
                return true;
            }
        }
        return false;
    }
}
