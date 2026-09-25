package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacySingleInputProcessorAnalyzerTest {
    @TempDir Path tempDir;

    @Test
    void unrelatedThreeSlotProcessorIsAdmittedFromStructureRatherThanNames() throws Exception {
        Path jar = tempDir.resolve("ForeignMachine.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            put(out, "foreign/machine/Bootstrap.class", bootstrap());
            put(out, "foreign/machine/MachineBlock.class", block());
            put(out, "foreign/machine/MachineTile.class", tile());
            put(out, "foreign/machine/RecipeManager.class", manager());
            put(out, "foreign/machine/Recipe.class", simple("foreign/machine/Recipe", "java/lang/Object", null));
            put(out, "foreign/machine/Handler.class", handler());
            put(out, "foreign/machine/MachineMenu.class", simple("foreign/machine/MachineMenu", "net/minecraft/inventory/Container", null));
        }

        var analysis = new LegacySingleInputProcessorAnalyzer().analyze(jar);
        assertTrue(analysis.diagnostics().isEmpty(), analysis.diagnostics().toString());
        assertEquals(1, analysis.rules().size(), analysis.skipped().toString());
        var rule = analysis.rules().getFirst();
        assertEquals("processor", rule.registryName());
        assertEquals("foreign/machine/MachineBlock", rule.sourceBlockClass());
        assertEquals("foreign/machine/MachineTile", rule.sourceTileClass());
        assertEquals("processor_tile", rule.legacyTileId());
        assertEquals(3, rule.slots());
        assertEquals(64, rule.stackLimit());
        assertEquals(0, rule.inputSlot());
        assertEquals(List.of(1, 2), rule.outputSlots());
        assertEquals(List.of(0), rule.topSlots());
        assertEquals(List.of(2, 1), rule.bottomSlots());
        assertEquals(List.of(0), rule.sideSlots());
        assertEquals(400, rule.processTicks());
        assertEquals(64.0, rule.interactionDistanceSq());
        assertEquals(7, rule.guiId());
        assertEquals("foreign/machine/RecipeManager", rule.recipeManagerOwner());
        assertEquals("lookup", rule.recipeLookupName());
        assertTrue(rule.comparator());
        assertTrue(rule.dropContents());
        assertFalse(rule.legacyEnergyApiPresent());
    }

    private static byte[] bootstrap() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/machine/Bootstrap", null, "java/lang/Object", null);
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "boot",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V", null, null);
        AnnotationVisitor a = m.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true); a.visitEnd();
        m.visitCode();
        m.visitTypeInsn(Opcodes.NEW, "foreign/machine/MachineBlock"); m.visitInsn(Opcodes.DUP);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, "foreign/machine/MachineBlock", "<init>", "()V", false);
        m.visitLdcInsn("processor");
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)V", false);
        m.visitLdcInsn(Type.getObjectType("foreign/machine/MachineTile")); m.visitLdcInsn("processor_tile");
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "cpw/mods/fml/common/registry/GameRegistry", "registerTileEntity",
                "(Ljava/lang/Class;Ljava/lang/String;)V", false);
        m.visitFieldInsn(Opcodes.GETSTATIC, "cpw/mods/fml/common/network/NetworkRegistry", "INSTANCE",
                "Lcpw/mods/fml/common/network/NetworkRegistry;");
        m.visitInsn(Opcodes.ACONST_NULL);
        m.visitTypeInsn(Opcodes.NEW, "foreign/machine/Handler"); m.visitInsn(Opcodes.DUP);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, "foreign/machine/Handler", "<init>", "()V", false);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "cpw/mods/fml/common/network/NetworkRegistry", "registerGuiHandler",
                "(Ljava/lang/Object;Lcpw/mods/fml/common/network/IGuiHandler;)V", false);
        m.visitInsn(Opcodes.RETURN); m.visitMaxs(0, 0); m.visitEnd();
        w.visitEnd(); return w.toByteArray();
    }

    private static byte[] block() {
        String name = "foreign/machine/MachineBlock";
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, name, null, "net/minecraft/block/BlockContainer", null);
        MethodVisitor ctor = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode(); ctor.visitVarInsn(Opcodes.ALOAD, 0); ctor.visitInsn(Opcodes.ACONST_NULL);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/block/BlockContainer", "<init>",
                "(Lnet/minecraft/block/material/Material;)V", false); ctor.visitInsn(Opcodes.RETURN); ctor.visitMaxs(0, 0); ctor.visitEnd();
        MethodVisitor create = w.visitMethod(Opcodes.ACC_PUBLIC, "func_149915_a",
                "(Lnet/minecraft/world/World;I)Lnet/minecraft/tileentity/TileEntity;", null, null);
        create.visitCode(); create.visitTypeInsn(Opcodes.NEW, "foreign/machine/MachineTile"); create.visitInsn(Opcodes.DUP);
        create.visitMethodInsn(Opcodes.INVOKESPECIAL, "foreign/machine/MachineTile", "<init>", "()V", false);
        create.visitInsn(Opcodes.ARETURN); create.visitMaxs(0, 0); create.visitEnd();
        MethodVisitor use = w.visitMethod(Opcodes.ACC_PUBLIC, "func_149727_a",
                "(Lnet/minecraft/world/World;IIILnet/minecraft/entity/player/EntityPlayer;IFFF)Z", null, null);
        use.visitCode(); use.visitVarInsn(Opcodes.ALOAD, 5); use.visitInsn(Opcodes.ACONST_NULL); push(use, 7);
        use.visitVarInsn(Opcodes.ALOAD, 1); use.visitVarInsn(Opcodes.ILOAD, 2); use.visitVarInsn(Opcodes.ILOAD, 3); use.visitVarInsn(Opcodes.ILOAD, 4);
        use.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/player/EntityPlayer", "openGui",
                "(Ljava/lang/Object;ILnet/minecraft/world/World;III)V", false);
        use.visitInsn(Opcodes.ICONST_1); use.visitInsn(Opcodes.IRETURN); use.visitMaxs(0, 0); use.visitEnd();
        MethodVisitor br = w.visitMethod(Opcodes.ACC_PUBLIC, "func_149749_a",
                "(Lnet/minecraft/world/World;IIILnet/minecraft/block/Block;I)V", null, null);
        br.visitCode(); br.visitVarInsn(Opcodes.ALOAD, 1); br.visitVarInsn(Opcodes.ILOAD, 2); br.visitVarInsn(Opcodes.ILOAD, 3); br.visitVarInsn(Opcodes.ILOAD, 4);
        br.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/World", "func_147438_o", "(III)Lnet/minecraft/tileentity/TileEntity;", false); br.visitInsn(Opcodes.POP);
        br.visitTypeInsn(Opcodes.NEW, "net/minecraft/entity/item/EntityItem"); br.visitInsn(Opcodes.DUP);
        br.visitVarInsn(Opcodes.ALOAD, 1); br.visitInsn(Opcodes.DCONST_0); br.visitInsn(Opcodes.DCONST_0); br.visitInsn(Opcodes.DCONST_0); br.visitInsn(Opcodes.ACONST_NULL);
        br.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/entity/item/EntityItem", "<init>",
                "(Lnet/minecraft/world/World;DDDLnet/minecraft/item/ItemStack;)V", false);
        br.visitVarInsn(Opcodes.ASTORE, 7); br.visitVarInsn(Opcodes.ALOAD, 1); br.visitVarInsn(Opcodes.ALOAD, 7);
        br.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/World", "func_72838_d", "(Lnet/minecraft/entity/Entity;)Z", false); br.visitInsn(Opcodes.POP); br.visitInsn(Opcodes.RETURN); br.visitMaxs(0, 0); br.visitEnd();
        intReturn(w, "func_149740_M", "()Z", 1);
        MethodVisitor cmp = w.visitMethod(Opcodes.ACC_PUBLIC, "func_149736_g", "(Lnet/minecraft/world/World;IIII)I", null, null);
        cmp.visitCode(); cmp.visitVarInsn(Opcodes.ALOAD, 1); cmp.visitVarInsn(Opcodes.ILOAD, 2); cmp.visitVarInsn(Opcodes.ILOAD, 3); cmp.visitVarInsn(Opcodes.ILOAD, 4);
        cmp.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/World", "func_147438_o", "(III)Lnet/minecraft/tileentity/TileEntity;", false);
        cmp.visitTypeInsn(Opcodes.CHECKCAST, "net/minecraft/inventory/IInventory");
        cmp.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraft/inventory/Container", "func_94526_b", "(Lnet/minecraft/inventory/IInventory;)I", false);
        cmp.visitInsn(Opcodes.IRETURN); cmp.visitMaxs(0, 0); cmp.visitEnd();
        w.visitEnd(); return w.toByteArray();
    }

    private static byte[] tile() {
        String name = "foreign/machine/MachineTile";
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, name, null, "net/minecraft/tileentity/TileEntity",
                new String[]{"net/minecraft/inventory/ISidedInventory"});
        w.visitField(Opcodes.ACC_PRIVATE, "slots", "[Lnet/minecraft/item/ItemStack;", null, null).visitEnd();
        w.visitField(Opcodes.ACC_PRIVATE, "progress", "I", null, null).visitEnd();
        w.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "top", "[I", null, null).visitEnd();
        w.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "bottom", "[I", null, null).visitEnd();
        w.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "sides", "[I", null, null).visitEnd();
        MethodVisitor ctor = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null); ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0); ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/tileentity/TileEntity", "<init>", "()V", false);
        ctor.visitVarInsn(Opcodes.ALOAD, 0); ctor.visitInsn(Opcodes.ICONST_3); ctor.visitTypeInsn(Opcodes.ANEWARRAY, "net/minecraft/item/ItemStack");
        ctor.visitFieldInsn(Opcodes.PUTFIELD, name, "slots", "[Lnet/minecraft/item/ItemStack;"); ctor.visitInsn(Opcodes.RETURN); ctor.visitMaxs(0, 0); ctor.visitEnd();
        intReturn(w, "func_70297_j_", "()I", 64);
        MethodVisitor valid = w.visitMethod(Opcodes.ACC_PUBLIC, "func_94041_b", "(ILnet/minecraft/item/ItemStack;)Z", null, null); valid.visitCode();
        Label no = new Label(); valid.visitVarInsn(Opcodes.ILOAD, 1); valid.visitInsn(Opcodes.ICONST_1); valid.visitJumpInsn(Opcodes.IF_ICMPEQ, no);
        valid.visitVarInsn(Opcodes.ILOAD, 1); valid.visitInsn(Opcodes.ICONST_2); valid.visitJumpInsn(Opcodes.IF_ICMPEQ, no);
        valid.visitInsn(Opcodes.ICONST_1); valid.visitInsn(Opcodes.IRETURN); valid.visitLabel(no); valid.visitInsn(Opcodes.ICONST_0); valid.visitInsn(Opcodes.IRETURN); valid.visitMaxs(0, 0); valid.visitEnd();
        MethodVisitor access = w.visitMethod(Opcodes.ACC_PUBLIC, "func_94128_d", "(I)[I", null, null); access.visitCode();
        Label notBottom = new Label(), notTop = new Label(); access.visitVarInsn(Opcodes.ILOAD, 1); access.visitJumpInsn(Opcodes.IFNE, notBottom);
        access.visitFieldInsn(Opcodes.GETSTATIC, name, "bottom", "[I"); access.visitInsn(Opcodes.ARETURN); access.visitLabel(notBottom);
        access.visitVarInsn(Opcodes.ILOAD, 1); access.visitInsn(Opcodes.ICONST_1); access.visitJumpInsn(Opcodes.IF_ICMPNE, notTop);
        access.visitFieldInsn(Opcodes.GETSTATIC, name, "top", "[I"); access.visitInsn(Opcodes.ARETURN); access.visitLabel(notTop);
        access.visitFieldInsn(Opcodes.GETSTATIC, name, "sides", "[I"); access.visitInsn(Opcodes.ARETURN); access.visitMaxs(0, 0); access.visitEnd();
        MethodVisitor insert = w.visitMethod(Opcodes.ACC_PUBLIC, "func_102007_a", "(ILnet/minecraft/item/ItemStack;I)Z", null, null); insert.visitCode();
        insert.visitVarInsn(Opcodes.ALOAD, 0); insert.visitVarInsn(Opcodes.ILOAD, 1); insert.visitVarInsn(Opcodes.ALOAD, 2);
        insert.visitMethodInsn(Opcodes.INVOKEVIRTUAL, name, "func_94041_b", "(ILnet/minecraft/item/ItemStack;)Z", false); insert.visitInsn(Opcodes.IRETURN); insert.visitMaxs(0, 0); insert.visitEnd();
        usable(w, name); nbt(w); tick(w, name); lookupHelper(w); clinit(w, name);
        w.visitEnd(); return w.toByteArray();
    }

    private static void usable(ClassWriter w, String name) {
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "func_70300_a", "(Lnet/minecraft/entity/player/EntityPlayer;)Z", null, null); m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD, 0); m.visitFieldInsn(Opcodes.GETFIELD, "net/minecraft/tileentity/TileEntity", "field_145850_b", "Lnet/minecraft/world/World;");
        m.visitInsn(Opcodes.ICONST_0); m.visitInsn(Opcodes.ICONST_0); m.visitInsn(Opcodes.ICONST_0);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/World", "func_147438_o", "(III)Lnet/minecraft/tileentity/TileEntity;", false);
        m.visitVarInsn(Opcodes.ALOAD, 0); Label same = new Label(); m.visitJumpInsn(Opcodes.IF_ACMPEQ, same); m.visitInsn(Opcodes.ICONST_0); m.visitInsn(Opcodes.IRETURN); m.visitLabel(same);
        m.visitVarInsn(Opcodes.ALOAD, 1); m.visitLdcInsn(0.5D); m.visitLdcInsn(0.5D); m.visitLdcInsn(0.5D);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/entity/player/EntityPlayer", "func_70092_e", "(DDD)D", false);
        m.visitLdcInsn(64.0D); m.visitInsn(Opcodes.DCMPG); Label far = new Label(); m.visitJumpInsn(Opcodes.IFGT, far); m.visitInsn(Opcodes.ICONST_1); m.visitInsn(Opcodes.IRETURN); m.visitLabel(far); m.visitInsn(Opcodes.ICONST_0); m.visitInsn(Opcodes.IRETURN); m.visitMaxs(0, 0); m.visitEnd();
    }

    private static void nbt(ClassWriter w) {
        MethodVisitor read = w.visitMethod(Opcodes.ACC_PUBLIC, "func_145839_a", "(Lnet/minecraft/nbt/NBTTagCompound;)V", null, null); read.visitCode();
        read.visitVarInsn(Opcodes.ALOAD, 1); read.visitLdcInsn("Items"); read.visitIntInsn(Opcodes.BIPUSH, 10);
        read.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/nbt/NBTTagCompound", "func_150295_c", "(Ljava/lang/String;I)Lnet/minecraft/nbt/NBTTagList;", false); read.visitInsn(Opcodes.POP);
        read.visitLdcInsn("Slot"); read.visitInsn(Opcodes.POP);
        read.visitInsn(Opcodes.ACONST_NULL); read.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraft/item/ItemStack", "func_77949_a", "(Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/item/ItemStack;", false); read.visitInsn(Opcodes.POP);
        read.visitInsn(Opcodes.RETURN); read.visitMaxs(0, 0); read.visitEnd();
        MethodVisitor write = w.visitMethod(Opcodes.ACC_PUBLIC, "func_145841_b", "(Lnet/minecraft/nbt/NBTTagCompound;)V", null, null); write.visitCode();
        write.visitTypeInsn(Opcodes.NEW, "net/minecraft/nbt/NBTTagList"); write.visitInsn(Opcodes.DUP); write.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/nbt/NBTTagList", "<init>", "()V", false); write.visitVarInsn(Opcodes.ASTORE, 2);
        write.visitLdcInsn("Slot"); write.visitInsn(Opcodes.POP);
        write.visitInsn(Opcodes.ACONST_NULL); write.visitInsn(Opcodes.ACONST_NULL); write.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/item/ItemStack", "func_77955_b", "(Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/nbt/NBTTagCompound;", false); write.visitInsn(Opcodes.POP);
        write.visitVarInsn(Opcodes.ALOAD, 2); write.visitInsn(Opcodes.ACONST_NULL); write.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/nbt/NBTTagList", "func_74742_a", "(Lnet/minecraft/nbt/NBTBase;)V", false);
        write.visitVarInsn(Opcodes.ALOAD, 1); write.visitLdcInsn("Items"); write.visitVarInsn(Opcodes.ALOAD, 2); write.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/nbt/NBTTagCompound", "func_74782_a", "(Ljava/lang/String;Lnet/minecraft/nbt/NBTBase;)V", false);
        write.visitInsn(Opcodes.RETURN); write.visitMaxs(0, 0); write.visitEnd();
    }

    private static void tick(ClassWriter w, String name) {
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "func_145845_h", "()V", null, null); m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD, 0); m.visitFieldInsn(Opcodes.GETFIELD, name, "progress", "I"); push(m, 400);
        Label done = new Label(); m.visitJumpInsn(Opcodes.IF_ICMPLE, done);
        m.visitInsn(Opcodes.ACONST_NULL); m.visitMethodInsn(Opcodes.INVOKESTATIC, "foreign/machine/RecipeManager", "lookup",
                "(Lnet/minecraft/item/ItemStack;)Lforeign/machine/Recipe;", false); m.visitInsn(Opcodes.POP);
        m.visitLabel(done); m.visitInsn(Opcodes.RETURN); m.visitMaxs(0, 0); m.visitEnd();
    }
    private static void lookupHelper(ClassWriter w) {
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PRIVATE, "canProcess", "()Z", null, null); m.visitCode();
        m.visitInsn(Opcodes.ACONST_NULL); m.visitMethodInsn(Opcodes.INVOKESTATIC, "foreign/machine/RecipeManager", "lookup",
                "(Lnet/minecraft/item/ItemStack;)Lforeign/machine/Recipe;", false);
        Label no = new Label(); m.visitJumpInsn(Opcodes.IFNULL, no); m.visitInsn(Opcodes.ICONST_1); m.visitInsn(Opcodes.IRETURN); m.visitLabel(no); m.visitInsn(Opcodes.ICONST_0); m.visitInsn(Opcodes.IRETURN); m.visitMaxs(0, 0); m.visitEnd();
    }
    private static void clinit(ClassWriter w, String name) {
        MethodVisitor m = w.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null); m.visitCode();
        array(m, name, "top", new int[]{0}); array(m, name, "bottom", new int[]{2, 1}); array(m, name, "sides", new int[]{0});
        m.visitInsn(Opcodes.RETURN); m.visitMaxs(0, 0); m.visitEnd();
    }
    private static void array(MethodVisitor m, String owner, String field, int[] values) {
        push(m, values.length); m.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT);
        for (int i = 0; i < values.length; i++) { m.visitInsn(Opcodes.DUP); push(m, i); push(m, values[i]); m.visitInsn(Opcodes.IASTORE); }
        m.visitFieldInsn(Opcodes.PUTSTATIC, owner, field, "[I");
    }

    private static byte[] manager() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS); w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/machine/RecipeManager", null, "java/lang/Object", null);
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "lookup", "(Lnet/minecraft/item/ItemStack;)Lforeign/machine/Recipe;", null, null);
        m.visitCode(); m.visitInsn(Opcodes.ACONST_NULL); m.visitInsn(Opcodes.ARETURN); m.visitMaxs(0, 0); m.visitEnd(); w.visitEnd(); return w.toByteArray();
    }

    private static byte[] handler() {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS); w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "foreign/machine/Handler", null, "java/lang/Object", new String[]{"cpw/mods/fml/common/network/IGuiHandler"});
        MethodVisitor ctor = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null); ctor.visitCode(); ctor.visitVarInsn(Opcodes.ALOAD, 0); ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false); ctor.visitInsn(Opcodes.RETURN); ctor.visitMaxs(0, 0); ctor.visitEnd();
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "getServerGuiElement", "(ILnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;III)Ljava/lang/Object;", null, null); m.visitCode();
        Label caseSeven = new Label(), defaultCase = new Label();
        m.visitVarInsn(Opcodes.ILOAD, 1); m.visitTableSwitchInsn(7, 7, defaultCase, caseSeven);
        m.visitLabel(caseSeven);
        m.visitTypeInsn(Opcodes.NEW, "foreign/machine/MachineMenu"); m.visitInsn(Opcodes.DUP); m.visitMethodInsn(Opcodes.INVOKESPECIAL, "foreign/machine/MachineMenu", "<init>", "()V", false);
        m.visitVarInsn(Opcodes.ALOAD, 3); m.visitVarInsn(Opcodes.ILOAD, 4); m.visitVarInsn(Opcodes.ILOAD, 5); m.visitVarInsn(Opcodes.ILOAD, 6); m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/World", "func_147438_o", "(III)Lnet/minecraft/tileentity/TileEntity;", false); m.visitTypeInsn(Opcodes.CHECKCAST, "foreign/machine/MachineTile"); m.visitInsn(Opcodes.POP); m.visitInsn(Opcodes.ARETURN);
        m.visitLabel(defaultCase); m.visitInsn(Opcodes.ACONST_NULL); m.visitInsn(Opcodes.ARETURN); m.visitMaxs(0, 0); m.visitEnd(); w.visitEnd(); return w.toByteArray();
    }

    private static byte[] simple(String name, String parent, String[] interfaces) {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS); w.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, name, null, parent, interfaces);
        MethodVisitor ctor = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null); ctor.visitCode(); ctor.visitVarInsn(Opcodes.ALOAD, 0); ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, parent, "<init>", "()V", false); ctor.visitInsn(Opcodes.RETURN); ctor.visitMaxs(0, 0); ctor.visitEnd(); w.visitEnd(); return w.toByteArray();
    }
    private static void intReturn(ClassWriter w, String name, String desc, int value) { MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, name, desc, null, null); m.visitCode(); push(m, value); m.visitInsn(Opcodes.IRETURN); m.visitMaxs(0, 0); m.visitEnd(); }
    private static void push(MethodVisitor m, int value) { if (value >= 0 && value <= 5) m.visitInsn(Opcodes.ICONST_0 + value); else if (value <= Byte.MAX_VALUE) m.visitIntInsn(Opcodes.BIPUSH, value); else if (value <= Short.MAX_VALUE) m.visitIntInsn(Opcodes.SIPUSH, value); else m.visitLdcInsn(value); }
    private static void put(JarOutputStream out, String name, byte[] bytes) throws Exception { out.putNextEntry(new JarEntry(name)); out.write(bytes); out.closeEntry(); }
}
