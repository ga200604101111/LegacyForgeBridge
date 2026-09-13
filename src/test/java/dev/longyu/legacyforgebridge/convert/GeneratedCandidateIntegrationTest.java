package dev.longyu.legacyforgebridge.convert;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.longyu.legacyforgebridge.convert.api.ConversionResult;
import dev.longyu.legacyforgebridge.convert.api.ConversionStatus;
import dev.longyu.legacyforgebridge.convert.pass.GeneratedModEntrypointPass;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedCandidateIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    void loaderSafeCandidateContainsItsOwnModernEntrypoints() throws Exception {
        Path source = tempDir.resolve("StandaloneLegacy.jar");
        String metadata = """
                [{"modid":"standalonelegacy","name":"Standalone Legacy","version":"1.0","mcversion":"1.7.10","dependencies":[]}]
                """;
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(source))) {
            output.putNextEntry(new JarEntry("mcmod.info"));
            output.write(metadata.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new JarEntry("assets/standalonelegacy/textures/item/example.png"));
            output.write(new byte[]{1, 2, 3});
            output.closeEntry();
        }

        ConversionResult result = new LegacyConversionEngine().convert(
                source,
                tempDir.resolve("converted"),
                tempDir.resolve("manifests")
        );
        assertEquals(ConversionStatus.CONVERTED, result.status());

        try (JarFile jar = new JarFile(result.candidateJar().orElseThrow().toFile())) {
            String generated = "dev/longyu/legacyforgebridge/generated/standalonelegacy/ConvertedModEntrypoint.class";
            assertNotNull(jar.getJarEntry(generated));
            assertNotNull(jar.getJarEntry(GeneratedModEntrypointPass.MARKER_PATH));

            JsonObject fabric;
            try (InputStreamReader reader = new InputStreamReader(
                    jar.getInputStream(jar.getJarEntry("fabric.mod.json")),
                    StandardCharsets.UTF_8
            )) {
                fabric = JsonParser.parseReader(reader).getAsJsonObject();
            }
            String binary = "dev.longyu.legacyforgebridge.generated.standalonelegacy.ConvertedModEntrypoint";
            assertEquals(binary, fabric.getAsJsonObject("entrypoints").getAsJsonArray("main").get(0).getAsString());
            assertEquals(binary, fabric.getAsJsonObject("entrypoints").getAsJsonArray("client").get(0).getAsString());
            assertTrue(fabric.getAsJsonObject("custom").getAsJsonObject("legacyforgebridge")
                    .get("runnableWrapper").getAsBoolean());
        }
    }
}
