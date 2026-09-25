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
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyBlockDropMetadataCompilerTest {
    @TempDir Path tempDir;

    @Test void pureDamageDroppedCallbacksCollapseToCompleteMetadataTablesAcrossUnrelatedNamespace() throws Exception {
        Path jar = tempDir.resolve("ForeignDrops.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/drop/IdentityDrop.class", block("foreign/drop/IdentityDrop", method -> {
                method.visitVarInsn(Opcodes.ILOAD, 1);
                method.visitInsn(Opcodes.IRETURN);
            }));
            put(out, "foreign/drop/MaskDrop.class", block("foreign/drop/MaskDrop", method -> {
                method.visitVarInsn(Opcodes.ILOAD, 1);
                method.visitIntInsn(Opcodes.BIPUSH, 7);
                method.visitInsn(Opcodes.IAND);
                method.visitInsn(Opcodes.IRETURN);
            }));
            put(out, "foreign/drop/ConstantDrop.class", block("foreign/drop/ConstantDrop", method -> {
                method.visitInsn(Opcodes.ICONST_0);
                method.visitInsn(Opcodes.IRETURN);
            }));
            put(out, "foreign/drop/UnsafeDrop.class", unsafeBlock());
            put(out, "foreign/drop/Bootstrap.class", bootstrap());
        }

        var analysis = new LegacyBlockDropMetadataCompiler().compile(jar);
        Map<String, LegacyBlockDropMetadataCompiler.Rule> rules = analysis.rules().stream()
                .collect(Collectors.toMap(LegacyBlockDropMetadataCompiler.Rule::registryName, value -> value));

        assertEquals(3, rules.size());
        assertEquals(0, rules.get("identity_drop").itemDamage(0));
        assertEquals(15, rules.get("identity_drop").itemDamage(15));
        assertEquals(7, rules.get("mask_drop").itemDamage(15));
        assertEquals(3, rules.get("mask_drop").itemDamage(11));
        assertEquals(0, rules.get("constant_drop").itemDamage(15));
        assertEquals(16, rules.get("mask_drop").itemDamageByBlockMeta().size());

        assertFalse(rules.containsKey("unsafe_drop"));
        assertTrue(analysis.diagnostics().stream().anyMatch(value ->
                        value.contains("Unsupported pure drop metadata callback") && value.contains("UnsafeDrop")),
                String.join("\n", analysis.diagnostics()));
    }

    private static byte[] block(String name, Consumer<MethodVisitor> damageBody) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, name, null, "net/minecraft/block/Block", null);
        constructor(writer, name);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "damageDropped", "(I)I", null, null);
        method.visitCode();
        damageBody.accept(method);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] unsafeBlock() {
        String name = "foreign/drop/UnsafeDrop";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, name, null, "net/minecraft/block/Block", null);
        constructor(writer, name);

        MethodVisitor helper = writer.visitMethod(Opcodes.ACC_PRIVATE, "normalize", "(I)I", null, null);
        helper.visitCode();
        helper.visitVarInsn(Opcodes.ILOAD, 1);
        helper.visitInsn(Opcodes.IRETURN);
        helper.visitMaxs(0, 0);
        helper.visitEnd();

        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "damageDropped", "(I)I", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ILOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, name, "normalize", "(I)I", false);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void constructor(ClassWriter writer, String name) {
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/block/Block", "<init>", "()V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static byte[] bootstrap() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/drop/Bootstrap", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor annotation = method.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        annotation.visitEnd();
        method.visitCode();
        register(method, "foreign/drop/IdentityDrop", "identity_drop");
        register(method, "foreign/drop/MaskDrop", "mask_drop");
        register(method, "foreign/drop/ConstantDrop", "constant_drop");
        register(method, "foreign/drop/UnsafeDrop", "unsafe_drop");
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void register(MethodVisitor method, String owner, String registryName) {
        method.visitTypeInsn(Opcodes.NEW, owner);
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, "<init>", "()V", false);
        method.visitLdcInsn(registryName);
        method.visitMethodInsn(Opcodes.INVOKESTATIC,
                "cpw/mods/fml/common/registry/GameRegistry",
                "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)V",
                false);
    }

    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name));
        out.write(bytes);
        out.closeEntry();
    }
}
