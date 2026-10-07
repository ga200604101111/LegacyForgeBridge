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

class LegacyEntityDataWatcherGlobalClosureAnalyzerTest {
    @TempDir Path tempDir;

    @Test void provesSourceWideClosureOnlyWhenEveryRuntimeWatcherCallIsAlreadyAccounted() throws Exception {
        Path safe = fixture("safe.jar", false);
        var safeAnalysis = new LegacyEntityDataWatcherGlobalClosureAnalyzer().analyze(safe);
        assertTrue(safeAnalysis.sourceWideClosureComplete(), safeAnalysis.unresolved().toString());
        assertEquals(2, safeAnalysis.runtimeCallCount());
        assertEquals(2, safeAnalysis.provenRuntimeCallCount());
        assertEquals(2, safeAnalysis.accounted().size());
        assertTrue(safeAnalysis.unresolved().isEmpty());

        Path rogue = fixture("rogue.jar", true);
        var rogueAnalysis = new LegacyEntityDataWatcherGlobalClosureAnalyzer().analyze(rogue);
        assertFalse(rogueAnalysis.sourceWideClosureComplete());
        assertEquals(3, rogueAnalysis.runtimeCallCount());
        assertEquals(2, rogueAnalysis.provenRuntimeCallCount());
        assertEquals(1, rogueAnalysis.unresolved().size());
        var unresolved = rogueAnalysis.unresolved().getFirst();
        assertEquals("third/global/Rogue", unresolved.sourceOwner());
        assertEquals("read", unresolved.sourceMethod());
        assertEquals(1, unresolved.runtimeCallCount());
        assertEquals(0, unresolved.provenAccessCount());
    }

    @Test void pinnedVanillaLivingBaseWatcherGettersCanCloseOutsideEntityLineage() throws Exception {
        Path safe = tempDir.resolve("platform-safe.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(safe))) {
            put(out, "third/global/PlatformReader.class", platformReader(false));
        }
        var safeAnalysis = new LegacyEntityDataWatcherGlobalClosureAnalyzer().analyze(safe);
        assertTrue(safeAnalysis.sourceWideClosureComplete(), safeAnalysis.unresolved().toString());
        assertEquals(2, safeAnalysis.runtimeCallCount());
        assertEquals(2, safeAnalysis.provenRuntimeCallCount());
        assertEquals(1, safeAnalysis.accounted().size());

        Path wrong = tempDir.resolve("platform-wrong.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(wrong))) {
            put(out, "third/global/PlatformReader.class", platformReader(true));
        }
        var wrongAnalysis = new LegacyEntityDataWatcherGlobalClosureAnalyzer().analyze(wrong);
        assertFalse(wrongAnalysis.sourceWideClosureComplete());
        assertEquals(2, wrongAnalysis.runtimeCallCount());
        assertEquals(1, wrongAnalysis.provenRuntimeCallCount());
        assertEquals(1, wrongAnalysis.unresolved().size());
    }

    private Path fixture(String name, boolean rogue) throws Exception {
        Path jar = tempDir.resolve(name);
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "third/global/Orb.class", entity());
            put(out, "third/global/Bootstrap.class", bootstrap());
            if (rogue) put(out, "third/global/Rogue.class", rogue());
        }
        return jar;
    }

    private static byte[] entity() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "third/global/Orb", null, "net/minecraft/entity/Entity", null);

        MethodVisitor init = w.visitMethod(Opcodes.ACC_PROTECTED, "func_70088_a", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/entity/Entity", "func_70088_a", "()V", false);
        watcher(init, 0);
        init.visitIntInsn(Opcodes.BIPUSH, 12);
        init.visitInsn(Opcodes.ICONST_0);
        init.visitInsn(Opcodes.I2B);
        init.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
        init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/DataWatcher", "func_75682_a",
                "(ILjava/lang/Object;)V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();

        MethodVisitor set = w.visitMethod(Opcodes.ACC_PUBLIC, "set", "(B)V", null, null);
        set.visitCode();
        watcher(set, 0);
        set.visitIntInsn(Opcodes.BIPUSH, 12);
        set.visitVarInsn(Opcodes.ILOAD, 1);
        set.visitInsn(Opcodes.I2B);
        set.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
        set.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/DataWatcher", "func_75692_b",
                "(ILjava/lang/Object;)V", false);
        set.visitInsn(Opcodes.RETURN);
        set.visitMaxs(0, 0);
        set.visitEnd();

        MethodVisitor get = w.visitMethod(Opcodes.ACC_PUBLIC, "get", "()B", null, null);
        get.visitCode();
        watcher(get, 0);
        get.visitIntInsn(Opcodes.BIPUSH, 12);
        get.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/DataWatcher", "func_75683_a", "(I)B", false);
        get.visitInsn(Opcodes.IRETURN);
        get.visitMaxs(0, 0);
        get.visitEnd();

        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] platformReader(boolean wrongType) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "third/global/PlatformReader",
                null, "java/lang/Object", null);
        MethodVisitor read = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "read",
                "(Lnet/minecraft/entity/EntityLivingBase;)V", null, null);
        read.visitCode();

        read.visitVarInsn(Opcodes.ALOAD, 0);
        read.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/EntityLivingBase",
                "func_70096_w", "()Lnet/minecraft/entity/DataWatcher;", false);
        read.visitIntInsn(Opcodes.BIPUSH, 7);
        if (wrongType) {
            read.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/DataWatcher",
                    "func_75683_a", "(I)B", false);
        } else {
            read.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/DataWatcher",
                    "func_75679_c", "(I)I", false);
        }
        read.visitInsn(Opcodes.POP);

        read.visitVarInsn(Opcodes.ALOAD, 0);
        read.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/EntityLivingBase",
                "func_70096_w", "()Lnet/minecraft/entity/DataWatcher;", false);
        read.visitIntInsn(Opcodes.BIPUSH, 8);
        read.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/DataWatcher",
                "func_75683_a", "(I)B", false);
        read.visitInsn(Opcodes.POP);

        read.visitInsn(Opcodes.RETURN);
        read.visitMaxs(0, 0);
        read.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] rogue() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "third/global/Rogue", null, "java/lang/Object", null);
        MethodVisitor read = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "read",
                "(Lnet/minecraft/entity/Entity;)B", null, null);
        read.visitCode();
        watcher(read, 0);
        read.visitIntInsn(Opcodes.BIPUSH, 12);
        read.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/DataWatcher", "func_75683_a", "(I)B", false);
        read.visitInsn(Opcodes.IRETURN);
        read.visitMaxs(0, 0);
        read.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static void watcher(MethodVisitor method, int entityLocal) {
        method.visitVarInsn(Opcodes.ALOAD, entityLocal);
        method.visitFieldInsn(Opcodes.GETFIELD, "net/minecraft/entity/Entity", "field_70180_af",
                "Lnet/minecraft/entity/DataWatcher;");
    }

    private static byte[] bootstrap() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "third/global/Bootstrap", null, "java/lang/Object", null);
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor annotation = m.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        annotation.visitEnd();
        m.visitCode();
        m.visitLdcInsn(Type.getObjectType("third/global/Orb"));
        m.visitLdcInsn("orb");
        m.visitIntInsn(Opcodes.BIPUSH, 17);
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitIntInsn(Opcodes.BIPUSH, 80);
        m.visitInsn(Opcodes.ICONST_2);
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
