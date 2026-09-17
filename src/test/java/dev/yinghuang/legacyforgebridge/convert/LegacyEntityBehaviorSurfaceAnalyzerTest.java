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

class LegacyEntityBehaviorSurfaceAnalyzerTest {
    @TempDir Path tempDir;

    @Test void inventoriesEffectiveCallbacksAndRetainsUnclassifiedSourceMethodsAcrossLineage() throws Exception {
        Path jar = tempDir.resolve("EntitySurface.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "third/surface/BaseOrb.class", baseEntity());
            put(out, "third/surface/Orb.class", entity());
            put(out, "third/surface/Bootstrap.class", bootstrap());
        }

        var analysis = new LegacyEntityBehaviorSurfaceAnalyzer().analyze(jar);
        assertEquals(1, analysis.entities().size(), analysis.diagnostics().toString());
        var surface = analysis.entities().getFirst();
        assertEquals("orb", surface.registryName());
        assertEquals("third/surface/Orb", surface.sourceClass());
        assertEquals("net/minecraft/entity/Entity", surface.externalBaseClass());
        assertEquals(java.util.List.of("third/surface/Orb", "third/surface/BaseOrb"), surface.sourceLineage());

        assertTrue(surface.callbacks().stream().anyMatch(callback -> callback.kind() == LegacyEntityBehaviorSurfaceAnalyzer.CallbackKind.ENTITY_INIT
                && callback.owner().equals("third/surface/BaseOrb")));
        assertTrue(surface.callbacks().stream().anyMatch(callback -> callback.kind() == LegacyEntityBehaviorSurfaceAnalyzer.CallbackKind.TICK
                && callback.owner().equals("third/surface/BaseOrb")));
        assertTrue(surface.callbacks().stream().anyMatch(callback -> callback.kind() == LegacyEntityBehaviorSurfaceAnalyzer.CallbackKind.READ_NBT
                && callback.owner().equals("third/surface/Orb")));
        assertTrue(surface.callbacks().stream().anyMatch(callback -> callback.kind() == LegacyEntityBehaviorSurfaceAnalyzer.CallbackKind.INTERACT
                && callback.owner().equals("third/surface/Orb")));

        assertTrue(surface.sourceMethods().stream().anyMatch(method -> method.owner().equals("third/surface/BaseOrb")
                && method.method().equals("helper") && method.callbackKind() == null));
        assertFalse(surface.sourceMethods().stream().anyMatch(method -> method.method().equals("<init>")));
    }

    private static byte[] baseEntity() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "third/surface/BaseOrb", null, "net/minecraft/entity/Entity", null);

        MethodVisitor init = w.visitMethod(Opcodes.ACC_PROTECTED, "func_70088_a", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/entity/Entity", "func_70088_a", "()V", false);
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitFieldInsn(Opcodes.GETFIELD, "net/minecraft/entity/Entity", "field_70180_af", "Lnet/minecraft/entity/DataWatcher;");
        init.visitIntInsn(Opcodes.BIPUSH, 12);
        init.visitInsn(Opcodes.ICONST_0);
        init.visitInsn(Opcodes.I2B);
        init.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
        init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/DataWatcher", "func_75682_a",
                "(ILjava/lang/Object;)V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();

        MethodVisitor tick = w.visitMethod(Opcodes.ACC_PUBLIC, "func_70071_h_", "()V", null, null);
        tick.visitCode();
        tick.visitInsn(Opcodes.RETURN);
        tick.visitMaxs(0, 0);
        tick.visitEnd();

        MethodVisitor helper = w.visitMethod(Opcodes.ACC_PROTECTED, "helper", "(I)I", null, null);
        helper.visitCode();
        helper.visitVarInsn(Opcodes.ILOAD, 1);
        helper.visitInsn(Opcodes.IRETURN);
        helper.visitMaxs(0, 0);
        helper.visitEnd();

        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] entity() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "third/surface/Orb", null, "third/surface/BaseOrb", null);

        MethodVisitor read = w.visitMethod(Opcodes.ACC_PROTECTED, "func_70037_a",
                "(Lnet/minecraft/nbt/NBTTagCompound;)V", null, null);
        read.visitCode();
        read.visitInsn(Opcodes.RETURN);
        read.visitMaxs(0, 0);
        read.visitEnd();

        MethodVisitor interact = w.visitMethod(Opcodes.ACC_PUBLIC, "func_70085_c",
                "(Lnet/minecraft/entity/player/EntityPlayer;)Z", null, null);
        interact.visitCode();
        interact.visitInsn(Opcodes.ICONST_1);
        interact.visitInsn(Opcodes.IRETURN);
        interact.visitMaxs(0, 0);
        interact.visitEnd();

        MethodVisitor utility = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "staticUtility", "()V", null, null);
        utility.visitCode();
        utility.visitInsn(Opcodes.RETURN);
        utility.visitMaxs(0, 0);
        utility.visitEnd();

        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] bootstrap() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "third/surface/Bootstrap", null, "java/lang/Object", null);
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor annotation = m.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        annotation.visitEnd();
        m.visitCode();
        m.visitLdcInsn(Type.getObjectType("third/surface/Orb"));
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
