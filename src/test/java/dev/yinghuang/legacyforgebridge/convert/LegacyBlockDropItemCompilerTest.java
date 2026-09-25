package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
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

class LegacyBlockDropItemCompilerTest {
    @TempDir Path tempDir;

    @Test void directItemBlockItemAndNoDropShapesRequireProvenRegistryBindings() throws Exception {
        Path jar = tempDir.resolve("ForeignDropItems.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/dropitem/DropIngredient.class", simpleItem());
            put(out, "foreign/dropitem/DropSourceBlock.class", simpleBlock("foreign/dropitem/DropSourceBlock"));
            put(out, "foreign/dropitem/DirectItemDrop.class", directItemDrop());
            put(out, "foreign/dropitem/BlockItemDrop.class", blockItemDrop());
            put(out, "foreign/dropitem/NullDrop.class", nullDrop());
            put(out, "foreign/dropitem/ZeroDrop.class", zeroDrop());
            put(out, "foreign/dropitem/UnsafeDrop.class", unsafeDrop());
            put(out, "foreign/dropitem/Bootstrap.class", bootstrap());
        }

        var analysis = new LegacyBlockDropItemCompiler().compile(jar);
        Map<String, LegacyBlockDropItemCompiler.Rule> rules = analysis.rules().stream()
                .collect(Collectors.toMap(LegacyBlockDropItemCompiler.Rule::registryName, value -> value));

        assertEquals(4, rules.size(), String.join("\n", analysis.diagnostics()));

        var direct = rules.get("direct_item_drop").target();
        assertEquals(LegacyBlockDropItemCompiler.TargetKind.ITEM, direct.kind());
        assertEquals("drop_ingredient", direct.registryName());
        assertEquals("foreign/dropitem/Bootstrap", direct.sourceFieldOwner());
        assertEquals("dropItem", direct.sourceFieldName());

        var blockItem = rules.get("block_item_drop").target();
        assertEquals(LegacyBlockDropItemCompiler.TargetKind.BLOCK_ITEM, blockItem.kind());
        assertEquals("drop_source", blockItem.registryName());
        assertEquals("foreign/dropitem/Bootstrap", blockItem.sourceFieldOwner());
        assertEquals("dropBlock", blockItem.sourceFieldName());

        assertEquals(LegacyBlockDropItemCompiler.TargetKind.NONE, rules.get("null_drop").target().kind());
        assertEquals(LegacyBlockDropItemCompiler.TargetKind.NONE, rules.get("zero_drop").target().kind());
        assertFalse(rules.containsKey("unsafe_drop"));
        assertTrue(analysis.diagnostics().stream().anyMatch(value ->
                        value.contains("Unsupported direct drop item callback") && value.contains("UnsafeDrop")),
                String.join("\n", analysis.diagnostics()));
    }

    private static byte[] simpleItem() {
        String owner = "foreign/dropitem/DropIngredient";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "net/minecraft/item/Item", null);
        noArgConstructor(writer, "net/minecraft/item/Item");
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] simpleBlock(String owner) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "net/minecraft/block/Block", null);
        noArgConstructor(writer, "net/minecraft/block/Block");
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] directItemDrop() {
        String owner = "foreign/dropitem/DirectItemDrop";
        ClassWriter writer = blockWriter(owner);
        MethodVisitor method = dropMethod(writer);
        method.visitFieldInsn(Opcodes.GETSTATIC, "foreign/dropitem/Bootstrap", "dropItem", "Lnet/minecraft/item/Item;");
        method.visitInsn(Opcodes.ARETURN);
        end(method);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] blockItemDrop() {
        String owner = "foreign/dropitem/BlockItemDrop";
        ClassWriter writer = blockWriter(owner);
        MethodVisitor method = dropMethod(writer);
        method.visitFieldInsn(Opcodes.GETSTATIC, "foreign/dropitem/Bootstrap", "dropBlock", "Lnet/minecraft/block/Block;");
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraft/item/Item", "func_150898_a",
                "(Lnet/minecraft/block/Block;)Lnet/minecraft/item/Item;", false);
        method.visitInsn(Opcodes.ARETURN);
        end(method);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] nullDrop() {
        String owner = "foreign/dropitem/NullDrop";
        ClassWriter writer = blockWriter(owner);
        MethodVisitor method = dropMethod(writer);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ARETURN);
        end(method);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] zeroDrop() {
        String owner = "foreign/dropitem/ZeroDrop";
        ClassWriter writer = blockWriter(owner);
        MethodVisitor method = dropMethod(writer);
        method.visitInsn(Opcodes.ICONST_0);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraft/item/Item", "func_150899_d",
                "(I)Lnet/minecraft/item/Item;", false);
        method.visitInsn(Opcodes.ARETURN);
        end(method);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] unsafeDrop() {
        String owner = "foreign/dropitem/UnsafeDrop";
        ClassWriter writer = blockWriter(owner);
        MethodVisitor method = dropMethod(writer);
        Label nonzero = new Label();
        method.visitVarInsn(Opcodes.ILOAD, 1);
        method.visitJumpInsn(Opcodes.IFNE, nonzero);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ARETURN);
        method.visitLabel(nonzero);
        method.visitFieldInsn(Opcodes.GETSTATIC, "foreign/dropitem/Bootstrap", "dropItem", "Lnet/minecraft/item/Item;");
        method.visitInsn(Opcodes.ARETURN);
        end(method);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static ClassWriter blockWriter(String owner) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "net/minecraft/block/Block", null);
        noArgConstructor(writer, "net/minecraft/block/Block");
        return writer;
    }

    private static MethodVisitor dropMethod(ClassWriter writer) {
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "getItemDropped",
                "(ILjava/util/Random;I)Lnet/minecraft/item/Item;", null, null);
        method.visitCode();
        return method;
    }

    private static void end(MethodVisitor method) {
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static void noArgConstructor(ClassWriter writer, String parent) {
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, parent, "<init>", "()V", false);
        method.visitInsn(Opcodes.RETURN);
        end(method);
    }

    private static byte[] bootstrap() {
        String owner = "foreign/dropitem/Bootstrap";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "dropItem", "Lnet/minecraft/item/Item;", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "dropBlock", "Lnet/minecraft/block/Block;", null, null).visitEnd();
        noArgConstructor(writer, "java/lang/Object");
        itemHelper(writer, owner);
        blockHelper(writer, owner);

        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor annotation = method.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        annotation.visitEnd();
        method.visitCode();

        method.visitTypeInsn(Opcodes.NEW, "foreign/dropitem/DropIngredient");
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "foreign/dropitem/DropIngredient", "<init>", "()V", false);
        method.visitLdcInsn("drop_ingredient");
        method.visitMethodInsn(Opcodes.INVOKESTATIC, owner, "item",
                "(Lnet/minecraft/item/Item;Ljava/lang/String;)Lnet/minecraft/item/Item;", false);
        method.visitFieldInsn(Opcodes.PUTSTATIC, owner, "dropItem", "Lnet/minecraft/item/Item;");

        method.visitTypeInsn(Opcodes.NEW, "foreign/dropitem/DropSourceBlock");
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "foreign/dropitem/DropSourceBlock", "<init>", "()V", false);
        method.visitLdcInsn("drop_source");
        method.visitMethodInsn(Opcodes.INVOKESTATIC, owner, "block",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)Lnet/minecraft/block/Block;", false);
        method.visitFieldInsn(Opcodes.PUTSTATIC, owner, "dropBlock", "Lnet/minecraft/block/Block;");

        registerBehaviorBlock(method, owner, "foreign/dropitem/DirectItemDrop", "direct_item_drop");
        registerBehaviorBlock(method, owner, "foreign/dropitem/BlockItemDrop", "block_item_drop");
        registerBehaviorBlock(method, owner, "foreign/dropitem/NullDrop", "null_drop");
        registerBehaviorBlock(method, owner, "foreign/dropitem/ZeroDrop", "zero_drop");
        registerBehaviorBlock(method, owner, "foreign/dropitem/UnsafeDrop", "unsafe_drop");

        method.visitInsn(Opcodes.RETURN);
        end(method);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void itemHelper(ClassWriter writer, String owner) {
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "item",
                "(Lnet/minecraft/item/Item;Ljava/lang/String;)Lnet/minecraft/item/Item;", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerItem",
                "(Lnet/minecraft/item/Item;Ljava/lang/String;)V", false);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitInsn(Opcodes.ARETURN);
        end(method);
    }

    private static void blockHelper(ClassWriter writer, String owner) {
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "block",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)Lnet/minecraft/block/Block;", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)V", false);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitInsn(Opcodes.ARETURN);
        end(method);
    }

    private static void registerBehaviorBlock(MethodVisitor method, String helperOwner,
                                              String implementation, String registryName) {
        method.visitTypeInsn(Opcodes.NEW, implementation);
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, implementation, "<init>", "()V", false);
        method.visitLdcInsn(registryName);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, helperOwner, "block",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)Lnet/minecraft/block/Block;", false);
        method.visitInsn(Opcodes.POP);
    }

    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name));
        out.write(bytes);
        out.closeEntry();
    }
}
