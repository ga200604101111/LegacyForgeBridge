package dev.yinghuang.legacyforgebridge.convert.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyModMetadataTest {
    @TempDir
    Path tempDir;

    @Test
    void readsAllLogicalModsAndPreservesEmbeddedVersion() throws Exception {
        Path jar = tempDir.resolve("Example-1.1.jar");
        String mcmod = """
                [
                  {
                    "modid": "Example.Mod",
                    "name": "Example Mod",
                    "version": "1.0",
                    "mcversion": "1.7.10",
                    "dependencies": ["required-after:Library@[2.0,)" ]
                  },
                  {
                    "modid": "ExampleAPI",
                    "name": "Example API",
                    "version": "1.0",
                    "mcversion": "1.7.10",
                    "dependencies": []
                  }
                ]
                """;
        writeJar(jar, mcmod);

        LegacyModMetadata metadata = LegacyModMetadata.read(jar);

        assertEquals("mcmod.info", metadata.metadataSource());
        assertEquals(2, metadata.mods().size());
        assertEquals("Example.Mod", metadata.primary().modId());
        assertEquals("1.0", metadata.primary().version());
        assertEquals("1.7.10", metadata.primary().mcVersion());
        assertEquals("required-after:Library@[2.0,)", metadata.primary().dependencies().getFirst());
        assertEquals("example_mod", metadata.fabricId());
        assertTrue(metadata.hasMultipleLogicalMods());
    }

    @Test
    void fallsBackToSafeFilenameIdentityWhenMetadataIsMissing() throws Exception {
        Path jar = tempDir.resolve("42 Strange Mod!.jar");
        writeJar(jar, null);

        LegacyModMetadata metadata = LegacyModMetadata.read(jar);

        assertEquals("filename-fallback", metadata.metadataSource());
        assertTrue(metadata.fabricId().startsWith("legacy_"));
        assertEquals("0.0.0+legacy", metadata.primary().version());
    }

    private static void writeJar(Path jar, String mcmod) throws IOException {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            if (mcmod != null) {
                output.putNextEntry(new JarEntry("mcmod.info"));
                output.write(mcmod.getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
    }
}
