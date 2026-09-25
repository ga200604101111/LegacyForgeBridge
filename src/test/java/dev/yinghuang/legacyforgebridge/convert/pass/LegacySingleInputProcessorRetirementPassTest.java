package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static dev.yinghuang.legacyforgebridge.convert.pass.ProcessorRetirementTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class LegacySingleInputProcessorRetirementPassTest {
    @TempDir Path tempDir;

    @Test
    void atomicallyDeletesCompleteCohort() throws Exception {
        Path source = emptyJar(tempDir.resolve("source.jar"));
        Path staging = tempDir.resolve("staging");
        writeCandidate(staging, false);
        writeReadiness(staging, List.of(HELPER));
        new LegacySingleInputProcessorRetirementPass().apply(
                context(source, staging, tempDir));

        JsonObject root = result(staging);
        assertEquals(1, root.get("retirementAuthorizedCohorts").getAsInt());
        assertEquals(5, root.get("deletedSourceClasses").getAsInt());
        assertEquals(0, root.get("blockedRetirementCohorts").getAsInt());
        JsonObject rule = root.getAsJsonArray("rules").get(0).getAsJsonObject();
        assertTrue(rule.get("retirementComplete").getAsBoolean(), rule.toString());
        assertEquals(Set.of(BLOCK, TILE, CONTAINER, SCREEN, HELPER),
                strings(rule.getAsJsonArray("retirementCohortClasses")));
        assertTrue(rule.getAsJsonArray("blockers").isEmpty(), rule.toString());
        for (String target : List.of(BLOCK, TILE, CONTAINER, SCREEN, HELPER))
            assertFalse(Files.exists(classPath(staging, target)), target);
        assertTrue(Files.isRegularFile(classPath(staging, BOOTSTRAP)));
    }

    @Test
    void preservesAllClassesWhenNestedSetDrifts() throws Exception {
        Path source = emptyJar(tempDir.resolve("source.jar"));
        Path staging = tempDir.resolve("staging");
        writeCandidate(staging, false);
        writeReadiness(staging, List.of(HELPER));
        String late = TILE + "$Late";
        writeMinimalClass(staging, late);
        new LegacySingleInputProcessorRetirementPass().apply(
                context(source, staging, tempDir));

        JsonObject rule = result(staging).getAsJsonArray("rules").get(0).getAsJsonObject();
        assertFalse(rule.get("retirementComplete").getAsBoolean(), rule.toString());
        assertTrue(strings(rule.getAsJsonArray("blockers"))
                .contains("nested-companion-set-changed-after-readiness"));
        for (String target : List.of(BLOCK, TILE, CONTAINER, SCREEN, HELPER, late))
            assertTrue(Files.isRegularFile(classPath(staging, target)), target);
    }

    @Test
    void preservesAllClassesForFreshExternalReference() throws Exception {
        Path source = emptyJar(tempDir.resolve("source.jar"));
        Path staging = tempDir.resolve("staging");
        writeCandidate(staging, true);
        writeReadiness(staging, List.of(HELPER));
        new LegacySingleInputProcessorRetirementPass().apply(
                context(source, staging, tempDir));

        JsonObject rule = result(staging).getAsJsonArray("rules").get(0).getAsJsonObject();
        assertFalse(rule.get("retirementComplete").getAsBoolean(), rule.toString());
        assertTrue(strings(rule.getAsJsonArray("blockers")).contains(
                "fresh-predelete-incoming-reference:" + BLOCK + "<-" + BOOTSTRAP));
        for (String target : List.of(BLOCK, TILE, CONTAINER, SCREEN, HELPER, BOOTSTRAP))
            assertTrue(Files.isRegularFile(classPath(staging, target)), target);
    }
}
