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

class LegacyEntityDataWatcherAccessAnalyzerTest {
    @TempDir Path tempDir;

    @Test void provesTypedReadsAndWritesOnlyAgainstTheSourceOwnedWatcherSchema() throws Exception {
        Path jar = tempDir.resolve("Access.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "third/entity/Orb.class", entity(Mode.SAFE));
            put(out, "third/entity/Bootstrap.class", bootstrap());
        }
        var analysis = new LegacyEntityDataWatcherAccessAnalyzer().analyze(jar);
        assertTrue(analysis.skipped().isEmpty(), analysis.skipped().toString());
        assertEquals(1, analysis.rules().size());
        var rule = analysis.rules().getFirst();
        assertEquals(3, rule.accesses().size());
        assertTrue(rule.accesses().stream().anyMatch(a -> a.index() == 12 && a.operation().equals("write") && a.valueKind().equals("byte")));
        assertTrue(rule.accesses().stream().anyMatch(a -> a.index() == 12 && a.operation().equals("read") && a.valueKind().equals("byte")));
        assertTrue(rule.accesses().stream().anyMatch(a -> a.index() == 13 && a.operation().equals("read") && a.valueKind().equals("int")));
    }

    @Test void dynamicReadAndMismatchedWriteFailClosed() throws Exception {
        for (Mode mode : new Mode[]{Mode.DYNAMIC_READ, Mode.WRONG_WRITE_TYPE}) {
            Path jar = tempDir.resolve(mode.name() + ".jar");
            try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
                put(out, "third/entity/Orb.class", entity(mode));
                put(out, "third/entity/Bootstrap.class", bootstrap());
            }
            var analysis = new LegacyEntityDataWatcherAccessAnalyzer().analyze(jar);
            assertTrue(analysis.rules().isEmpty(), mode + " must stay fail closed");
            assertEquals(1, analysis.skipped().size());
            String reason = analysis.skipped().getFirst().reason();
            if (mode == Mode.DYNAMIC_READ) assertTrue(reason.contains("Dynamic/unproven DataWatcher read index"), reason);
            else assertTrue(reason.contains("write type mismatch"), reason);
        }
    }

    @Test void followsOnlyReachableStaticHelpersBoundToTheSourceEntity() throws Exception {
        Path jar = tempDir.resolve("StaticHelpers.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "third/entity/Orb.class", helperEntity(false));
            put(out, "third/entity/WatcherHelpers.class", watcherHelpers());
            put(out, "third/entity/WatcherLeaf.class", watcherLeaf());
            put(out, "third/entity/Bootstrap.class", bootstrap());
        }

        var analysis = new LegacyEntityDataWatcherAccessAnalyzer().analyze(jar);
        assertTrue(analysis.skipped().isEmpty(), analysis.skipped().toString());
        assertEquals(1, analysis.rules().size());
        var accesses = analysis.rules().getFirst().accesses();
        assertEquals(2, accesses.size(), accesses.toString());
        assertTrue(accesses.stream().anyMatch(a -> a.operation().equals("write")
                && a.sourceOwner().equals("third/entity/WatcherHelpers")
                && a.sourceMethod().equals("set")));
        assertTrue(accesses.stream().anyMatch(a -> a.operation().equals("read")
                && a.sourceOwner().equals("third/entity/WatcherLeaf")
                && a.sourceMethod().equals("read")));
        assertFalse(accesses.stream().anyMatch(a -> a.sourceMethod().equals("unreachable")),
                "an uncalled helper must not contaminate the source entity access surface");
    }

    @Test void dynamicIndexInsideReachableStaticHelperFailsClosed() throws Exception {
        Path jar = tempDir.resolve("DynamicHelper.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "third/entity/Orb.class", helperEntity(true));
            put(out, "third/entity/WatcherHelpers.class", watcherHelpers());
            put(out, "third/entity/WatcherLeaf.class", watcherLeaf());
            put(out, "third/entity/Bootstrap.class", bootstrap());
        }

        var analysis = new LegacyEntityDataWatcherAccessAnalyzer().analyze(jar);
        assertTrue(analysis.rules().isEmpty());
        assertEquals(1, analysis.skipped().size());
        assertTrue(analysis.skipped().getFirst().reason().contains("Dynamic/unproven DataWatcher read index"),
                analysis.skipped().getFirst().reason());
        assertTrue(analysis.skipped().getFirst().reason().contains("WatcherHelpers.dynamic"),
                analysis.skipped().getFirst().reason());
    }

    private enum Mode { SAFE, DYNAMIC_READ, WRONG_WRITE_TYPE }

    private static byte[] entity(Mode mode) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "third/entity/Orb", null, "net/minecraft/entity/Entity", null);
        if (mode == Mode.DYNAMIC_READ) w.visitField(Opcodes.ACC_PRIVATE, "index", "I", null, null).visitEnd();

        MethodVisitor init = w.visitMethod(Opcodes.ACC_PROTECTED, "func_70088_a", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/entity/Entity", "func_70088_a", "()V", false);
        addByte(init, 12, 0);
        addInt(init, 13, 7);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();

        MethodVisitor set = w.visitMethod(Opcodes.ACC_PUBLIC, "setActive", "(Z)V", null, null);
        set.visitCode();
        watcher(set);
        set.visitIntInsn(Opcodes.BIPUSH, 12);
        set.visitVarInsn(Opcodes.ILOAD, 1);
        if (mode == Mode.WRONG_WRITE_TYPE) {
            set.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
        } else {
            set.visitInsn(Opcodes.I2B);
            set.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
        }
        set.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/DataWatcher", "func_75692_b", "(ILjava/lang/Object;)V", false);
        set.visitInsn(Opcodes.RETURN);
        set.visitMaxs(0, 0);
        set.visitEnd();

        MethodVisitor get = w.visitMethod(Opcodes.ACC_PUBLIC, "active", "()B", null, null);
        get.visitCode();
        watcher(get);
        if (mode == Mode.DYNAMIC_READ) {
            get.visitVarInsn(Opcodes.ALOAD, 0);
            get.visitFieldInsn(Opcodes.GETFIELD, "third/entity/Orb", "index", "I");
        } else get.visitIntInsn(Opcodes.BIPUSH, 12);
        get.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/DataWatcher", "func_75683_a", "(I)B", false);
        get.visitInsn(Opcodes.IRETURN);
        get.visitMaxs(0, 0);
        get.visitEnd();

        MethodVisitor count = w.visitMethod(Opcodes.ACC_PUBLIC, "count", "()I", null, null);
        count.visitCode();
        count.visitVarInsn(Opcodes.ALOAD, 0);
        count.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/Entity", "func_70096_w", "()Lnet/minecraft/entity/DataWatcher;", false);
        count.visitIntInsn(Opcodes.BIPUSH, 13);
        count.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/DataWatcher", "func_75679_c", "(I)I", false);
        count.visitInsn(Opcodes.IRETURN);
        count.visitMaxs(0, 0);
        count.visitEnd();

        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] helperEntity(boolean dynamicHelper) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "third/entity/Orb", null, "net/minecraft/entity/Entity", null);

        MethodVisitor init = w.visitMethod(Opcodes.ACC_PROTECTED, "func_70088_a", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/entity/Entity", "func_70088_a", "()V", false);
        addByte(init, 12, 0);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();

        MethodVisitor set = w.visitMethod(Opcodes.ACC_PUBLIC, "setThroughHelper", "(Z)V", null, null);
        set.visitCode();
        set.visitVarInsn(Opcodes.ALOAD, 0);
        set.visitVarInsn(Opcodes.ILOAD, 1);
        set.visitMethodInsn(Opcodes.INVOKESTATIC, "third/entity/WatcherHelpers", "set",
                "(Lthird/entity/Orb;Z)V", false);
        set.visitInsn(Opcodes.RETURN);
        set.visitMaxs(0, 0);
        set.visitEnd();

        MethodVisitor read;
        if (dynamicHelper) {
            read = w.visitMethod(Opcodes.ACC_PUBLIC, "readThroughHelper", "(I)B", null, null);
            read.visitCode();
            read.visitVarInsn(Opcodes.ALOAD, 0);
            read.visitVarInsn(Opcodes.ILOAD, 1);
            read.visitMethodInsn(Opcodes.INVOKESTATIC, "third/entity/WatcherHelpers", "dynamic",
                    "(Lthird/entity/Orb;I)B", false);
        } else {
            read = w.visitMethod(Opcodes.ACC_PUBLIC, "readThroughHelper", "()B", null, null);
            read.visitCode();
            read.visitVarInsn(Opcodes.ALOAD, 0);
            read.visitMethodInsn(Opcodes.INVOKESTATIC, "third/entity/WatcherHelpers", "read",
                    "(Lthird/entity/Orb;)B", false);
        }
        read.visitInsn(Opcodes.IRETURN);
        read.visitMaxs(0, 0);
        read.visitEnd();

        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] watcherHelpers() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "third/entity/WatcherHelpers", null, "java/lang/Object", null);

        MethodVisitor set = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "set",
                "(Lthird/entity/Orb;Z)V", null, null);
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

        MethodVisitor read = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "read",
                "(Lthird/entity/Orb;)B", null, null);
        read.visitCode();
        read.visitVarInsn(Opcodes.ALOAD, 0);
        read.visitMethodInsn(Opcodes.INVOKESTATIC, "third/entity/WatcherLeaf", "read",
                "(Lthird/entity/Orb;)B", false);
        read.visitInsn(Opcodes.IRETURN);
        read.visitMaxs(0, 0);
        read.visitEnd();

        MethodVisitor dynamic = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "dynamic",
                "(Lthird/entity/Orb;I)B", null, null);
        dynamic.visitCode();
        watcher(dynamic, 0);
        dynamic.visitVarInsn(Opcodes.ILOAD, 1);
        dynamic.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/DataWatcher", "func_75683_a", "(I)B", false);
        dynamic.visitInsn(Opcodes.IRETURN);
        dynamic.visitMaxs(0, 0);
        dynamic.visitEnd();

        MethodVisitor unreachable = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "unreachable",
                "(Lthird/entity/Orb;I)B", null, null);
        unreachable.visitCode();
        watcher(unreachable, 0);
        unreachable.visitVarInsn(Opcodes.ILOAD, 1);
        unreachable.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/DataWatcher", "func_75683_a", "(I)B", false);
        unreachable.visitInsn(Opcodes.IRETURN);
        unreachable.visitMaxs(0, 0);
        unreachable.visitEnd();

        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] watcherLeaf() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "third/entity/WatcherLeaf", null, "java/lang/Object", null);
        MethodVisitor read = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "read",
                "(Lthird/entity/Orb;)B", null, null);
        read.visitCode();
        read.visitVarInsn(Opcodes.ALOAD, 0);
        read.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/Entity", "func_70096_w",
                "()Lnet/minecraft/entity/DataWatcher;", false);
        read.visitIntInsn(Opcodes.BIPUSH, 12);
        read.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/DataWatcher", "func_75683_a", "(I)B", false);
        read.visitInsn(Opcodes.IRETURN);
        read.visitMaxs(0, 0);
        read.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static void addByte(MethodVisitor m, int index, int value) {
        watcher(m); m.visitIntInsn(Opcodes.BIPUSH, index); m.visitIntInsn(Opcodes.BIPUSH, value); m.visitInsn(Opcodes.I2B);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/DataWatcher", "func_75682_a", "(ILjava/lang/Object;)V", false);
    }
    private static void addInt(MethodVisitor m, int index, int value) {
        watcher(m); m.visitIntInsn(Opcodes.BIPUSH, index); m.visitIntInsn(Opcodes.BIPUSH, value);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/DataWatcher", "func_75682_a", "(ILjava/lang/Object;)V", false);
    }
    private static void watcher(MethodVisitor m) {
        watcher(m, 0);
    }
    private static void watcher(MethodVisitor m, int entityLocal) {
        m.visitVarInsn(Opcodes.ALOAD, entityLocal);
        m.visitFieldInsn(Opcodes.GETFIELD, "net/minecraft/entity/Entity", "field_70180_af", "Lnet/minecraft/entity/DataWatcher;");
    }

    private static byte[] bootstrap() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "third/entity/Bootstrap", null, "java/lang/Object", null);
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "preInit", "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor annotation = m.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true); annotation.visitEnd();
        m.visitCode();
        m.visitLdcInsn(Type.getObjectType("third/entity/Orb"));
        m.visitLdcInsn("orb");
        m.visitIntInsn(Opcodes.BIPUSH, 17);
        m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitIntInsn(Opcodes.BIPUSH, 80);
        m.visitInsn(Opcodes.ICONST_2);
        m.visitInsn(Opcodes.ICONST_1);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/EntityRegistry", "registerModEntity",
                "(Ljava/lang/Class;Ljava/lang/String;ILjava/lang/Object;IIZ)V", false);
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 0); m.visitEnd(); w.visitEnd(); return w.toByteArray();
    }

    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name)); out.write(bytes); out.closeEntry();
    }
}
