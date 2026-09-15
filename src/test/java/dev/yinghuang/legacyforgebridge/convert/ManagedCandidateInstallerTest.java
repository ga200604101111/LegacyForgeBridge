package dev.yinghuang.legacyforgebridge.convert;

import dev.yinghuang.legacyforgebridge.convert.pass.GeneratedModEntrypointPass;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagedCandidateInstallerTest {
    private static final String OUTPUT_NAME = "RPGTool1-1.1-1.7.10-lfb.jar";

    @TempDir
    Path tempDir;

    @Test
    void stagesWithSourceDerivedNameSkipsIdenticalAndReplacesChangedInactiveCandidate() throws Exception {
        Path mods = tempDir.resolve("mods");
        Path cache = tempDir.resolve("legacy-cache");
        ManagedCandidateInstaller installer = new ManagedCandidateInstaller(mods, cache, false);

        Path first = candidate(tempDir.resolve("a").resolve(OUTPUT_NAME), "one", false);
        assertTrue(installer.isLoaderSafeCandidate(first));

        ManagedCandidateInstaller.StageResult initial = installer.stage(first, "rpgtool1", false);
        assertTrue(initial.changed());
        assertTrue(initial.restartRequired());
        assertFalse(initial.pendingSwap());
        assertEquals(OUTPUT_NAME, initial.managedJar().getFileName().toString());
        assertTrue(Files.isRegularFile(initial.managedJar()));
        assertEquals(Hashing.sha256(first), Hashing.sha256(initial.managedJar()));

        ManagedCandidateInstaller.StageResult unchanged = installer.stage(first, "rpgtool1", false);
        assertFalse(unchanged.changed());
        assertFalse(unchanged.restartRequired());

        Path second = candidate(tempDir.resolve("b").resolve(OUTPUT_NAME), "two", false);
        ManagedCandidateInstaller.StageResult replaced = installer.stage(second, "rpgtool1", false);
        assertTrue(replaced.changed());
        assertFalse(replaced.pendingSwap());
        assertEquals(Hashing.sha256(second), Hashing.sha256(replaced.managedJar()));
    }

    @Test
    void deletedManagedModIsRestagedFromCachedCandidate() throws Exception {
        Path mods = tempDir.resolve("mods");
        Path cache = tempDir.resolve("legacy-cache");
        ManagedCandidateInstaller installer = new ManagedCandidateInstaller(mods, cache, false);
        Path candidate = candidate(tempDir.resolve("converted").resolve(OUTPUT_NAME), "one", false);

        ManagedCandidateInstaller.StageResult initial = installer.stage(candidate, "rpgtool1", false);
        Files.delete(initial.managedJar());
        assertFalse(Files.exists(initial.managedJar()));

        ManagedCandidateInstaller.StageResult repaired = installer.stage(candidate, "rpgtool1", false);
        assertTrue(repaired.changed());
        assertTrue(repaired.restartRequired());
        assertEquals(OUTPUT_NAME, repaired.managedJar().getFileName().toString());
        assertEquals(Hashing.sha256(candidate), Hashing.sha256(repaired.managedJar()));
    }

    @Test
    void loadedCandidateUpdateUsesPendingSwapInsteadOfMutatingLiveJar() throws Exception {
        Path mods = tempDir.resolve("mods");
        Path cache = tempDir.resolve("legacy-cache");
        ManagedCandidateInstaller installer = new ManagedCandidateInstaller(mods, cache, false);

        Path first = candidate(tempDir.resolve("a").resolve(OUTPUT_NAME), "one", false);
        Path second = candidate(tempDir.resolve("b").resolve(OUTPUT_NAME), "two", false);
        ManagedCandidateInstaller.StageResult initial = installer.stage(first, "rpgtool1", false);
        String liveHash = Hashing.sha256(initial.managedJar());

        ManagedCandidateInstaller.StageResult pending = installer.stage(second, "rpgtool1", true);
        assertTrue(pending.changed());
        assertTrue(pending.pendingSwap());
        assertTrue(pending.restartRequired());
        assertEquals(liveHash, Hashing.sha256(initial.managedJar()), "Loaded managed JAR must not be mutated in-place");
        assertTrue(Files.isRegularFile(
                cache.resolve("pending").resolve(initial.managedJar().getFileName() + ".pending.jar")
        ));
    }

    @Test
    void generatedModernWrapperIsAllowedButOriginalLegacyClassIsRejected() throws Exception {
        ManagedCandidateInstaller installer = new ManagedCandidateInstaller(
                tempDir.resolve("mods"),
                tempDir.resolve("legacy-cache"),
                false
        );
        Path safe = candidate(tempDir.resolve("safe").resolve(OUTPUT_NAME), "safe", false);
        Path unsafe = candidate(tempDir.resolve("unsafe").resolve(OUTPUT_NAME), "unsafe", true);
        assertTrue(installer.isLoaderSafeCandidate(safe));
        assertFalse(installer.isLoaderSafeCandidate(unsafe));
    }

    private static Path candidate(Path path, String marker, boolean withLegacyClass) throws Exception {
        Files.createDirectories(path.getParent());
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
            add(output, "fabric.mod.json", "{\"schemaVersion\":1,\"id\":\"rpgtool1\",\"version\":\"1.0\"}");
            add(output, "legacyforgebridge/converted-content.json", "{\"sourceSha256\":\"" + marker + "\",\"items\":[]}");
            add(output, GeneratedModEntrypointPass.MARKER_PATH, "dev.yinghuang.legacyforgebridge.generated.rpgtool1.ConvertedModEntrypoint\n");
            add(output, "dev/yinghuang/legacyforgebridge/generated/rpgtool1/ConvertedModEntrypoint.class", "generated-modern-bytecode");
            add(output, "assets/rpgtool1/test.txt", marker);
            if (withLegacyClass) {
                add(output, "legacy/Unsafe.class", "legacy-bytecode");
            }
        }
        return path;
    }

    private static void add(JarOutputStream output, String name, String value) throws Exception {
        output.putNextEntry(new JarEntry(name));
        output.write(value.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }
}
