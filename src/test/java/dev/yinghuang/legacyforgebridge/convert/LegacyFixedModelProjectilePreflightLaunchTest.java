package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Geometry is independent of launcher provenance; even two proofs do not enable runtime. */
class LegacyFixedModelProjectilePreflightLaunchTest {
    @TempDir Path directory;

    private LegacyFixedModelProjectilePreflight.Analysis inspect(LegacyProjectileLauncherFixture.Shape launch) throws Exception {
        Path geometry=LegacyFixedModelProjectileFixture.jar(directory.resolve("geom.jar"),
                false,false,false,false,false,false);
        Path source=LegacyProjectileLauncherFixture.jar(directory.resolve("launch.jar"),launch);
        Path joined=directory.resolve("combined.jar");
        try (JarOutputStream output=new JarOutputStream(Files.newOutputStream(joined))) {
            Set<String> copied=new HashSet<>();
            for (Path jar : List.of(geometry,source)) try (JarFile input=new JarFile(jar.toFile())) {
                var entries=input.entries();while(entries.hasMoreElements()) {
                    JarEntry entry=entries.nextElement();if(!copied.add(entry.getName()))continue;
                    output.putNextEntry(new JarEntry(entry.getName()));
                    try(var bytes=input.getInputStream(entry)){bytes.transferTo(output);}
                    output.closeEntry();
                }
            }
        }
        return new LegacyFixedModelProjectilePreflight().inspect(joined,
                List.of(new LegacyFixedModelProjectilePreflight.EntityRegistration(
                        "renamed_throwable",LegacyProjectileLauncherFixture.TARGET)),
                List.of(new LegacyFixedModelProjectilePreflight.RendererRegistration(
                        LegacyProjectileLauncherFixture.TARGET,LegacyFixedModelProjectileFixture.RENDERER)),
                List.of(new LegacyProjectileLauncherAnalyzer.ItemRegistration(
                        "renamed_queen",LegacyProjectileLauncherFixture.ITEM)));
    }

    @Test void rendererAndLaunchSourceProvenTogether() throws Exception {
        var result=inspect(LegacyProjectileLauncherFixture.Shape.RELEASE_DIRECT);
        assertEquals(1,result.candidates().size());
        var candidate=result.candidates().getFirst();
        assertEquals(4,candidate.geometry().cuboids().size());
        var launch=candidate.launcherProof().orElseThrow();
        assertEquals("renamed_queen",launch.registryName());
        assertEquals(LegacyProjectileLauncherAnalyzer.Callback.RELEASE_USE,launch.callback());
    }

    @Test void unprovedFactoryLaunchDoesNotSuppressGeometryPreflight() throws Exception {
        var result=inspect(LegacyProjectileLauncherFixture.Shape.FACTORY_RESULT);
        assertEquals(1,result.candidates().size());
        assertTrue(result.candidates().getFirst().launcherProof().isEmpty());
    }
}
