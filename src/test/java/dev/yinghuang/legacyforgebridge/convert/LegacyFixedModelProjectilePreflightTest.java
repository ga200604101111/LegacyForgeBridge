package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Proof discovery never writes executable projectile rules or guesses renderer ownership. */
class LegacyFixedModelProjectilePreflightTest {
    private static final String ENTITY = "unrelated/throwable/Disc";
    @TempDir Path directory;

    @Test void sourceRegisteredThrowableAndUniqueRendererProduceEvidenceOnly() throws Exception {
        var result = new LegacyFixedModelProjectilePreflight().inspect(jar("good", false, false, false),
                List.of(entity()), List.of(renderer()));
        assertEquals(1, result.candidates().size());
        var candidate = result.candidates().getFirst();
        assertEquals("foreign_disc", candidate.registryName());
        assertEquals(ENTITY, candidate.entityClass());
        assertEquals(LegacyFixedModelProjectileFixture.RENDERER, candidate.rendererClass());
        assertEquals(4, candidate.geometry().cuboids().size());
        assertTrue(result.skipped().isEmpty());
    }

    @Test void missingOrAmbiguousRendererDoesNotGuess() throws Exception {
        Path jar = jar("renderer", false, false, false);
        var analyzer = new LegacyFixedModelProjectilePreflight();
        assertTrue(analyzer.inspect(jar, List.of(entity()), List.of()).candidates().isEmpty());
        assertTrue(analyzer.inspect(jar, List.of(entity()), List.of(renderer(), renderer())).candidates().isEmpty());
    }

    @Test void duplicateEntityRegistrationIsBlocked() throws Exception {
        var result = new LegacyFixedModelProjectilePreflight().inspect(jar("registration", false, false, false),
                List.of(entity(), entity()), List.of(renderer()));
        assertTrue(result.candidates().isEmpty());
        assertTrue(result.skipped().getFirst().reason().contains("not unique"));
    }

    @Test void foreignAdditionalSpawnEnvelopeIsNotAdmitted() throws Exception {
        var result = new LegacyFixedModelProjectilePreflight().inspect(jar("spawn-data", true, false, false),
                List.of(entity()), List.of(renderer()));
        assertTrue(result.candidates().isEmpty());
        assertTrue(result.skipped().getFirst().reason().contains("additional-spawn-data"));
    }

    @Test void nonThrowableEntityIsNotMisclassified() throws Exception {
        var result = new LegacyFixedModelProjectilePreflight().inspect(jar("not-throwable", false, true, false),
                List.of(entity()), List.of(renderer()));
        assertTrue(result.candidates().isEmpty());
    }

    @Test void incompleteTextureCannotProduceEvidence() throws Exception {
        var result = new LegacyFixedModelProjectilePreflight().inspect(jar("texture", false, false, true),
                List.of(entity()), List.of(renderer()));
        assertTrue(result.candidates().isEmpty());
        assertTrue(result.skipped().getFirst().reason().contains("fixed cuboid family"));
    }

    @Test void evidenceRowsCannotInventUnregisteredEntities() throws Exception {
        var result = new LegacyFixedModelProjectilePreflight().inspect(jar("unregistered", false, false, false),
                List.of(), List.of(renderer()));
        assertTrue(result.candidates().isEmpty());
        assertTrue(result.skipped().isEmpty());
    }

    private static LegacyFixedModelProjectilePreflight.EntityRegistration entity() {
        return new LegacyFixedModelProjectilePreflight.EntityRegistration("foreign_disc", ENTITY);
    }
    private static LegacyFixedModelProjectilePreflight.RendererRegistration renderer() {
        return new LegacyFixedModelProjectilePreflight.RendererRegistration(ENTITY,
                LegacyFixedModelProjectileFixture.RENDERER);
    }

    private Path jar(String suffix, boolean additionalSpawn, boolean directEntity, boolean noTexture) throws Exception {
        Path base = LegacyFixedModelProjectileFixture.jar(directory.resolve(suffix + "-source.jar"),
                false, false, false, false, false, noTexture);
        Path target = directory.resolve(suffix + "-joined.jar");
        try (JarFile source = new JarFile(base.toFile());
             JarOutputStream output = new JarOutputStream(Files.newOutputStream(target))) {
            var entries = source.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                output.putNextEntry(new JarEntry(entry.getName()));
                try (var stream = source.getInputStream(entry)) { stream.transferTo(output); }
                output.closeEntry();
            }
            ClassWriter classWriter = new ClassWriter(0);
            classWriter.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, ENTITY, null,
                    directEntity ? "net/minecraft/entity/Entity" : "net/minecraft/entity/projectile/EntityThrowable",
                    additionalSpawn ? new String[]{"cpw/mods/fml/common/registry/IEntityAdditionalSpawnData"} : null);
            classWriter.visitEnd();
            output.putNextEntry(new JarEntry(ENTITY + ".class"));
            output.write(classWriter.toByteArray()); output.closeEntry();
        }
        return target;
    }
}
