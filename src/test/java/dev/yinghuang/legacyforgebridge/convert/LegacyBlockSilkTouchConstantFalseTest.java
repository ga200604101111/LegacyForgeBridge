package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyBlockSilkTouchConstantFalseTest {
    @TempDir Path tempDir;

    @Test
    void exactFalseRenderOverrideProvesSilkDisabledByForgeShortCircuit() throws Exception {
        Path jar = tempDir.resolve("FalseRenderSilkProof.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/silkfalse/FalseRender.class", falseRenderBlock());
            put(out, "foreign/silkfalse/DynamicRender.class", dynamicRenderBlock());
            put(out, "foreign/silkfalse/Bootstrap.class", bootstrap());
        }

        Map<String, LegacyBlockSilkTouchAnalyzer.Proof> proofs = new LegacyBlockSilkTouchAnalyzer().analyze(jar)
                .proofs().stream().collect(Collectors.toMap(LegacyBlockSilkTouchAnalyzer.Proof::registryName, value -> value));
        assertEquals(2, proofs.size());

        var disabled = proofs.get("false_render");
        assertTrue(disabled.eligibilityProofComplete(), disabled.eligibilityReasons().toString());
        assertEquals(Boolean.FALSE, disabled.silkEligible());
        assertTrue(disabled.stackedItemProofComplete());
        assertEquals(0, disabled.stackedLegacyDamage());
        assertTrue(disabled.eligibilityReasons().isEmpty());

        var dynamic = proofs.get("dynamic_render");
        assertFalse(dynamic.eligibilityProofComplete());
        assertNull(dynamic.silkEligible());
        assertTrue(dynamic.eligibilityReasons().stream()
                .anyMatch(reason -> reason.contains("renderAsNormalBlock")), dynamic.eligibilityReasons().toString());
    }

    private static byte[] falseRenderBlock() {
        String owner = "foreign/silkfalse/FalseRender";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "net/minecraft/block/Block",
                new String[]{"foreign/external/UnknownTileish"});
        constructor(writer, owner);
        MethodVisitor render = writer.visitMethod(Opcodes.ACC_PUBLIC, "func_149686_d", "()Z", null, null);
        render.visitCode(); render.visitInsn(Opcodes.ICONST_0); render.visitInsn(Opcodes.IRETURN); end(render);
        MethodVisitor tile = writer.visitMethod(Opcodes.ACC_PUBLIC, "hasTileEntity", "(I)Z", null, null);
        tile.visitCode(); tile.visitInsn(Opcodes.ICONST_1); tile.visitInsn(Opcodes.IRETURN); end(tile);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] dynamicRenderBlock() {
        String owner = "foreign/silkfalse/DynamicRender";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "net/minecraft/block/Block", null);
        writer.visitField(Opcodes.ACC_PRIVATE, "normal", "Z", null, null).visitEnd();
        constructor(writer, owner);
        MethodVisitor render = writer.visitMethod(Opcodes.ACC_PUBLIC, "func_149686_d", "()Z", null, null);
        render.visitCode();
        render.visitVarInsn(Opcodes.ALOAD, 0);
        render.visitFieldInsn(Opcodes.GETFIELD, owner, "normal", "Z");
        render.visitInsn(Opcodes.IRETURN);
        end(render);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void constructor(ClassWriter writer, String owner) {
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitInsn(Opcodes.ACONST_NULL);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/block/Block", "<init>",
                "(Lnet/minecraft/block/material/Material;)V", false);
        init.visitInsn(Opcodes.RETURN);
        end(init);
    }

    private static byte[] bootstrap() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/silkfalse/Bootstrap", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor annotation = method.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        annotation.visitEnd();
        method.visitCode();
        register(method, "foreign/silkfalse/FalseRender", "false_render");
        register(method, "foreign/silkfalse/DynamicRender", "dynamic_render");
        method.visitInsn(Opcodes.RETURN);
        end(method);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void register(MethodVisitor method, String owner, String name) {
        method.visitTypeInsn(Opcodes.NEW, owner);
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, "<init>", "()V", false);
        method.visitLdcInsn(name);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)V", false);
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
