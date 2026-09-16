package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacySingleInputProcessorRawTileGuiTest {
    private static final String TILE = "foreign/rawgui/Tile";
    private static final String GUI = "foreign/rawgui/Gui";

    @Test
    void rawWorldTileEntityHandoffIsAcceptedWhenGuiConstructorNarrowsIt() throws Exception {
        ClassNode handler = handlerWithoutCaseCast();
        ClassNode gui = canonicalGuiWithConstructorCast();
        Map<String, ClassNode> classes = new LinkedHashMap<>();
        classes.put(handler.name, handler);
        classes.put(gui.name, gui);

        Method findGui = LegacySingleInputProcessorPresentationAnalyzer.class
                .getDeclaredMethod("findGuiClass", Map.class, int.class, String.class);
        findGui.setAccessible(true);
        assertEquals(GUI, findGui.invoke(null, classes, 7, TILE));

        Method canonical = LegacySingleInputProcessorPresentationAnalyzer.class
                .getDeclaredMethod("canonicalGui", ClassNode.class, String.class);
        canonical.setAccessible(true);
        assertTrue((Boolean) canonical.invoke(null, gui, TILE));

        MethodNode caseMethod = handler.methods.getFirst();
        assertFalse(hasType(caseMethod, Opcodes.CHECKCAST, TILE),
                "The regression must prove the IGuiHandler path without a redundant tile cast");
        assertTrue(hasType(gui.methods.getFirst(), Opcodes.CHECKCAST, TILE),
                "The GUI constructor itself remains the narrowing proof");
    }

    private static ClassNode handlerWithoutCaseCast() {
        ClassNode owner = new ClassNode(Opcodes.ASM9);
        owner.name = "foreign/rawgui/Handler";
        owner.superName = "java/lang/Object";
        owner.interfaces.add("cpw/mods/fml/common/network/IGuiHandler");
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "getClientGuiElement",
                "(ILnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;III)Ljava/lang/Object;",
                null, null);
        owner.methods.add(method);
        LabelNode hit = new LabelNode();
        LabelNode miss = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new TableSwitchInsnNode(7, 7, miss, hit));
        method.instructions.add(hit);
        method.instructions.add(new TypeInsnNode(Opcodes.NEW, GUI));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,
                "net/minecraft/entity/player/EntityPlayer", "field_71071_by",
                "Lnet/minecraft/entity/player/InventoryPlayer;"));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 3));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 4));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 5));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 6));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "net/minecraft/world/World", "func_147438_o",
                "(III)Lnet/minecraft/tileentity/TileEntity;", false));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, GUI, "<init>",
                "(Lnet/minecraft/entity/player/InventoryPlayer;Lnet/minecraft/tileentity/TileEntity;)V", false));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.instructions.add(miss);
        method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        return owner;
    }

    private static ClassNode canonicalGuiWithConstructorCast() {
        ClassNode gui = new ClassNode(Opcodes.ASM9);
        gui.name = GUI;
        gui.superName = "net/minecraft/client/gui/inventory/GuiContainer";

        MethodNode ctor = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC, "<init>",
                "(Lnet/minecraft/entity/player/InventoryPlayer;Lnet/minecraft/tileentity/TileEntity;)V",
                null, null);
        gui.methods.add(ctor);
        ctor.instructions.add(new TypeInsnNode(Opcodes.NEW, "foreign/rawgui/Menu"));
        ctor.instructions.add(new InsnNode(Opcodes.POP));
        ctor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        ctor.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, TILE));
        ctor.instructions.add(new InsnNode(Opcodes.POP));
        ctor.instructions.add(new InsnNode(Opcodes.RETURN));

        MethodNode background = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PROTECTED,
                "func_146976_a", "(FII)V", null, null);
        gui.methods.add(background);
        draw(background, 0, 0, 0, 0, 176, 166);
        background.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        background.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, TILE, "a", "I"));
        background.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        background.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, TILE, "b", "I"));
        background.instructions.add(new InsnNode(Opcodes.IMUL));
        background.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        background.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, TILE, "c", "I"));
        background.instructions.add(new InsnNode(Opcodes.POP));
        draw(background, 80, 28, 176, 16, 16, 16);
        draw(background, 80, 46, 192, 6, 16, 6);
        background.instructions.add(new LdcInsnNode(4));
        background.instructions.add(new InsnNode(Opcodes.POP));
        background.instructions.add(new InsnNode(Opcodes.RETURN));
        return gui;
    }

    private static void draw(MethodNode method, int x, int y, int u, int v, int width, int height) {
        method.instructions.add(new LdcInsnNode(x));
        method.instructions.add(new LdcInsnNode(y));
        method.instructions.add(new LdcInsnNode(u));
        method.instructions.add(new LdcInsnNode(v));
        method.instructions.add(new LdcInsnNode(width));
        method.instructions.add(new LdcInsnNode(height));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, GUI,
                "func_73729_b", "(IIIIII)V", false));
    }

    private static boolean hasType(MethodNode method, int opcode, String type) {
        for (var insn : method.instructions) {
            if (insn instanceof TypeInsnNode value && value.getOpcode() == opcode && type.equals(value.desc)) return true;
        }
        return false;
    }
}
