package dev.yinghuang.legacyforgebridge.convert;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacySingleInputProcessorPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacySingleInputProcessorRetirementPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacySingleInputProcessorRetirementReadiness;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

/** Exact-corpus guard for atomic MillStone processor source-cohort retirement. */
@Tag("exact-corpus")
class BambooExactProcessorRetirementTest {
    private static final String SHA =
            "bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";
    @TempDir Path tempDir;

    @Test
    void retiresMillStoneCohortAtomicallyOnlyWhenFreshProofCloses() throws Exception {
        String input = System.getProperty("lfb.exactCorpus.jar");
        assertNotNull(input, "exact Bamboo corpus is required");
        Path source = Path.of(input);
        assertTrue(Files.isRegularFile(source));
        assertEquals(SHA, Hashing.sha256(source));
        Path candidate = new LegacyConversionEngine().convert(
                source, tempDir.resolve("converted"), tempDir.resolve("manifests"))
                .candidateJar().orElseThrow();

        try (JarFile jar = new JarFile(candidate.toFile())) {
            JsonObject machine = read(jar, LegacySingleInputProcessorPass.OUTPUT)
                    .getAsJsonArray("machines").get(0).getAsJsonObject();
            assertTrue(machine.get("runtimeComplete").getAsBoolean(), machine.toString());
            String block = machine.get("sourceBlockClass").getAsString();
            String tile = machine.get("sourceTileClass").getAsString();
            JsonObject readiness = find(read(jar,
                    LegacySingleInputProcessorRetirementReadiness.OUTPUT), block);
            JsonObject retirementRoot = read(jar,
                    LegacySingleInputProcessorRetirementPass.OUTPUT);
            JsonObject retirement = find(retirementRoot, block);
            assertNotNull(readiness);
            assertNotNull(retirement);
            assertTrue(retirementRoot.get("sourceClassDeletionWired").getAsBoolean());
            assertTrue(retirementRoot.get("freshPreDeleteReferenceCheckWired").getAsBoolean());
            assertTrue(retirementRoot.get("freshPostDeleteReferenceCheckWired").getAsBoolean());
            assertTrue(retirementRoot.get("restoreOnPostDeleteFailureWired").getAsBoolean());

            boolean ready = readiness.get("retirementCohortCandidateReady").getAsBoolean();
            assertEquals(ready, retirement.get("retirementComplete").getAsBoolean(),
                    retirement.toString());
            assertFalse(retirement.get("restoredAfterFailedRetirement").getAsBoolean(),
                    retirement.toString());
            LinkedHashSet<String> cohort = new LinkedHashSet<>();
            cohort.add(block);
            cohort.add(tile);
            cohort.addAll(strings(readiness.getAsJsonArray("presentationSourceClasses")));
            cohort.addAll(strings(readiness.getAsJsonArray("nestedCompanionClasses")));
            assertEquals(cohort,
                    strings(retirement.getAsJsonArray("retirementCohortClasses")));
            assertEquals(ready ? cohort.size() : 0,
                    retirement.get("deletedSourceClassCount").getAsInt());
            for (String sourceClass : cohort) {
                if (ready) assertTrue(jar.getJarEntry(sourceClass + ".class") == null, sourceClass);
                else assertNotNull(jar.getJarEntry(sourceClass + ".class"), sourceClass);
            }
            JsonElement handler = readiness.get("guiHandlerClass");
            if (handler != null && handler.isJsonPrimitive())
                assertNotNull(jar.getJarEntry(handler.getAsString() + ".class"));
        }
    }

    private static JsonObject find(JsonObject root, String block) {
        for (JsonElement element : root.getAsJsonArray("rules")) {
            JsonObject value = element.getAsJsonObject();
            if (block.equals(value.get("sourceBlockClass").getAsString())) return value;
        }
        return null;
    }

    private static Set<String> strings(JsonArray values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values != null) values.forEach(value -> result.add(value.getAsString()));
        return result;
    }

    private static JsonObject read(JarFile jar, String path) throws Exception {
        var entry = jar.getJarEntry(path);
        assertNotNull(entry, path);
        try (var reader = new InputStreamReader(
                jar.getInputStream(entry), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
