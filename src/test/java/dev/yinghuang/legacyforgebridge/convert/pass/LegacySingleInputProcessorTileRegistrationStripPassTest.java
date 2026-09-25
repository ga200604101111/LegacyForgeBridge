package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyCandidateReferenceAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacySingleInputProcessorTileRegistrationStripPassTest {
    @TempDir Path tempDir;

    @Test
    void stripsOnlyRuntimeCompleteProcessorTileRegistration() throws Exception {
        byte[] bootstrap = bootstrap();
        byte[] tile = tile();
        Path source = tempDir.resolve("source.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(source))) {
            put(out, "foreign/machine/Bootstrap.class", bootstrap);
            put(out, "foreign/machine/MachineTile.class", tile);
        }

        Path staging = tempDir.resolve("staging");
        writeClass(staging, "foreign/machine/Bootstrap", bootstrap);
        writeClass(staging, "foreign/machine/MachineTile", tile);
        Files.createDirectories(staging.resolve("legacyforgebridge"));
        Files.writeString(
                staging.resolve(LegacySingleInputProcessorPass.OUTPUT),
                """
                {
                  "schemaVersion": 4,
                  "sourceSha256": "sha",
                  "machines": [{
                    "id": "foreign:processor",
                    "sourceTileClass": "foreign/machine/MachineTile",
                    "legacyTileId": "processor_tile",
                    "baseRuntimeComplete": true,
                    "sourcePresentationComplete": true,
                    "runtimeComplete": true
                  }]
                }
                """,
                StandardCharsets.UTF_8);

        ConversionContext context = context(source, staging);
        new LegacySingleInputProcessorTileRegistrationStripPass().apply(context);

        Path output = staging.resolve(
                LegacySingleInputProcessorTileRegistrationStripPass.OUTPUT);
        assertTrue(Files.isRegularFile(output));
        JsonObject root = JsonParser.parseString(
                Files.readString(output, StandardCharsets.UTF_8)).getAsJsonObject();
        assertTrue(root.get("tileRegistrationStripWired").getAsBoolean());
        assertTrue(root.get("runtimeCompleteRequired").getAsBoolean());
        assertFalse(root.get("sourceClassDeletionWired").getAsBoolean());
        assertEquals(1, root.get("tileRegistrationStripCompleteRules").getAsInt());
        assertEquals(1, root.get("strippedTileRegistrationSites").getAsInt());
        assertEquals(0, root.get("blockedTileRegistrationStripRules").getAsInt());

        JsonObject rule = root.getAsJsonArray("rules").get(0).getAsJsonObject();
        assertTrue(rule.get("tileRegistrationStripComplete").getAsBoolean(),
                rule.toString());
        assertTrue(rule.getAsJsonArray("blockers").isEmpty(), rule.toString());
        assertEquals("foreign/machine/Bootstrap",
                rule.get("sourceOwner").getAsString());

        var refs = new LegacyCandidateReferenceAnalyzer().analyze(
                staging, Set.of("foreign/machine/MachineTile"));
        assertFalse(refs.forTarget("foreign/machine/MachineTile")
                .incomingClassReferences().contains("foreign/machine/Bootstrap"));
        assertTrue(Files.isRegularFile(
                staging.resolve("foreign/machine/MachineTile.class")));
    }

    private ConversionContext context(Path source, Path staging) throws Exception {
        LegacyModMetadata metadata = new LegacyModMetadata(
                "source.jar",
                "test",
                List.of(new LegacyModMetadata.ModEntry(
                        "foreign", "Foreign", "1.0", "1.7.10", List.of())));
        LegacyJarAnalyzer.Analysis analysis = new LegacyJarAnalyzer.Analysis(
                "source.jar", 0, 0, false, false,
                0, 0, 0, 0, Set.of(), Set.of(), Set.of());
        return new ConversionContext(
                source, staging, tempDir.resolve("candidate.jar"), "sha",
                Files.size(source), metadata, analysis,
                new DiagnosticCollector(), "generic-test");
    }

    private static byte[] bootstrap() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC,
                "foreign/machine/Bootstrap", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC,
                "boot",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V",
                null,
                null);
        AnnotationVisitor annotation = method.visitAnnotation(
                "Lcpw/mods/fml/common/Mod$EventHandler;", true);
        annotation.visitEnd();
        method.visitCode();
        method.visitLdcInsn(Type.getObjectType("foreign/machine/MachineTile"));
        method.visitLdcInsn("processor_tile");
        method.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "cpw/mods/fml/common/registry/GameRegistry",
                "registerTileEntity",
                "(Ljava/lang/Class;Ljava/lang/String;)V",
                false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] tile() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC,
                "foreign/machine/MachineTile", null,
                "net/minecraft/tileentity/TileEntity", null);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void writeClass(Path root, String name, byte[] bytes)
            throws Exception {
        Path path = root.resolve(name + ".class");
        Files.createDirectories(path.getParent());
        Files.write(path, bytes);
    }

    private static void put(JarOutputStream out, String name, byte[] bytes)
            throws Exception {
        out.putNextEntry(new JarEntry(name));
        out.write(bytes);
        out.closeEntry();
    }
}
