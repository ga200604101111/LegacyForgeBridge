package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyStorageBlockAnalyzerTest {
    @TempDir Path tempDir;

    @Test
    void unrelatedSixRowInventoryIsAdmittedWhileUnprovenVariantFailsClosed() throws Exception {
        Path jar = tempDir.resolve("ForeignStorage.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "unrelated/store/GoodBlock.class", block("unrelated/store/GoodBlock", "unrelated/store/GoodTile", true));
            put(out, "unrelated/store/GoodTile.class", tile("unrelated/store/GoodTile", 54));
            put(out, "unrelated/store/UnsafeBlock.class", block("unrelated/store/UnsafeBlock", "unrelated/store/UnsafeTile", false));
            put(out, "unrelated/store/UnsafeTile.class", tile("unrelated/store/UnsafeTile", 54));
            put(out, "unrelated/store/Bootstrap.class", bootstrap());
        }

        LegacyStorageBlockAnalyzer.Analysis analysis = new LegacyStorageBlockAnalyzer().analyze(jar);
        assertTrue(analysis.diagnostics().isEmpty(), String.join("\n", analysis.diagnostics()));
        assertEquals(1, analysis.rules().size());
        LegacyStorageBlockAnalyzer.Rule rule = analysis.rules().getFirst();
        assertEquals("good_storage", rule.registryName());
        assertEquals("unrelated/store/GoodBlock", rule.sourceBlockClass());
        assertEquals("unrelated/store/GoodTile", rule.sourceTileClass());
        assertEquals("good_tile", rule.legacyTileId());
        assertEquals(54, rule.slots());
        assertEquals(6, rule.rows());
        assertEquals(64, rule.stackLimit());
        assertEquals("Foreign Chest", rule.title());
        assertEquals(64.0, rule.interactionDistanceSq());
        assertTrue(rule.sneakingPass());
        assertTrue(rule.dropContents());
        assertTrue(rule.comparator());

        assertEquals(1, analysis.skipped().size());
        assertEquals("unsafe_storage", analysis.skipped().getFirst().registryName());
        assertTrue(analysis.skipped().getFirst().reason().contains("comparator"));
    }

    private static byte[] bootstrap() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "unrelated/store/Bootstrap", null, "java/lang/Object", null);
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor event = m.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true);
        event.visitEnd();
        m.visitCode();
        registerBlock(m, "unrelated/store/GoodBlock", "good_storage");
        registerBlock(m, "unrelated/store/UnsafeBlock", "unsafe_storage");
        registerTile(m, "unrelated/store/GoodTile", "good_tile");
        registerTile(m, "unrelated/store/UnsafeTile", "unsafe_tile");
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(0, 0);
        m.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static void registerBlock(MethodVisitor m, String type, String id) {
        m.visitTypeInsn(Opcodes.NEW, type);
        m.visitInsn(Opcodes.DUP);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, type, "<init>", "()V", false);
        m.visitLdcInsn(id);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)V", false);
    }

    private static void registerTile(MethodVisitor m, String type, String id) {
        m.visitLdcInsn(Type.getObjectType(type));
        m.visitLdcInsn(id);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerTileEntity",
                "(Ljava/lang/Class;Ljava/lang/String;)V", false);
    }

    private static byte[] block(String name, String tile, boolean comparator) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, name, null, "net/minecraft/block/BlockContainer", null);
        ctor(w, "net/minecraft/block/BlockContainer");

        MethodVisitor create = w.visitMethod(Opcodes.ACC_PUBLIC, "func_149915_a",
                "(Lnet/minecraft/world/World;I)Lnet/minecraft/tileentity/TileEntity;", null, null);
        create.visitCode();
        create.visitTypeInsn(Opcodes.NEW, tile); create.visitInsn(Opcodes.DUP);
        create.visitMethodInsn(Opcodes.INVOKESPECIAL, tile, "<init>", "()V", false);
        create.visitInsn(Opcodes.ARETURN); create.visitMaxs(0, 0); create.visitEnd();

        MethodVisitor use = w.visitMethod(Opcodes.ACC_PUBLIC, "func_149727_a",
                "(Lnet/minecraft/world/World;IIILnet/minecraft/entity/player/EntityPlayer;IFFF)Z", null, null);
        use.visitCode();
        use.visitVarInsn(Opcodes.ALOAD, 5);
        use.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/player/EntityPlayer", "func_70093_af", "()Z", false);
        Label notSneaking = new Label(); use.visitJumpInsn(Opcodes.IFEQ, notSneaking);
        use.visitInsn(Opcodes.ICONST_0); use.visitInsn(Opcodes.IRETURN); use.visitLabel(notSneaking);
        use.visitVarInsn(Opcodes.ALOAD, 5);
        use.visitVarInsn(Opcodes.ALOAD, 1); use.visitVarInsn(Opcodes.ILOAD, 2); use.visitVarInsn(Opcodes.ILOAD, 3); use.visitVarInsn(Opcodes.ILOAD, 4);
        use.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/World", "func_147438_o",
                "(III)Lnet/minecraft/tileentity/TileEntity;", false);
        use.visitTypeInsn(Opcodes.CHECKCAST, "net/minecraft/inventory/IInventory");
        use.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/player/EntityPlayer", "func_71007_a",
                "(Lnet/minecraft/inventory/IInventory;)V", false);
        use.visitInsn(Opcodes.ICONST_1); use.visitInsn(Opcodes.IRETURN); use.visitMaxs(0, 0); use.visitEnd();

        MethodVisitor drop = w.visitMethod(Opcodes.ACC_PUBLIC, "func_149749_a",
                "(Lnet/minecraft/world/World;IIILnet/minecraft/block/Block;I)V", null, null);
        drop.visitCode();
        drop.visitVarInsn(Opcodes.ALOAD, 1); drop.visitVarInsn(Opcodes.ILOAD, 2); drop.visitVarInsn(Opcodes.ILOAD, 3); drop.visitVarInsn(Opcodes.ILOAD, 4);
        drop.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/World", "func_147438_o",
                "(III)Lnet/minecraft/tileentity/TileEntity;", false); drop.visitInsn(Opcodes.POP);
        drop.visitTypeInsn(Opcodes.NEW, "net/minecraft/entity/item/EntityItem"); drop.visitInsn(Opcodes.POP);
        drop.visitVarInsn(Opcodes.ALOAD, 1); drop.visitInsn(Opcodes.ACONST_NULL);
        drop.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/World", "func_72838_d",
                "(Lnet/minecraft/entity/Entity;)Z", false); drop.visitInsn(Opcodes.POP);
        drop.visitInsn(Opcodes.RETURN); drop.visitMaxs(0, 0); drop.visitEnd();

        MethodVisitor flag = w.visitMethod(Opcodes.ACC_PUBLIC, "func_149740_M", "()Z", null, null);
        flag.visitCode(); flag.visitInsn(comparator ? Opcodes.ICONST_1 : Opcodes.ICONST_0); flag.visitInsn(Opcodes.IRETURN); flag.visitMaxs(0, 0); flag.visitEnd();
        MethodVisitor value = w.visitMethod(Opcodes.ACC_PUBLIC, "func_149736_g",
                "(Lnet/minecraft/world/World;IIII)I", null, null);
        value.visitCode(); value.visitInsn(Opcodes.ACONST_NULL);
        value.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraft/inventory/Container", "func_94526_b",
                "(Lnet/minecraft/inventory/IInventory;)I", false); value.visitInsn(Opcodes.IRETURN); value.visitMaxs(0, 0); value.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static byte[] tile(String name, int slots) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, name, null, "net/minecraft/tileentity/TileEntity",
                new String[]{"net/minecraft/inventory/IInventory"});
        ctor(w, "net/minecraft/tileentity/TileEntity");
        intReturn(w, "func_70302_i_", slots);
        intReturn(w, "func_70297_j_", 64);
        stringReturn(w, "func_145825_b", "Foreign Chest");
        intReturn(w, "func_145818_k_", 0);

        MethodVisitor read = w.visitMethod(Opcodes.ACC_PUBLIC, "func_145839_a", "(Lnet/minecraft/nbt/NBTTagCompound;)V", null, null);
        read.visitCode();
        read.visitVarInsn(Opcodes.ALOAD, 1); read.visitLdcInsn("Items"); read.visitIntInsn(Opcodes.BIPUSH, 10);
        read.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/nbt/NBTTagCompound", "func_150295_c",
                "(Ljava/lang/String;I)Lnet/minecraft/nbt/NBTTagList;", false); read.visitInsn(Opcodes.POP);
        read.visitVarInsn(Opcodes.ALOAD, 1); read.visitLdcInsn("Slot");
        read.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/nbt/NBTTagCompound", "func_74771_c", "(Ljava/lang/String;)B", false); read.visitInsn(Opcodes.POP);
        read.visitInsn(Opcodes.ACONST_NULL); read.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraft/item/ItemStack", "func_77949_a",
                "(Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/item/ItemStack;", false); read.visitInsn(Opcodes.POP);
        read.visitInsn(Opcodes.RETURN); read.visitMaxs(0, 0); read.visitEnd();

        MethodVisitor write = w.visitMethod(Opcodes.ACC_PUBLIC, "func_145841_b", "(Lnet/minecraft/nbt/NBTTagCompound;)V", null, null);
        write.visitCode();
        write.visitVarInsn(Opcodes.ALOAD, 1); write.visitLdcInsn("Slot"); write.visitInsn(Opcodes.ICONST_0);
        write.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/nbt/NBTTagCompound", "func_74774_a", "(Ljava/lang/String;B)V", false);
        write.visitInsn(Opcodes.ACONST_NULL); write.visitInsn(Opcodes.ACONST_NULL);
        write.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/item/ItemStack", "func_77955_b",
                "(Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/nbt/NBTTagCompound;", false); write.visitInsn(Opcodes.POP);
        write.visitInsn(Opcodes.ACONST_NULL); write.visitInsn(Opcodes.ACONST_NULL);
        write.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/nbt/NBTTagList", "func_74742_a", "(Lnet/minecraft/nbt/NBTBase;)V", false);
        write.visitVarInsn(Opcodes.ALOAD, 1); write.visitLdcInsn("Items"); write.visitInsn(Opcodes.ACONST_NULL);
        write.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/nbt/NBTTagCompound", "func_74782_a",
                "(Ljava/lang/String;Lnet/minecraft/nbt/NBTBase;)V", false);
        write.visitInsn(Opcodes.RETURN); write.visitMaxs(0, 0); write.visitEnd();

        MethodVisitor usable = w.visitMethod(Opcodes.ACC_PUBLIC, "func_70300_a", "(Lnet/minecraft/entity/player/EntityPlayer;)Z", null, null);
        usable.visitCode(); usable.visitInsn(Opcodes.ACONST_NULL); usable.visitInsn(Opcodes.ICONST_0); usable.visitInsn(Opcodes.ICONST_0); usable.visitInsn(Opcodes.ICONST_0);
        usable.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/World", "func_147438_o",
                "(III)Lnet/minecraft/tileentity/TileEntity;", false); usable.visitVarInsn(Opcodes.ALOAD, 0);
        Label same = new Label(); usable.visitJumpInsn(Opcodes.IF_ACMPEQ, same); usable.visitInsn(Opcodes.ICONST_0); usable.visitInsn(Opcodes.IRETURN); usable.visitLabel(same);
        usable.visitVarInsn(Opcodes.ALOAD, 1); usable.visitLdcInsn(0.5D); usable.visitLdcInsn(0.5D); usable.visitLdcInsn(0.5D);
        usable.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/player/EntityPlayer", "func_70092_e", "(DDD)D", false);
        usable.visitLdcInsn(64.0D); usable.visitInsn(Opcodes.DCMPG); Label far = new Label(); usable.visitJumpInsn(Opcodes.IFGT, far);
        usable.visitInsn(Opcodes.ICONST_1); usable.visitInsn(Opcodes.IRETURN); usable.visitLabel(far); usable.visitInsn(Opcodes.ICONST_0); usable.visitInsn(Opcodes.IRETURN);
        usable.visitMaxs(0, 0); usable.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    private static void ctor(ClassWriter w, String parent) {
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        m.visitCode(); m.visitVarInsn(Opcodes.ALOAD, 0); m.visitMethodInsn(Opcodes.INVOKESPECIAL, parent, "<init>", "()V", false); m.visitInsn(Opcodes.RETURN); m.visitMaxs(0, 0); m.visitEnd();
    }
    private static void intReturn(ClassWriter w, String name, int value) {
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, name, name.equals("func_145818_k_") ? "()Z" : "()I", null, null);
        m.visitCode(); if (value >= -1 && value <= 5) m.visitInsn(Opcodes.ICONST_0 + value); else m.visitIntInsn(Opcodes.BIPUSH, value); m.visitInsn(Opcodes.IRETURN); m.visitMaxs(0, 0); m.visitEnd();
    }
    private static void stringReturn(ClassWriter w, String name, String value) {
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, name, "()Ljava/lang/String;", null, null); m.visitCode(); m.visitLdcInsn(value); m.visitInsn(Opcodes.ARETURN); m.visitMaxs(0, 0); m.visitEnd();
    }
    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception { out.putNextEntry(new JarEntry(name)); out.write(bytes); out.closeEntry(); }
}
