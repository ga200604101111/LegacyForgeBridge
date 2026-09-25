package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyBlockSilkTouchScopedMutationTest {
    @TempDir Path tempDir;

    @Test
    void unrelatedItemBlockConstructorSubtypeMutationDoesNotPoisonDefaultBlockItems() throws Exception {
        Path jar = tempDir.resolve("ScopedSubtype.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/scoped/Plain.class", plainBlock());
            put(out, "foreign/scoped/LocalSubtypeItemBlock.class", localSubtypeItemBlock());
            put(out, "foreign/scoped/Bootstrap.class", bootstrap());
        }

        var analysis = new LegacyBlockSilkTouchAnalyzer().analyze(jar);
        assertEquals(1, analysis.proofs().size());
        var proof = analysis.proofs().getFirst();
        assertEquals("plain", proof.registryName());
        assertTrue(proof.eligibilityProofComplete(), proof.eligibilityReasons().toString());
        assertEquals(Boolean.TRUE, proof.silkEligible());
        assertTrue(proof.stackedItemProofComplete(), proof.stackedItemReasons().toString());
        assertEquals(0, proof.stackedLegacyDamage());
    }

    private static byte[] plainBlock() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/scoped/Plain", null,
                "net/minecraft/block/Block", null);
        MethodVisitor ctor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitInsn(Opcodes.ACONST_NULL);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/block/Block", "<init>",
                "(Lnet/minecraft/block/material/Material;)V", false);
        ctor.visitInsn(Opcodes.RETURN);
        end(ctor);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] localSubtypeItemBlock() {
        String owner = "foreign/scoped/LocalSubtypeItemBlock";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "net/minecraft/item/ItemBlock", null);
        MethodVisitor ctor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                "(Lnet/minecraft/block/Block;)V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitVarInsn(Opcodes.ALOAD, 1);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/item/ItemBlock", "<init>",
                "(Lnet/minecraft/block/Block;)V", false);
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitInsn(Opcodes.ICONST_1);
        ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/item/Item", "func_77627_a",
                "(Z)Lnet/minecraft/item/Item;", false);
        ctor.visitInsn(Opcodes.POP);
        ctor.visitInsn(Opcodes.RETURN);
        end(ctor);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] bootstrap() {
        String owner = "foreign/scoped/Bootstrap";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor annotation = method.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        annotation.visitEnd();
        method.visitCode();
        method.visitTypeInsn(Opcodes.NEW, "foreign/scoped/Plain");
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "foreign/scoped/Plain", "<init>", "()V", false);
        method.visitLdcInsn("plain");
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)V", false);
        method.visitInsn(Opcodes.RETURN);
        end(method);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void end(MethodVisitor method) {
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name));
        out.write(bytes);
        out.closeEntry();
    }
}
