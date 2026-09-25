package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversionStateStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void persistsProgressFingerprintManagedArtifactAndRestartState() throws Exception {
        ConversionStateStore state = ConversionStateStore.open(tempDir);
        state.beginLaunch("0.2.0-alpha.15");
        state.progress("RPGTool1-1.1-1.7.10.jar", 35, "ANALYZING", "Inspecting bytecode");
        state.complete(
                "RPGTool1-1.1-1.7.10.jar",
                new ConversionStateStore.Completion(
                        "0.2.0-alpha.15:abc",
                        "abc",
                        123L,
                        456L,
                        "rpgtool1-1.7.10",
                        "PARTIAL",
                        "rpgtool1",
                        "legacy-cache/converted/rpgtool.jar",
                        "candidate-sha",
                        "mods/legacyforgebridge-converted-rpgtool1.jar",
                        "candidate-sha",
                        true,
                        false,
                        false,
                        true,
                        "STAGED",
                        "Restart required"
                )
        );
        state.setRestartRequired(true);

        assertTrue(Files.isRegularFile(tempDir.resolve(ConversionStateStore.FILE_NAME)));

        ConversionStateStore reopened = ConversionStateStore.open(tempDir);
        ConversionStateStore.Snapshot snapshot = reopened.snapshot("RPGTool1-1.1-1.7.10.jar");
        assertEquals("abc", snapshot.sourceSha256());
        assertEquals("rpgtool1", snapshot.fabricId());
        assertEquals("candidate-sha", snapshot.managedSha256());
        assertEquals("STAGED", snapshot.phase());
        assertEquals(100, snapshot.percent());
        assertTrue(snapshot.loaderSafe());
        assertTrue(snapshot.restartRequired());
        assertTrue(reopened.restartRequired());

        reopened.remove("RPGTool1-1.1-1.7.10.jar");
        assertFalse(reopened.sourceFiles().contains("RPGTool1-1.1-1.7.10.jar"));
    }
}
