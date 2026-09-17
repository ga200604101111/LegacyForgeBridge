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

class LegacyEntityDataWatcherExactDispatchAnalyzerTest {
    @TempDir Path tempDir;

    @Test void followsSpecialAndFinalVirtualHelpersButLeavesOpenVirtualDispatchOutsideTheClosure() throws Exception {
        Path jar = tempDir.resolve("ExactDispatch.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "third/exact/Orb.class", entity());
            put(out, "third/exact/Helpers.class", helpers());
            put(out, "third/exact/FinalWorker.class", finalWorker());
            put(out, "third/exact/OpenWorker.class", openWorker());
            put(out, "third/exact/Bootstrap.class", bootstrap());
        }

        var analysis = new LegacyEntityDataWatcherAccessAnalyzer().analyze(jar);
        assertTrue(analysis.skipped().isEmpty(), analysis.skipped().toString());
        assertEquals(1, analysis.rules().size());
        var accesses = analysis.rules().getFirst().accesses();
        assertEquals(2, accesses.size(), accesses.toString());
        assertTrue(accesses.stream().anyMatch(a -> a.operation().equals("write")
                && a.sourceOwner().equals("third/exact/Helpers")
                && a.sourceMethod().equals("specialSet")), accesses.toString());
        assertTrue(accesses.stream().anyMatch(a -> a.operation().equals("read")
                && a.sourceOwner().equals("third/exact/FinalWorker")
                && a.sourceMethod().equals("read")), accesses.toString());
        assertFalse(accesses.stream().anyMatch(a -> a.sourceOwner().equals("third/exact/OpenWorker")),
                "overridable INVOKEVIRTUAL targets must remain outside the exact-dispatch closure");
    }

    private static byte[] entity() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "third/exact/Orb", null, "net/minecraft/entity/Entity", null);

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

        MethodVisitor run = w.visitMethod(Opcodes.ACC_PUBLIC, "runExact", "()V", null, null);
        run.visitCode();
        run.visitVarInsn(Opcodes.ALOAD, 0);
        run.visitInsn(Opcodes.ICONST_1);
        run.visitMethodInsn(Opcodes.INVOKESTATIC, "third/exact/Helpers", "setViaSpecial",
                "(Lthird/exact/Orb;Z)V", false);
        run.visitVarInsn(Opcodes.ALOAD, 0);
        run.visitMethodInsn(Opcodes.INVOKESTATIC, "third/exact/Helpers", "readViaFinal",
                "(Lthird/exact/Orb;)B", false);
        run.visitInsn(Opcodes.POP);
        run.visitVarInsn(Opcodes.ALOAD, 0);
        run.visitIntInsn(Opcodes.BIPUSH, 12);
        run.visitMethodInsn(Opcodes.INVOKESTATIC, "third/exact/Helpers", "readViaOpen",
                "(Lthird/exact/Orb;I)B", false);
        run.visitInsn(Opcodes.POP);
        run.visitInsn(Opcodes.RETURN);
        run.visitMaxs(0, 0);
        run.visitEnd();

        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] helpers() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "third/exact/Helpers", null, "java/lang/Object", null);
        constructor(w, "third/exact/Helpers");

        MethodVisitor set = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "setViaSpecial",
                "(Lthird/exact/Orb;Z)V", null, null);
        set.visitCode();
        set.visitTypeInsn(Opcodes.NEW, "third/exact/Helpers");
        set.visitInsn(Opcodes.DUP);
        set.visitMethodInsn(Opcodes.INVOKESPECIAL, "third/exact/Helpers", "<init>", "()V", false);
        set.visitVarInsn(Opcodes.ALOAD, 0);
        set.visitVarInsn(Opcodes.ILOAD, 1);
        set.visitMethodInsn(Opcodes.INVOKESPECIAL, "third/exact/Helpers", "specialSet",
                "(Lthird/exact/Orb;Z)V", false);
        set.visitInsn(Opcodes.RETURN);
        set.visitMaxs(0, 0);
        set.visitEnd();

        MethodVisitor special = w.visitMethod(Opcodes.ACC_PRIVATE, "specialSet",
                "(Lthird/exact/Orb;Z)V", null, null);
        special.visitCode();
        watcher(special, 1);
        special.visitIntInsn(Opcodes.BIPUSH, 12);
        special.visitVarInsn(Opcodes.ILOAD, 2);
        special.visitInsn(Opcodes.I2B);
        special.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
        special.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/DataWatcher", "func_75692_b",
                "(ILjava/lang/Object;)V", false);
        special.visitInsn(Opcodes.RETURN);
        special.visitMaxs(0, 0);
        special.visitEnd();

        MethodVisitor finalRead = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "readViaFinal",
                "(Lthird/exact/Orb;)B", null, null);
        finalRead.visitCode();
        finalRead.visitTypeInsn(Opcodes.NEW, "third/exact/FinalWorker");
        finalRead.visitInsn(Opcodes.DUP);
        finalRead.visitMethodInsn(Opcodes.INVOKESPECIAL, "third/exact/FinalWorker", "<init>", "()V", false);
        finalRead.visitVarInsn(Opcodes.ALOAD, 0);
        finalRead.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "third/exact/FinalWorker", "read",
                "(Lthird/exact/Orb;)B", false);
        finalRead.visitInsn(Opcodes.IRETURN);
        finalRead.visitMaxs(0, 0);
        finalRead.visitEnd();

        MethodVisitor openRead = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "readViaOpen",
                "(Lthird/exact/Orb;I)B", null, null);
        openRead.visitCode();
        openRead.visitTypeInsn(Opcodes.NEW, "third/exact/OpenWorker");
        openRead.visitInsn(Opcodes.DUP);
        openRead.visitMethodInsn(Opcodes.INVOKESPECIAL, "third/exact/OpenWorker", "<init>", "()V", false);
        openRead.visitVarInsn(Opcodes.ALOAD, 0);
        openRead.visitVarInsn(Opcodes.ILOAD, 1);
        openRead.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "third/exact/OpenWorker", "read",
                "(Lthird/exact/Orb;I)B", false);
        openRead.visitInsn(Opcodes.IRETURN);
        openRead.visitMaxs(0, 0);
        openRead.visitEnd();

        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] finalWorker() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "third/exact/FinalWorker", null, "java/lang/Object", null);
        constructor(w, "third/exact/FinalWorker");
        MethodVisitor read = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "read",
                "(Lthird/exact/Orb;)B", null, null);
        read.visitCode();
        watcher(read, 1);
        read.visitIntInsn(Opcodes.BIPUSH, 12);
        read.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/DataWatcher", "func_75683_a", "(I)B", false);
        read.visitInsn(Opcodes.IRETURN);
        read.visitMaxs(0, 0);
        read.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] openWorker() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "third/exact/OpenWorker", null, "java/lang/Object", null);
        constructor(w, "third/exact/OpenWorker");
        MethodVisitor read = w.visitMethod(Opcodes.ACC_PUBLIC, "read",
                "(Lthird/exact/Orb;I)B", null, null);
        read.visitCode();
        watcher(read, 1);
        read.visitVarInsn(Opcodes.ILOAD, 2);
        read.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/DataWatcher", "func_75683_a", "(I)B", false);
        read.visitInsn(Opcodes.IRETURN);
        read.visitMaxs(0, 0);
        read.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static void constructor(ClassWriter w, String owner) {
        MethodVisitor init = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();
    }

    private static void watcher(MethodVisitor m, int entityLocal) {
        m.visitVarInsn(Opcodes.ALOAD, entityLocal);
        m.visitFieldInsn(Opcodes.GETFIELD, "net/minecraft/entity/Entity", "field_70180_af",
                "Lnet/minecraft/entity/DataWatcher;");
    }

    private static byte[] bootstrap() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "third/exact/Bootstrap", null, "java/lang/Object", null);
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor annotation = m.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        annotation.visitEnd();
        m.visitCode();
        m.visitLdcInsn(Type.getObjectType("third/exact/Orb"));
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
