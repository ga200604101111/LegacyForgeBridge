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

class LegacyEntityAttackableBehaviorSurfaceAnalyzerTest {
    @TempDir Path tempDir;

    @Test void classifiesLegacyCanAttackWithItemSrgCallback() throws Exception {
        Path jar = tempDir.resolve("attackable.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/Orb.class", entity());
            put(out, "foreign/Bootstrap.class", bootstrap());
        }

        var analysis = new LegacyEntityBehaviorSurfaceAnalyzer().analyze(jar);
        assertEquals(1, analysis.entities().size(), analysis.diagnostics().toString());
        var surface = analysis.entities().getFirst();
        assertTrue(surface.callbacks().stream().anyMatch(callback ->
                callback.kind() == LegacyEntityBehaviorSurfaceAnalyzer.CallbackKind.CAN_ATTACK_WITH_ITEM
                        && callback.owner().equals("foreign/Orb")
                        && callback.method().equals("func_70075_an")
                        && callback.descriptor().equals("()Z")));
        assertTrue(surface.sourceMethods().stream().anyMatch(method ->
                method.method().equals("func_70075_an")
                        && method.callbackKind() == LegacyEntityBehaviorSurfaceAnalyzer.CallbackKind.CAN_ATTACK_WITH_ITEM));
    }

    private static byte[] entity() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/Orb", null, "net/minecraft/entity/Entity", null);
        MethodVisitor init = w.visitMethod(Opcodes.ACC_PROTECTED, "func_70088_a", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitFieldInsn(Opcodes.GETFIELD, "net/minecraft/entity/Entity", "field_70180_af", "Lnet/minecraft/entity/DataWatcher;");
        init.visitIntInsn(Opcodes.BIPUSH, 12);
        init.visitInsn(Opcodes.ICONST_0);
        init.visitInsn(Opcodes.I2B);
        init.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
        init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/DataWatcher", "func_75682_a", "(ILjava/lang/Object;)V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();
        MethodVisitor attackable = w.visitMethod(Opcodes.ACC_PUBLIC, "func_70075_an", "()Z", null, null);
        attackable.visitCode();
        attackable.visitInsn(Opcodes.ICONST_0);
        attackable.visitInsn(Opcodes.IRETURN);
        attackable.visitMaxs(0, 0);
        attackable.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] bootstrap() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/Bootstrap", null, "java/lang/Object", null);
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "preInit", "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor annotation = m.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        annotation.visitEnd();
        m.visitCode();
        m.visitLdcInsn(Type.getObjectType("foreign/Orb"));
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
        out.putNextEntry(new JarEntry(name)); out.write(bytes); out.closeEntry();
    }
}
