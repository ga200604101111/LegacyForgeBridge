package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Shared, non-executing proof that an inline Block value has exactly one stack consumer. */
final class LegacyInlineBlockAllocationSafety {
    private LegacyInlineBlockAllocationSafety() { }

    static boolean exclusiveUse(
            MethodNode method,
            TypeInsnNode allocation,
            AbstractInsnNode expressionEnd,
            AbstractInsnNode valueProducer,
            AbstractInsnNode consumer,
            int argumentsAboveValue,
            Function<AbstractInsnNode, Frame<SourceValue>> frames) {
        int start = method.instructions.indexOf(allocation);
        int tail = method.instructions.indexOf(expressionEnd);
        int end = method.instructions.indexOf(consumer);
        Frame<SourceValue> before = frames.apply(allocation);
        if (start < 0 || tail < start || end <= tail
                || argumentsAboveValue < 0 || before == null) {
            return false;
        }
        int slot = before.getStackSize();

        Set<LabelNode> targeted = new LinkedHashSet<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof JumpInsnNode jump) {
                targeted.add(jump.label);
            } else if (instruction instanceof LookupSwitchInsnNode lookup) {
                targeted.add(lookup.dflt);
                targeted.addAll(lookup.labels);
            } else if (instruction instanceof TableSwitchInsnNode table) {
                targeted.add(table.dflt);
                targeted.addAll(table.labels);
            }
        }
        for (var handler : method.tryCatchBlocks) {
            targeted.add(handler.handler);
            int protectedStart = method.instructions.indexOf(handler.start);
            int protectedEnd = method.instructions.indexOf(handler.end);
            if (protectedStart <= end && protectedEnd > start) {
                return false;
            }
        }

        boolean afterExpression = false;
        for (AbstractInsnNode instruction = allocation;
             instruction != null;
             instruction = instruction.getNext()) {
            int opcode = instruction.getOpcode();
            // No edge may enter/leave the live interval. A frame inside it would require
            // stack-map rewriting; frames outside the interval remain valid and are retained.
            if (instruction instanceof JumpInsnNode
                    || instruction instanceof LookupSwitchInsnNode
                    || instruction instanceof TableSwitchInsnNode
                    || instruction instanceof FrameNode
                    || (instruction instanceof LabelNode label && targeted.contains(label))
                    || opcode == Opcodes.RET || opcode == Opcodes.ATHROW
                    || (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN)) {
                return false;
            }
            if (afterExpression && opcode >= 0) {
                Frame<SourceValue> frame = frames.apply(instruction);
                if (frame == null || frame.getStackSize() <= slot) return false;
                SourceValue value = frame.getStack(slot);
                // Do not chase DUP, CHECKCAST or local aliases here: they are additional
                // consumers, not evidence that deleting the original value is harmless.
                if (value == null || value.insns == null
                        || value.insns.size() != 1
                        || !(value.insns.contains(valueProducer)
                        || (valueProducer.getOpcode() == Opcodes.DUP
                        && value.insns.contains(allocation)))) {
                    return false;
                }
                if (instruction == consumer) {
                    return frame.getStackSize() == slot + 1 + argumentsAboveValue;
                }
                // DUP can retain the original SourceValue while copying an alias above it.
                // Inspect reads as well as provenance; a later balanced stack is insufficient.
                if (opcode == Opcodes.POP && frame.getStackSize() == slot + 1) return false;
                if (opcode == Opcodes.POP2) {
                    int top = frame.getStackSize() - 1;
                    int consumed = frame.getStack(top).getSize() == 2 ? 1 : 2;
                    if (top - consumed + 1 <= slot) return false;
                }
                ReadTracker tracker = new ReadTracker(value);
                try {
                    new Frame<>(frame).execute(instruction, tracker);
                } catch (AnalyzerException | RuntimeException invalid) {
                    return false;
                }
                if (tracker.touched) return false;
            }
            if (instruction == expressionEnd) afterExpression = true;
        }
        return false;
    }

    private static final class ReadTracker extends SourceInterpreter {
        private final SourceValue tracked;
        private boolean touched;

        private ReadTracker(SourceValue tracked) {
            super(Opcodes.ASM9);
            this.tracked = tracked;
        }

        private void read(SourceValue value) {
            touched |= tracked.equals(value);
        }

        @Override
        public SourceValue copyOperation(AbstractInsnNode instruction, SourceValue value) {
            read(value);
            return super.copyOperation(instruction, value);
        }

        @Override
        public SourceValue unaryOperation(AbstractInsnNode instruction, SourceValue value) {
            read(value);
            return super.unaryOperation(instruction, value);
        }

        @Override
        public SourceValue binaryOperation(
                AbstractInsnNode instruction, SourceValue left, SourceValue right) {
            read(left);
            read(right);
            return super.binaryOperation(instruction, left, right);
        }

        @Override
        public SourceValue ternaryOperation(
                AbstractInsnNode instruction, SourceValue a, SourceValue b, SourceValue c) {
            read(a);
            read(b);
            read(c);
            return super.ternaryOperation(instruction, a, b, c);
        }

        @Override
        public SourceValue naryOperation(
                AbstractInsnNode instruction, List<? extends SourceValue> values) {
            values.forEach(this::read);
            return super.naryOperation(instruction, values);
        }

        @Override
        public void returnOperation(
                AbstractInsnNode instruction, SourceValue value, SourceValue expected) {
            read(value);
            super.returnOperation(instruction, value, expected);
        }
    }
}
