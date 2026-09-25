package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import java.util.HashSet;
import java.util.Set;

/** Operand-level evidence for the bounded (InventoryPlayer, TileEntity) GUI handoff. */
final class LegacyGuiTileHandoff {
    static final String CONSTRUCTOR =
            "(Lnet/minecraft/entity/player/InventoryPlayer;Lnet/minecraft/tileentity/TileEntity;)V";
    private static final String HANDLER =
            "(ILnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;III)Ljava/lang/Object;";
    private static final String LOOKUP = "(III)Lnet/minecraft/tileentity/TileEntity;";

    private LegacyGuiTileHandoff() { }

    /** The actual tile operand must come from this handler's World and x/y/z parameters. */
    static boolean fromWorld(ClassNode owner, MethodNode method, MethodInsnNode constructor, String tile) {
        if (owner == null || method == null || constructor == null
                || (method.access & Opcodes.ACC_STATIC) != 0 || !HANDLER.equals(method.desc)
                || constructor.getOpcode() != Opcodes.INVOKESPECIAL
                || !"<init>".equals(constructor.name) || !CONSTRUCTOR.equals(constructor.desc)) return false;
        Trace trace = Trace.of(owner, method);
        Frame<SourceValue> frame = trace == null ? null : trace.before(constructor);
        return frame != null && frame.getStackSize() >= 3
                && trace.worldTile(frame.getStack(frame.getStackSize() - 1), tile, new HashSet<>());
    }

    /** A cast elsewhere in the constructor does not prove which value reaches the GUI's tile field. */
    static boolean narrowsToField(ClassNode gui, MethodNode constructor, String tile) {
        if (gui == null || constructor == null || !CONSTRUCTOR.equals(constructor.desc)
                || !"<init>".equals(constructor.name) || (constructor.access & Opcodes.ACC_STATIC) != 0
                || !constructor.tryCatchBlocks.isEmpty()) return false;
        // This slice admits straight-line constructors only; conditional assignment needs a separate proof.
        for (AbstractInsnNode insn : constructor.instructions) {
            if (insn instanceof JumpInsnNode || insn instanceof TableSwitchInsnNode
                    || insn instanceof LookupSwitchInsnNode) return false;
        }
        Trace trace = Trace.of(gui, constructor);
        if (trace == null) return false;
        boolean assigned = false;
        for (AbstractInsnNode insn : constructor.instructions) {
            if (!(insn instanceof FieldInsnNode field) || field.getOpcode() != Opcodes.PUTFIELD
                    || !field.owner.equals(gui.name) || !field.desc.equals("L" + tile + ";")) continue;
            Frame<SourceValue> frame = trace.before(insn);
            if (frame == null || frame.getStackSize() < 2
                    || !trace.parameter(frame.getStack(frame.getStackSize() - 2), 0, Opcodes.ALOAD, new HashSet<>())
                    || !trace.narrowedParameter(frame.getStack(frame.getStackSize() - 1), tile, new HashSet<>())) {
                return false;
            }
            assigned = true;
        }
        return assigned;
    }

    private record Trace(MethodNode method, Frame<SourceValue>[] frames) {
        static Trace of(ClassNode owner, MethodNode method) {
            try {
                return new Trace(method, new Analyzer<>(new SourceInterpreter()).analyze(owner.name, method));
            } catch (AnalyzerException | RuntimeException ignored) {
                return null;
            }
        }

        Frame<SourceValue> before(AbstractInsnNode insn) {
            if (!method.instructions.contains(insn)) return null;
            int index = method.instructions.indexOf(insn);
            return index < 0 || index >= frames.length ? null : frames[index];
        }

        SourceValue input(AbstractInsnNode insn) {
            Frame<SourceValue> frame = before(insn);
            if (frame == null) return null;
            if (insn instanceof VarInsnNode variable
                    && (insn.getOpcode() == Opcodes.ALOAD || insn.getOpcode() == Opcodes.ILOAD)) {
                return variable.var < frame.getLocals() ? frame.getLocal(variable.var) : null;
            }
            return frame.getStackSize() == 0 ? null : frame.getStack(frame.getStackSize() - 1);
        }

        AbstractInsnNode source(SourceValue value, Set<AbstractInsnNode> visited) {
            if (value == null || value.insns.size() != 1 || visited.size() >= 128) return null;
            AbstractInsnNode insn = value.insns.iterator().next();
            return visited.add(insn) ? insn : null;
        }

        boolean parameter(SourceValue value, int slot, int loadOpcode, Set<AbstractInsnNode> visited) {
            AbstractInsnNode insn = source(value, visited);
            if (!(insn instanceof VarInsnNode variable)) return false;
            int opcode = insn.getOpcode();
            if (opcode != loadOpcode && opcode != (loadOpcode == Opcodes.ALOAD ? Opcodes.ASTORE : Opcodes.ISTORE)) return false;
            SourceValue input = input(insn);
            if (opcode == loadOpcode && variable.var == slot && input != null && input.insns.isEmpty()) return true;
            return parameter(input, slot, loadOpcode, visited);
        }

        boolean worldTile(SourceValue value, String tile, Set<AbstractInsnNode> visited) {
            AbstractInsnNode insn = source(value, visited);
            if (insn == null) return false;
            if (insn instanceof VarInsnNode && (insn.getOpcode() == Opcodes.ALOAD || insn.getOpcode() == Opcodes.ASTORE)) {
                return worldTile(input(insn), tile, visited);
            }
            if (insn instanceof TypeInsnNode cast && cast.getOpcode() == Opcodes.CHECKCAST && cast.desc.equals(tile)) {
                return worldTile(input(insn), tile, visited);
            }
            if (!(insn instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKEVIRTUAL
                    || !call.owner.equals("net/minecraft/world/World") || !LOOKUP.equals(call.desc)
                    || !(call.name.equals("getTileEntity") || call.name.equals("func_147438_o"))) return false;
            Frame<SourceValue> frame = before(insn);
            if (frame == null || frame.getStackSize() < 4) return false;
            int base = frame.getStackSize() - 4;
            return parameter(frame.getStack(base), 3, Opcodes.ALOAD, new HashSet<>())
                    && parameter(frame.getStack(base + 1), 4, Opcodes.ILOAD, new HashSet<>())
                    && parameter(frame.getStack(base + 2), 5, Opcodes.ILOAD, new HashSet<>())
                    && parameter(frame.getStack(base + 3), 6, Opcodes.ILOAD, new HashSet<>());
        }

        boolean narrowedParameter(SourceValue value, String tile, Set<AbstractInsnNode> visited) {
            AbstractInsnNode insn = source(value, visited);
            if (insn == null) return false;
            if (insn instanceof VarInsnNode && (insn.getOpcode() == Opcodes.ALOAD || insn.getOpcode() == Opcodes.ASTORE)) {
                return narrowedParameter(input(insn), tile, visited);
            }
            return insn instanceof TypeInsnNode cast && cast.getOpcode() == Opcodes.CHECKCAST && cast.desc.equals(tile)
                    && parameter(input(insn), 2, Opcodes.ALOAD, new HashSet<>());
        }
    }
}
