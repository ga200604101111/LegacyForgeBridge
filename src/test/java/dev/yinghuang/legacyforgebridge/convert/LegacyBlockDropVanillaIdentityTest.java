package dev.longyu.legacyforgebridge.convert;

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

class LegacyBlockDropVanillaIdentityTest {
    @TempDir Path tempDir;

    @Test void pinned1710ItemAndBlockFieldsBecomeLegacyMinecraftDropIdentitiesWhileUnknownFieldsFailClosed() throws Exception {
        Path jar = tempDir.resolve("ForeignVanillaDrops.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/vanilladrop/StickDrop.class", itemFieldDrop(
                    "foreign/vanilladrop/StickDrop", "net/minecraft/init/Items", "field_151055_y",
                    "Lnet/minecraft/item/Item;"));
            put(out, "foreign/vanilladrop/CobbleDrop.class", blockFieldDrop(
                    "foreign/vanilladrop/CobbleDrop", "net/minecraft/init/Blocks", "field_150347_e",
                    "Lnet/minecraft/block/Block;"));
            put(out, "foreign/vanilladrop/UnknownDrop.class", itemFieldDrop(
                    "foreign/vanilladrop/UnknownDrop", "net/minecraft/init/Items", "field_999999_x",
                    "Lnet/minecraft/item/Item;"));
            put(out, "foreign/vanilladrop/Bootstrap.class", bootstrap());
        }

        var analysis = new LegacyBlockDropItemCompiler().compile(jar);
        Map<String, LegacyBlockDropItemCompiler.Rule> rules = analysis.rules().stream()
                .collect(Collectors.toMap(LegacyBlockDropItemCompiler.Rule::registryName, value -> value));

        assertEquals(2, rules.size(), String.join("\n", analysis.diagnostics()));

        var stick = rules.get("stick_drop").target();
        assertEquals(LegacyBlockDropItemCompiler.TargetKind.ITEM, stick.kind());
        assertEquals("stick", stick.registryName());
        assertEquals("minecraft", stick.legacyNamespace());
        assertEquals("net/minecraft/init/Items", stick.sourceFieldOwner());
        assertEquals("field_151055_y", stick.sourceFieldName());

        var cobble = rules.get("cobble_drop").target();
        assertEquals(LegacyBlockDropItemCompiler.TargetKind.BLOCK_ITEM, cobble.kind());
        assertEquals("cobblestone", cobble.registryName());
        assertEquals("minecraft", cobble.legacyNamespace());
        assertEquals("net/minecraft/init/Blocks", cobble.sourceFieldOwner());
        assertEquals("field_150347_e", cobble.sourceFieldName());

        assertFalse(rules.containsKey("unknown_drop"));
        assertTrue(analysis.diagnostics().stream().anyMatch(value ->
                        value.contains("Unsupported direct drop item callback")
                                && value.contains("UnknownDrop")
                                && value.contains("no proven source or vanilla 1.7.10 item identity")),
                String.join("\n", analysis.diagnostics()));
    }

    private static byte[] itemFieldDrop(String owner, String fieldOwner, String fieldName, String descriptor) {
        ClassWriter writer = blockClass(owner);
        MethodVisitor method = dropMethod(writer);
        method.visitFieldInsn(Opcodes.GETSTATIC, fieldOwner, fieldName, descriptor);
        method.visitInsn(Opcodes.ARETURN);
        end(method);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] blockFieldDrop(String owner, String fieldOwner, String fieldName, String descriptor) {
        ClassWriter writer = blockClass(owner);
        MethodVisitor method = dropMethod(writer);
        method.visitFieldInsn(Opcodes.GETSTATIC, fieldOwner, fieldName, descriptor);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraft/item/Item", "func_150898_a",
                "(Lnet/minecraft/block/Block;)Lnet/minecraft/item/Item;", false);
        method.visitInsn(Opcodes.ARETURN);
        end(method);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static ClassWriter blockClass(String owner) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "net/minecraft/block/Block", null);
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitInsn(Opcodes.ACONST_NULL);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/block/Block", "<init>",
                "(Lnet/minecraft/block/material/Material;)V", false);
        init.visitInsn(Opcodes.RETURN);
        end(init);
        return writer;
    }

    private static MethodVisitor dropMethod(ClassWriter writer) {
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "getItemDropped",
                "(ILjava/util/Random;I)Lnet/minecraft/item/Item;", null, null);
        method.visitCode();
        return method;
    }

    private static byte[] bootstrap() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/vanilladrop/Bootstrap", null,
                "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor annotation = method.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        annotation.visitEnd();
        method.visitCode();
        register(method, "foreign/vanilladrop/StickDrop", "stick_drop");
        register(method, "foreign/vanilladrop/CobbleDrop", "cobble_drop");
        register(method, "foreign/vanilladrop/UnknownDrop", "unknown_drop");
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
