package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacySingleInputProcessorGuiHandlerBranchStripperTest {
    private static final String HANDLER_DESC =
            "(ILnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;III)Ljava/lang/Object;";

    @Test
    void stripsOnlyTargetGuiIdAndPreservesOtherCases() {
        byte[] original = handler(false);
        var result = new LegacySingleInputProcessorGuiHandlerBranchStripper()
                .strip(original, target());

        assertTrue(result.blockers().isEmpty(), result.blockers().toString());
        assertEquals(2, result.strippedBranches());
        assertTrue(result.serverBranchStripped());
        assertTrue(result.clientBranchStripped());

        ClassNode node = read(result.bytes());
        MethodNode server = method(node, "getServerGuiElement");
        MethodNode client = method(node, "getClientGuiElement");

        assertTrue(isNullReturn(caseInstructions(server, 7)));
        assertTrue(isNullReturn(caseInstructions(client, 7)));
        assertTrue(caseInstructions(server, 8).stream().anyMatch(instruction ->
                instruction instanceof TypeInsnNode type
                        && type.getOpcode() == Opcodes.NEW
                        && "java/lang/Object".equals(type.desc)));
        assertTrue(caseInstructions(client, 8).stream().anyMatch(instruction ->
                instruction instanceof TypeInsnNode type
                        && type.getOpcode() == Opcodes.NEW
                        && "java/lang/Object".equals(type.desc)));

        assertFalse(references(server, "foreign/gui/ProcessorTile"));
        assertFalse(references(server, "foreign/gui/ProcessorMenu"));
        assertFalse(references(client, "foreign/gui/ProcessorTile"));
        assertFalse(references(client, "foreign/gui/ProcessorGui"));
    }

    @Test
    void sharedCaseLabelFailsClosedWithoutChangingBytes() {
        byte[] original = handler(true);
        var result = new LegacySingleInputProcessorGuiHandlerBranchStripper()
                .strip(original, target());

        assertEquals(0, result.strippedBranches());
        assertFalse(result.serverBranchStripped());
        assertFalse(result.clientBranchStripped());
        assertTrue(result.blockers().stream().anyMatch(value ->
                value.contains("case-label-shared")));
        assertArrayEquals(original, result.bytes());
    }

    private static LegacySingleInputProcessorGuiHandlerBranchStripper.Target target() {
        return new LegacySingleInputProcessorGuiHandlerBranchStripper.Target(
                7,
                "getServerGuiElement",
                HANDLER_DESC,
                "getClientGuiElement",
                HANDLER_DESC,
                "foreign/gui/ProcessorTile",
                "foreign/gui/ProcessorMenu",
                "foreign/gui/ProcessorGui");
    }

    private static byte[] handler(boolean sharedTarget) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(
                Opcodes.V1_7,
                Opcodes.ACC_PUBLIC,
                "foreign/gui/Handler",
                null,
                "java/lang/Object",
                new String[]{"cpw/mods/fml/common/network/IGuiHandler"});
        handlerMethod(
                writer,
                "getServerGuiElement",
                "foreign/gui/ProcessorMenu",
                sharedTarget);
        handlerMethod(
                writer,
                "getClientGuiElement",
                "foreign/gui/ProcessorGui",
                sharedTarget);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void handlerMethod(
            ClassWriter writer,
            String methodName,
            String processorClass,
            boolean sharedTarget) {
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC, methodName, HANDLER_DESC, null, null);
        method.visitCode();

        Label processor = new Label();
        Label other = sharedTarget ? processor : new Label();
        Label miss = new Label();
        method.visitVarInsn(Opcodes.ILOAD, 1);
        method.visitTableSwitchInsn(7, 8, miss, processor, other);

        method.visitLabel(processor);
        method.visitTypeInsn(Opcodes.NEW, processorClass);
        method.visitInsn(Opcodes.DUP);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitFieldInsn(
                Opcodes.GETFIELD,
                "net/minecraft/entity/player/EntityPlayer",
                "field_71071_by",
                "Lnet/minecraft/entity/player/InventoryPlayer;");
        method.visitVarInsn(Opcodes.ALOAD, 3);
        method.visitVarInsn(Opcodes.ILOAD, 4);
        method.visitVarInsn(Opcodes.ILOAD, 5);
        method.visitVarInsn(Opcodes.ILOAD, 6);
        method.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "net/minecraft/world/World",
                "func_147438_o",
                "(III)Lnet/minecraft/tileentity/TileEntity;",
                false);
        method.visitTypeInsn(
                Opcodes.CHECKCAST, "foreign/gui/ProcessorTile");
        method.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                processorClass,
                "<init>",
                LegacyGuiTileHandoff.CONSTRUCTOR,
                false);
        method.visitInsn(Opcodes.ARETURN);

        if (!sharedTarget) {
            method.visitLabel(other);
            method.visitTypeInsn(Opcodes.NEW, "java/lang/Object");
            method.visitInsn(Opcodes.DUP);
            method.visitMethodInsn(
                    Opcodes.INVOKESPECIAL,
                    "java/lang/Object",
                    "<init>",
                    "()V",
                    false);
            method.visitInsn(Opcodes.ARETURN);
        }

        method.visitLabel(miss);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream()
                .filter(method -> name.equals(method.name)
                        && HANDLER_DESC.equals(method.desc))
                .findFirst()
                .orElseThrow();
    }

    private static List<AbstractInsnNode> caseInstructions(
            MethodNode method, int guiId) {
        LabelNode target = null;
        List<LabelNode> boundaries = new ArrayList<>();
        LabelNode defaultLabel = null;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof TableSwitchInsnNode table) {
                if (guiId < table.min || guiId > table.max) continue;
                target = table.labels.get(guiId - table.min);
                defaultLabel = table.dflt;
                boundaries.addAll(table.labels);
                break;
            }
            if (instruction instanceof LookupSwitchInsnNode lookup) {
                int index = lookup.keys.indexOf(guiId);
                if (index < 0) continue;
                target = lookup.labels.get(index);
                defaultLabel = lookup.dflt;
                boundaries.addAll(lookup.labels);
                break;
            }
        }
        if (target == null) return List.of();

        List<AbstractInsnNode> result = new ArrayList<>();
        for (AbstractInsnNode cursor = target.getNext();
             cursor != null;
             cursor = cursor.getNext()) {
            if (cursor instanceof LabelNode label
                    && label != target
                    && (label == defaultLabel || boundaries.contains(label))) {
                break;
            }
            if (cursor instanceof LabelNode
                    || cursor instanceof LineNumberNode
                    || cursor instanceof FrameNode) {
                continue;
            }
            result.add(cursor);
            if (cursor.getOpcode() == Opcodes.ARETURN) break;
        }
        return result;
    }

    private static boolean isNullReturn(List<AbstractInsnNode> instructions) {
        return instructions.size() == 2
                && instructions.get(0).getOpcode() == Opcodes.ACONST_NULL
                && instructions.get(1).getOpcode() == Opcodes.ARETURN;
    }

    private static boolean references(MethodNode method, String internalName) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof TypeInsnNode type
                    && internalName.equals(type.desc)) {
                return true;
            }
            if (instruction instanceof MethodInsnNode call
                    && (internalName.equals(call.owner)
                    || call.desc.contains("L" + internalName + ";"))) {
                return true;
            }
        }
        return false;
    }
}
