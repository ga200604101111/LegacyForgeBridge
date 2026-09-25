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

class LegacyEntityConstructionAnalyzerTest {
    @TempDir Path tempDir;

    @Test void provesEffectiveConstantSizeAcrossExactSourceConstructorChainAndRetainsEffects() throws Exception {
        Path jar = tempDir.resolve("construction.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "third/construction/BaseOrb.class", baseEntity());
            put(out, "third/construction/Orb.class", entity(false));
            put(out, "third/construction/Bootstrap.class", bootstrap());
        }

        var analysis = new LegacyEntityConstructionAnalyzer().analyze(jar);
        assertEquals(1, analysis.rules().size(), analysis.diagnostics().toString());
        var rule = analysis.rules().getFirst();
        assertTrue(rule.worldConstructorPresent());
        assertTrue(rule.constructorChainComplete(), rule.sizeProofReason());
        assertTrue(rule.constructorControlFlowSimple());
        assertEquals("net/minecraft/entity/Entity", rule.externalBaseClass());
        assertFalse(rule.sourceSetSizeOverridePresent());
        assertTrue(rule.sizeProofComplete(), rule.sizeProofReason());
        assertEquals(0.5F, rule.width());
        assertEquals(1.5F, rule.height());
        assertEquals(2, rule.constructorChain().size());
        assertEquals("third/construction/Orb", rule.constructorChain().get(0).owner());
        assertEquals("third/construction/BaseOrb", rule.constructorChain().get(1).owner());
        assertTrue(rule.effects().stream().anyMatch(effect -> effect.kind().equals("this-field-write")
                && effect.member().equals("marker")));
        assertTrue(rule.effects().stream().anyMatch(effect -> effect.kind().equals("method-call")
                && effect.member().equals("touch")));
    }

    @Test void dynamicSetSizeFailsClosedWithoutDiscardingConstructionInventory() throws Exception {
        Path jar = tempDir.resolve("dynamic-construction.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "third/construction/BaseOrb.class", baseEntity());
            put(out, "third/construction/Orb.class", entity(true));
            put(out, "third/construction/Bootstrap.class", bootstrap());
        }

        var rule = new LegacyEntityConstructionAnalyzer().analyze(jar).rules().getFirst();
        assertTrue(rule.worldConstructorPresent());
        assertFalse(rule.sizeProofComplete());
        assertNull(rule.width());
        assertNull(rule.height());
        assertTrue(rule.sizeProofReason().contains("Dynamic/unproven setSize dimensions"), rule.sizeProofReason());
        assertTrue(rule.effects().stream().anyMatch(effect -> effect.kind().equals("unproven-set-size")));
    }

    private static byte[] baseEntity() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "third/construction/BaseOrb", null, "net/minecraft/entity/Entity", null);

        MethodVisitor init = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Lnet/minecraft/world/World;)V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitVarInsn(Opcodes.ALOAD, 1);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/entity/Entity", "<init>", "(Lnet/minecraft/world/World;)V", false);
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitLdcInsn(0.25F);
        init.visitLdcInsn(0.75F);
        init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/Entity", "func_70105_a", "(FF)V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();

        MethodVisitor entityInit = w.visitMethod(Opcodes.ACC_PROTECTED, "func_70088_a", "()V", null, null);
        entityInit.visitCode();
        entityInit.visitVarInsn(Opcodes.ALOAD, 0);
        entityInit.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/entity/Entity", "func_70088_a", "()V", false);
        entityInit.visitVarInsn(Opcodes.ALOAD, 0);
        entityInit.visitFieldInsn(Opcodes.GETFIELD, "net/minecraft/entity/Entity", "field_70180_af", "Lnet/minecraft/entity/DataWatcher;");
        entityInit.visitIntInsn(Opcodes.BIPUSH, 12);
        entityInit.visitInsn(Opcodes.ICONST_0);
        entityInit.visitInsn(Opcodes.I2B);
        entityInit.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
        entityInit.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/DataWatcher", "func_75682_a",
                "(ILjava/lang/Object;)V", false);
        entityInit.visitInsn(Opcodes.RETURN);
        entityInit.visitMaxs(0, 0);
        entityInit.visitEnd();

        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] entity(boolean dynamic) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "third/construction/Orb", null, "third/construction/BaseOrb", null);
        w.visitField(Opcodes.ACC_PRIVATE, "marker", "I", null, null).visitEnd();
        if (dynamic) w.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "WIDTH", "F", null, null).visitEnd();

        MethodVisitor init = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Lnet/minecraft/world/World;)V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitVarInsn(Opcodes.ALOAD, 1);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "third/construction/BaseOrb", "<init>", "(Lnet/minecraft/world/World;)V", false);
        init.visitVarInsn(Opcodes.ALOAD, 0);
        if (dynamic) init.visitFieldInsn(Opcodes.GETSTATIC, "third/construction/Orb", "WIDTH", "F");
        else init.visitLdcInsn(0.5F);
        init.visitLdcInsn(1.5F);
        init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/Entity", "func_70105_a", "(FF)V", false);
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitIntInsn(Opcodes.BIPUSH, 7);
        init.visitFieldInsn(Opcodes.PUTFIELD, "third/construction/Orb", "marker", "I");
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "third/construction/Orb", "touch", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();

        MethodVisitor touch = w.visitMethod(Opcodes.ACC_PRIVATE, "touch", "()V", null, null);
        touch.visitCode();
        touch.visitInsn(Opcodes.RETURN);
        touch.visitMaxs(0, 0);
        touch.visitEnd();

        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] bootstrap() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "third/construction/Bootstrap", null, "java/lang/Object", null);
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor annotation = m.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        annotation.visitEnd();
        m.visitCode();
        m.visitLdcInsn(Type.getObjectType("third/construction/Orb"));
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
