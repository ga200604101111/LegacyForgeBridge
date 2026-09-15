package dev.longyu.legacyforgebridge.convert;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exact-corpus guard for the presentation runtime boundary added after the base MillStone runtime. */
@Tag("exact-corpus")
class BambooExactProcessorPresentationRuntimeTest {
    private static final String BAMBOO_SHA256="bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";
    @TempDir Path tempDir;

    @Test
    void exactMillStoneGuiAndWorldPresentationAreEnabledWithoutOverclaimingFullPresentation() throws Exception {
        String input=System.getProperty("lfb.exactCorpus.jar");
        assertNotNull(input,"exact Bamboo corpus is required");
        Path source=Path.of(input);
        assertTrue(Files.isRegularFile(source));
        assertEquals(BAMBOO_SHA256,Hashing.sha256(source));

        var result=new LegacyConversionEngine().convert(source,tempDir.resolve("converted"),tempDir.resolve("manifests"));
        Path candidate=result.candidateJar().orElseThrow();
        try(JarFile jar=new JarFile(candidate.toFile())){
            JsonObject root=readJson(jar,"legacyforgebridge/single-input-processor-rules.json");
            assertEquals(1,root.get("presentationProofCompleteMachines").getAsInt());
            assertEquals(1,root.get("guiPresentationRuntimeCompleteMachines").getAsInt());
            assertEquals(1,root.get("worldPresentationRuntimeCompleteMachines").getAsInt());
            JsonObject machine=root.getAsJsonArray("machines").get(0).getAsJsonObject();
            assertTrue(machine.get("presentationProofComplete").getAsBoolean());
            assertTrue(machine.get("guiPresentationRuntimeComplete").getAsBoolean());
            assertTrue(machine.get("worldPresentationRuntimeComplete").getAsBoolean());
            assertTrue(machine.get("metadataRollRuntimeComplete").getAsBoolean());
            assertFalse(machine.get("inventoryPresentationRuntimeComplete").getAsBoolean());
            assertFalse(machine.get("particlePresentationRuntimeComplete").getAsBoolean());
            assertFalse(machine.get("sourcePresentationComplete").getAsBoolean());
            assertFalse(machine.get("runtimeComplete").getAsBoolean());

            JsonObject presentation=machine.getAsJsonObject("presentation");
            assertEquals("bamboo:textures/guis/guimillstone.png",presentation.get("guiTexture").getAsString());
            assertEquals(176,presentation.get("guiWidth").getAsInt());
            assertEquals(166,presentation.get("guiHeight").getAsInt());
            assertEquals("bamboo:textures/entitys/millstone.png",presentation.get("entityTexture").getAsString());
            assertEquals(64,presentation.get("modelTextureWidth").getAsInt());
            assertEquals(64,presentation.get("modelTextureHeight").getAsInt());
            assertTrue(presentation.get("metadataDrivesRoll").getAsBoolean());
            assertTrue(presentation.get("centeredAtBlock").getAsBoolean());
            assertEquals(0.0625F,presentation.get("renderScale").getAsFloat());

            JsonObject lower=presentation.getAsJsonObject("rotatingLower");
            assertEquals(0,lower.get("u").getAsInt());
            assertEquals(25,lower.get("v").getAsInt());
            assertEquals(-8F,lower.get("x").getAsFloat());
            assertEquals(0F,lower.get("y").getAsFloat());
            assertEquals(-8F,lower.get("z").getAsFloat());
            assertEquals(16,lower.get("width").getAsInt());
            assertEquals(8,lower.get("height").getAsInt());
            assertEquals(16,lower.get("depth").getAsInt());

            assertNotNull(jar.getJarEntry("assets/bamboo/textures/guis/guimillstone.png"));
            assertNotNull(jar.getJarEntry("assets/bamboo/textures/entitys/millstone.png"));
        }
    }

    private static JsonObject readJson(JarFile jar,String path)throws Exception{
        var entry=jar.getJarEntry(path);assertNotNull(entry,"Missing exact-corpus output "+path);
        try(InputStreamReader reader=new InputStreamReader(jar.getInputStream(entry),StandardCharsets.UTF_8)){
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
