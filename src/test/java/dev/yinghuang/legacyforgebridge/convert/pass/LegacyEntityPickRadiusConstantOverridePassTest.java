package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LegacyEntityPickRadiusConstantOverridePassTest {
    @TempDir Path tempDir;

    @Test void materializesExactCollisionBorderAsModernPickRadiusFloat() throws Exception {
        Path source = tempDir.resolve("source.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(source))) {
            put(out, "foreign/Orb.class", entity());
        }
        Path staging = tempDir.resolve("staging");
        Files.createDirectories(staging.resolve("legacyforgebridge"));
        Files.writeString(staging.resolve(LegacyEntityBehaviorSurfacePass.OUTPUT), """
                {
                  "schemaVersion":1,"sourceSha256":"sha","rules":[{
                    "id":"foreign:orb","legacyRegistryName":"orb","sourceClass":"foreign/Orb",
                    "callbacks":[{"kind":"COLLISION_BORDER_SIZE","owner":"foreign/Orb","method":"getCollisionBorderSize","descriptor":"()F"}]
                  }]
                }
                """, StandardCharsets.UTF_8);

        new LegacyEntityConstantOverridePass().apply(context(source, staging));

        JsonObject root = JsonParser.parseString(Files.readString(
                staging.resolve(LegacyEntityConstantOverridePass.OUTPUT), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject mapping = root.getAsJsonArray("rules").get(0).getAsJsonObject()
                .getAsJsonArray("constantOverrides").get(0).getAsJsonObject();
        assertEquals("COLLISION_BORDER_SIZE", mapping.get("sourceKind").getAsString());
        assertEquals("float", mapping.get("constantKind").getAsString());
        assertEquals(0.25F, mapping.get("constantFloat").getAsFloat());
        assertEquals("getPickRadius", mapping.get("targetMethod").getAsString());
        assertEquals("()F", mapping.get("targetDescriptor").getAsString());
        assertEquals("PICK_RADIUS_FLOAT_IDENTITY", mapping.get("mappingSemantics").getAsString());
        assertTrue(mapping.get("sourceConstantProofComplete").getAsBoolean());
        assertTrue(mapping.get("runtimeCodegenReady").getAsBoolean());
    }

    private ConversionContext context(Path source, Path staging) throws Exception {
        LegacyModMetadata metadata = new LegacyModMetadata("source.jar", "test",
                List.of(new LegacyModMetadata.ModEntry("foreign", "Foreign", "1.0", "1.7.10", List.of())));
        LegacyJarAnalyzer.Analysis analysis = new LegacyJarAnalyzer.Analysis("source.jar", 0, 0, false, false,
                0, 0, 0, 0, Set.of(), Set.of(), Set.of());
        return new ConversionContext(source, staging, tempDir.resolve("candidate.jar"), "sha",
                Files.size(source), metadata, analysis, new DiagnosticCollector(), "generic-test");
    }

    private static byte[] entity() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/Orb", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "getCollisionBorderSize", "()F", null, null);
        method.visitCode(); method.visitLdcInsn(0.25F); method.visitInsn(Opcodes.FRETURN); method.visitMaxs(0, 0); method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name)); out.write(bytes); out.closeEntry();
    }
}
