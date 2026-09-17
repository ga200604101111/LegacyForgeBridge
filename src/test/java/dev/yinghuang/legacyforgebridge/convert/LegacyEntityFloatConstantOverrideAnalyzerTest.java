package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LegacyEntityFloatConstantOverrideAnalyzerTest {
    @TempDir Path tempDir;

    @Test void provesFiniteFloatConstantAndRejectsFieldDependentBody() throws Exception {
        Path jar = tempDir.resolve("float.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/Orb.class", entity());
        }
        LegacyEntityConstantOverrideAnalyzer analyzer = new LegacyEntityConstantOverrideAnalyzer();
        var constant = analyzer.proveFloat(jar, "foreign/Orb", "getCollisionBorderSize", "()F");
        assertTrue(constant.proven(), constant.reason());
        assertEquals(0.25F, constant.value());
        var dynamic = analyzer.proveFloat(jar, "foreign/Orb", "dynamicBorder", "()F");
        assertFalse(dynamic.proven());
        assertNull(dynamic.value());
        assertEquals("float-callback-not-exact-constant-return", dynamic.reason());
    }

    private static byte[] entity() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/Orb", null, "java/lang/Object", null);
        FieldVisitor field = writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "BORDER", "F", null, null);
        field.visitEnd();
        MethodVisitor constant = writer.visitMethod(Opcodes.ACC_PUBLIC, "getCollisionBorderSize", "()F", null, null);
        constant.visitCode(); constant.visitLdcInsn(0.25F); constant.visitInsn(Opcodes.FRETURN); constant.visitMaxs(0, 0); constant.visitEnd();
        MethodVisitor dynamic = writer.visitMethod(Opcodes.ACC_PUBLIC, "dynamicBorder", "()F", null, null);
        dynamic.visitCode(); dynamic.visitFieldInsn(Opcodes.GETSTATIC, "foreign/Orb", "BORDER", "F"); dynamic.visitInsn(Opcodes.FRETURN); dynamic.visitMaxs(0, 0); dynamic.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name)); out.write(bytes); out.closeEntry();
    }
}
