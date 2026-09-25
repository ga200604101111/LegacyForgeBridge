package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class LegacySingleInputProcessorGuiHandlerBranchSafetyTest {
    private static final String HANDLER = "foreign/safety/Handler";
    private static final String TILE = "foreign/safety/Tile";
    private static final String MENU = "foreign/safety/Menu";
    private static final String GUI = "foreign/safety/Gui";
    private static final String PLAYER = "net/minecraft/entity/player/EntityPlayer";
    private static final String WORLD = "net/minecraft/world/World";
    private static final String DESC = "(IL" + PLAYER + ";L" + WORLD + ";III)Ljava/lang/Object;";
    private static final String INIT = "(Lnet/minecraft/entity/player/InventoryPlayer;Lnet/minecraft/tileentity/TileEntity;)V";

    @Test
    void tableAndLookupRewritesLoadOnJvmAndPreserveUnrelatedAndDefaultCases() throws Exception {
        for (boolean lookup : List.of(false, true)) {
            byte[] original = handler(lookup, false);
            var result = strip(original);
            assertEquals(2, result.strippedBranches(), result.blockers().toString());
            assertTrue(result.serverBranchStripped());
            assertTrue(result.clientBranchStripped());
            assertTrue(result.blockers().isEmpty());
            executeCases(result.bytes());
        }
    }

    @Test
    void rawWorldTileOperandIsAcceptedWithoutRequiringADecorativeCast() throws Exception {
        var result = strip(handler(false, true));
        assertEquals(2, result.strippedBranches(), result.blockers().toString());
        executeCases(result.bytes());
    }

    @Test
    void extraJumpToSelectedEntryIsRejectedAtomically() {
        byte[] original = mutate(node -> {
            MethodNode method = client(node);
            TableSwitchInsnNode table = table(method);
            method.instructions.insert(table.labels.get(1), new JumpInsnNode(Opcodes.GOTO, table.labels.get(0)));
        });
        rejected(original, "external-control-flow-target");
    }

    @Test
    void sharedSwitchLabelAndDefaultEntryAreRejected() {
        rejected(mutate(node -> {
            TableSwitchInsnNode table = table(client(node));
            table.labels.set(1, table.labels.get(0));
        }), "case-label-shared");
        rejected(mutate(node -> {
            TableSwitchInsnNode table = table(client(node));
            table.dflt = table.labels.get(0);
        }), "case-label-shared");
    }

    @Test
    void fallthroughFromUnrelatedCaseIsRejected() {
        byte[] original = mutate(node -> {
            MethodNode method = client(node);
            TableSwitchInsnNode table = table(method);
            LabelNode other = table.labels.get(1);
            AbstractInsnNode cursor = other.getNext();
            // Replace case 8's old body with a NOP, then move its entry immediately before case 7.
            while (cursor != table.dflt) {
                AbstractInsnNode next = cursor.getNext();
                method.instructions.remove(cursor);
                cursor = next;
            }
            method.instructions.remove(other);
            method.instructions.insertBefore(table.labels.get(0), other);
            method.instructions.insert(other, new InsnNode(Opcodes.NOP));
        });
        rejected(original, "fallthrough-entry");
    }

    @Test
    void tryCatchRangesAreNeverSilentlyDamaged() {
        byte[] original = mutate(node -> {
            MethodNode method = client(node);
            TableSwitchInsnNode table = table(method);
            LabelNode caught = new LabelNode();
            method.instructions.add(caught);
            method.instructions.add(new InsnNode(Opcodes.POP));
            method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
            method.instructions.add(new InsnNode(Opcodes.ARETURN));
            method.tryCatchBlocks.add(new TryCatchBlockNode(table.labels.get(0), table.labels.get(1), caught,
                    "java/lang/Throwable"));
        });
        rejected(original, "exception-handlers-not-proven");
    }

    @Test
    void extraEffectsAndDiscardedConstructionCannotBorrowSourceProof() {
        rejected(mutate(node -> {
            MethodNode method = client(node);
            AbstractInsnNode first = table(method).labels.getFirst().getNext();
            while (first.getOpcode() < 0) first = first.getNext();
            method.instructions.insertBefore(first, new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "foreign/safety/Audit", "touch", "()V", false));
        }), "construction-or-effects-not-proven");
        rejected(mutate(node -> {
            MethodNode method = client(node);
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && GUI.equals(call.owner)) {
                    InsnList substitute = new InsnList();
                    substitute.add(new InsnNode(Opcodes.POP));
                    substitute.add(new InsnNode(Opcodes.ACONST_NULL));
                    method.instructions.insert(call, substitute);
                    break;
                }
            }
        }), "construction-or-effects-not-proven");
    }

    @Test
    void changedWorldCoordinatesAndPlayerOperandsFailClosed() {
        rejected(mutate(node -> {
            MethodNode method = client(node);
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof VarInsnNode variable && variable.var == 4) {
                    method.instructions.set(instruction, new InsnNode(Opcodes.ICONST_0));
                    break;
                }
            }
        }), "construction-or-effects-not-proven");
        rejected(mutate(node -> {
            MethodNode method = client(node);
            InsnList overwrite = new InsnList();
            overwrite.add(new InsnNode(Opcodes.ICONST_0));
            overwrite.add(new VarInsnNode(Opcodes.ISTORE, 1));
            method.instructions.insert(overwrite);
        }), "handler-parameter-overwritten");
    }

    @Test
    void malformedBytesAndInvalidTargetsReturnOriginalInsteadOfThrowing() {
        byte[] malformed = new byte[]{0, 1, 2};
        rejected(malformed, "verification-failed");
        byte[] original = handler(false, false);
        var invalid = new LegacySingleInputProcessorGuiHandlerBranchStripper().strip(original, null);
        assertArrayEquals(original, invalid.bytes());
        assertEquals(0, invalid.strippedBranches());
        assertFalse(invalid.serverBranchStripped());
        assertFalse(invalid.clientBranchStripped());
    }

    @Test
    void invalidOperandStackRejectsBothBranchesWithoutAOneSidedRewrite() {
        rejected(mutate(node -> {
            MethodNode method = client(node);
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction.getOpcode() == Opcodes.DUP) {
                    method.instructions.remove(instruction);
                    break;
                }
            }
        }), "bytecode-verification-failed");
    }

    @Test
    void debugScopesSurviveTheRemovedInstructions() throws Exception {
        byte[] original = mutate(node -> {
            MethodNode method = client(node);
            TableSwitchInsnNode table = table(method);
            LabelNode internal = new LabelNode();
            AbstractInsnNode first = table.labels.getFirst().getNext();
            while (first.getOpcode() < 0) first = first.getNext();
            method.instructions.insert(first, internal);
            method.instructions.insert(internal, new LineNumberNode(123, internal));
            method.localVariables.add(new LocalVariableNode("world", "L" + WORLD + ";", null,
                    internal, table.labels.get(1), 3));
        });
        var result = strip(original);
        assertEquals(2, result.strippedBranches(), result.blockers().toString());
        ClassNode checked = read(result.bytes());
        for (LocalVariableNode local : client(checked).localVariables) {
            assertTrue(client(checked).instructions.contains(local.start));
            assertTrue(client(checked).instructions.contains(local.end));
        }
        executeCases(result.bytes());
    }

    private static void rejected(byte[] original, String blocker) {
        var result = strip(original);
        assertArrayEquals(original, result.bytes());
        assertEquals(0, result.strippedBranches());
        assertFalse(result.serverBranchStripped());
        assertFalse(result.clientBranchStripped());
        assertTrue(result.blockers().stream().anyMatch(value -> value.contains(blocker)),
                result.blockers().toString());
    }

    private static LegacySingleInputProcessorGuiHandlerBranchStripper.Result strip(byte[] original) {
        return new LegacySingleInputProcessorGuiHandlerBranchStripper().strip(original,
                new LegacySingleInputProcessorGuiHandlerBranchStripper.Target(7, "getServerGuiElement", DESC,
                        "getClientGuiElement", DESC, TILE, MENU, GUI));
    }

    private static byte[] mutate(Consumer<ClassNode> mutation) {
        ClassNode node = read(handler(false, false));
        mutation.accept(node);
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node, ClassReader.EXPAND_FRAMES);
        return node;
    }

    private static MethodNode client(ClassNode node) {
        return node.methods.stream().filter(method -> "getClientGuiElement".equals(method.name))
                .findFirst().orElseThrow();
    }

    private static TableSwitchInsnNode table(MethodNode method) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof TableSwitchInsnNode table) return table;
        }
        throw new AssertionError("fixture switch missing");
    }

    private static byte[] handler(boolean lookup, boolean rawTile) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, HANDLER, null, "java/lang/Object", null);
        defaultConstructor(writer);
        handlerMethod(writer, "getServerGuiElement", MENU, lookup, rawTile);
        handlerMethod(writer, "getClientGuiElement", GUI, lookup, rawTile);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void handlerMethod(ClassWriter writer, String name, String constructed,
                                      boolean lookup, boolean rawTile) {
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, name, DESC, null, null);
        method.visitCode();
        Label hit = new Label(), other = new Label(), miss = new Label();
        method.visitVarInsn(Opcodes.ILOAD, 1);
        if (lookup) method.visitLookupSwitchInsn(miss, new int[]{7, 8}, new Label[]{hit, other});
        else method.visitTableSwitchInsn(7, 8, miss, hit, other);
        method.visitLabel(hit);
        method.visitTypeInsn(Opcodes.NEW, constructed);
        method.visitInsn(Opcodes.DUP);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitFieldInsn(Opcodes.GETFIELD, PLAYER, "field_71071_by", "Lnet/minecraft/entity/player/InventoryPlayer;");
        method.visitVarInsn(Opcodes.ALOAD, 3);
        method.visitVarInsn(Opcodes.ILOAD, 4);
        method.visitVarInsn(Opcodes.ILOAD, 5);
        method.visitVarInsn(Opcodes.ILOAD, 6);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, WORLD, "func_147438_o", "(III)Lnet/minecraft/tileentity/TileEntity;", false);
        if (!rawTile) method.visitTypeInsn(Opcodes.CHECKCAST, TILE);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, constructed, "<init>", INIT, false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitLabel(other);
        method.visitLdcInsn("other:" + name);
        method.visitInsn(Opcodes.ARETURN);
        method.visitLabel(miss);
        method.visitLdcInsn("default:" + name);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static void defaultConstructor(ClassWriter writer) {
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
    }

    private static void executeCases(byte[] bytes) throws Exception {
        class FixtureLoader extends ClassLoader {
            FixtureLoader() { super(null); }
            Class<?> define(String name, byte[] code) { return defineClass(name.replace('/', '.'), code, 0, code.length); }
        }
        FixtureLoader loader = new FixtureLoader();
        Class<?> player = loader.define(PLAYER, stub(PLAYER));
        Class<?> world = loader.define(WORLD, stub(WORLD));
        Class<?> handler = loader.define(HANDLER, bytes);
        Object instance = handler.getConstructor().newInstance();
        for (String name : List.of("getServerGuiElement", "getClientGuiElement")) {
            var method = handler.getMethod(name, int.class, player, world, int.class, int.class, int.class);
            assertNull(method.invoke(instance, 7, null, null, 0, 0, 0));
            assertEquals("other:" + name, method.invoke(instance, 8, null, null, 0, 0, 0));
            assertEquals("default:" + name, method.invoke(instance, -1, null, null, 0, 0, 0));
        }
    }

    private static byte[] stub(String name) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        defaultConstructor(writer);
        writer.visitEnd();
        return writer.toByteArray();
    }
}
