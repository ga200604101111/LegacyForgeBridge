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
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyBlockDropQuantityCompilerTest {
    @TempDir Path tempDir;

    @Test void literalQuantitiesCompileWhileRandomDependentDropCountsFailClosed() throws Exception {
        Path jar = tempDir.resolve("ForeignDropQuantity.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/dropqty/Zero.class", quantityBlock("foreign/dropqty/Zero", Opcodes.ICONST_0, 0));
            put(out, "foreign/dropqty/One.class", quantityBlock("foreign/dropqty/One", Opcodes.ICONST_1, 0));
            put(out, "foreign/dropqty/Dozen.class", quantityBlock("foreign/dropqty/Dozen", Opcodes.BIPUSH, 12));
            put(out, "foreign/dropqty/Thousand.class", quantityBlock("foreign/dropqty/Thousand", Opcodes.SIPUSH, 1000));
            put(out, "foreign/dropqty/RandomCount.class", randomQuantityBlock());
            put(out, "foreign/dropqty/Bootstrap.class", bootstrap());
        }

        var analysis = new LegacyBlockDropQuantityCompiler().compile(jar);
        Map<String, LegacyBlockDropQuantityCompiler.Rule> rules = analysis.rules().stream()
                .collect(Collectors.toMap(LegacyBlockDropQuantityCompiler.Rule::registryName, value -> value));

        assertEquals(4, rules.size(), String.join("\n", analysis.diagnostics()));
        assertEquals(0, rules.get("zero").quantity());
        assertEquals(1, rules.get("one").quantity());
        assertEquals(12, rules.get("dozen").quantity());
        assertEquals(1000, rules.get("thousand").quantity());
        assertFalse(rules.containsKey("random_count"));
        assertTrue(analysis.diagnostics().stream().anyMatch(value ->
                        value.contains("Unsupported constant drop quantity callback") && value.contains("RandomCount")),
                String.join("\n", analysis.diagnostics()));
    }

    private static byte[] quantityBlock(String owner, int opcode, int operand) {
        ClassWriter writer = blockWriter(owner);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "quantityDropped", "(Ljava/util/Random;)I", null, null);
        method.visitCode();
        if (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH) method.visitIntInsn(opcode, operand);
        else method.visitInsn(opcode);
        method.visitInsn(Opcodes.IRETURN);
        end(method);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] randomQuantityBlock() {
        String owner = "foreign/dropqty/RandomCount";
        ClassWriter writer = blockWriter(owner);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "quantityDropped", "(Ljava/util/Random;)I", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitIntInsn(Opcodes.BIPUSH, 4);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Random", "nextInt", "(I)I", false);
        method.visitInsn(Opcodes.IRETURN);
        end(method);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static ClassWriter blockWriter(String owner) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "net/minecraft/block/Block", null);
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/block/Block", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        end(init);
        return writer;
    }

    private static byte[] bootstrap() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/dropqty/Bootstrap", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor annotation = method.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        annotation.visitEnd();
        method.visitCode();
        register(method, "foreign/dropqty/Zero", "zero");
        register(method, "foreign/dropqty/One", "one");
        register(method, "foreign/dropqty/Dozen", "dozen");
        register(method, "foreign/dropqty/Thousand", "thousand");
        register(method, "foreign/dropqty/RandomCount", "random_count");
        method.visitInsn(Opcodes.RETURN);
        end(method);
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
