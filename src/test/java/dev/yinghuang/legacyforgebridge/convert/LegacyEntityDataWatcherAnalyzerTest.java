package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class LegacyEntityDataWatcherAnalyzerTest {
    @TempDir Path tempDir;

    @Test void provesRegistrationAndDirectPrimitiveWatcherDefaultsAcrossAnotherNamespace() throws Exception {
        Path jar = tempDir.resolve("ForeignEntity.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "alt/entity/Spark.class", entity(false));
            put(out, "alt/entity/Bootstrap.class", bootstrap());
        }
        var analysis = new LegacyEntityDataWatcherAnalyzer().analyze(jar);
        assertTrue(analysis.diagnostics().isEmpty(), String.join("\n", analysis.diagnostics()));
        assertTrue(analysis.skipped().isEmpty(), analysis.skipped().toString());
        assertEquals(1, analysis.rules().size());
        var rule = analysis.rules().getFirst();
        assertEquals("spark", rule.registryName());
        assertEquals("alt/entity/Spark", rule.sourceClass());
        assertEquals(37, rule.numericId());
        assertEquals(96, rule.trackingRange());
        assertEquals(3, rule.updateFrequency());
        assertTrue(rule.velocityUpdates());
        assertEquals(2, rule.entries().size());
        assertEquals(12, rule.entries().get(0).index());
        assertEquals("byte", rule.entries().get(0).valueKind());
        assertEquals((byte) 0, ((Number) rule.entries().get(0).defaultValue()).byteValue());
        assertEquals(13, rule.entries().get(1).index());
        assertEquals("int", rule.entries().get(1).valueKind());
        assertEquals(7, ((Number) rule.entries().get(1).defaultValue()).intValue());
    }

    @Test void dynamicWatcherIndexFailsClosed() throws Exception {
        Path jar = tempDir.resolve("UnsafeEntity.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "alt/entity/Spark.class", entity(true));
            put(out, "alt/entity/Bootstrap.class", bootstrap());
        }
        var analysis = new LegacyEntityDataWatcherAnalyzer().analyze(jar);
        assertTrue(analysis.rules().isEmpty());
        assertEquals(1, analysis.skipped().size());
        assertTrue(analysis.skipped().getFirst().reason().contains("Dynamic/unproven DataWatcher index"),
                analysis.skipped().getFirst().reason());
    }

    private static byte[] entity(boolean dynamicIndex) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "alt/entity/Spark", null, "net/minecraft/entity/Entity", null);
        if (dynamicIndex) w.visitField(Opcodes.ACC_PRIVATE, "watcherIndex", "I", null, null).visitEnd();
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PROTECTED, "func_70088_a", "()V", null, null);
        m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/entity/Entity", "func_70088_a", "()V", false);

        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitFieldInsn(Opcodes.GETFIELD, "net/minecraft/entity/Entity", "field_70180_af", "Lnet/minecraft/entity/DataWatcher;");
        if (dynamicIndex) {
            m.visitVarInsn(Opcodes.ALOAD, 0);
            m.visitFieldInsn(Opcodes.GETFIELD, "alt/entity/Spark", "watcherIndex", "I");
        } else m.visitIntInsn(Opcodes.BIPUSH, 12);
        m.visitInsn(Opcodes.ICONST_0);
        m.visitInsn(Opcodes.I2B);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/DataWatcher", "func_75682_a", "(ILjava/lang/Object;)V", false);

        if (!dynamicIndex) {
            m.visitVarInsn(Opcodes.ALOAD, 0);
            m.visitFieldInsn(Opcodes.GETFIELD, "net/minecraft/entity/Entity", "field_70180_af", "Lnet/minecraft/entity/DataWatcher;");
            m.visitIntInsn(Opcodes.BIPUSH, 13);
            m.visitIntInsn(Opcodes.BIPUSH, 7);
            m.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
            m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/DataWatcher", "func_75682_a", "(ILjava/lang/Object;)V", false);
        }
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] bootstrap() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "alt/entity/Bootstrap", null, "java/lang/Object", null);
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "preInit", "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor annotation = m.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        annotation.visitEnd();
        m.visitCode();
        m.visitLdcInsn(Type.getObjectType("alt/entity/Spark"));
        m.visitLdcInsn("spark");
        m.visitIntInsn(Opcodes.BIPUSH, 37);
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitIntInsn(Opcodes.BIPUSH, 96);
        m.visitInsn(Opcodes.ICONST_3);
        m.visitInsn(Opcodes.ICONST_1);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/EntityRegistry", "registerModEntity",
                "(Ljava/lang/Class;Ljava/lang/String;ILjava/lang/Object;IIZ)V", false);
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name));
        out.write(bytes);
        out.closeEntry();
    }
}
