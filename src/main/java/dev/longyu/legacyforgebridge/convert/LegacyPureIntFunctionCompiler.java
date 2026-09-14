package dev.longyu.legacyforgebridge.convert;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded compiler for source-owned pure {@code int -> int} legacy helpers. */
public final class LegacyPureIntFunctionCompiler {
    private static final int MAX_INSTRUCTIONS = 96;
    private static final int MAX_STEPS = 384;

    public enum Op {
        NOP, LOAD, STORE, IINC, CONST,
        IADD, ISUB, IMUL, IDIV, IREM, INEG,
        ISHL, ISHR, IUSHR, IAND, IOR, IXOR,
        IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE,
        IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE,
        GOTO, TABLE_SWITCH, LOOKUP_SWITCH, IRETURN
    }

    public record Instruction(Op op, int operand, int value, int target,
                              List<Integer> keys, List<Integer> targets) {
        public Instruction {
            keys = keys == null ? List.of() : List.copyOf(keys);
            targets = targets == null ? List.of() : List.copyOf(targets);
        }
    }

    public record Program(int inputLocal, List<Instruction> instructions) {
        public Program { instructions = List.copyOf(instructions); }

        public static Program constant(int value) {
            return new Program(1, List.of(
                    simple(Op.CONST, value),
                    simple(Op.IRETURN, 0)));
        }

        public int evaluate(int input) {
            Map<Integer, Integer> locals = new HashMap<>();
            locals.put(inputLocal, input);
            ArrayDeque<Integer> stack = new ArrayDeque<>();
            int pc = 0;
            int steps = 0;
            while (pc >= 0 && pc < instructions.size()) {
                if (++steps > MAX_STEPS) throw new IllegalStateException("Pure int program exceeded step budget");
                Instruction instruction = instructions.get(pc);
                switch (instruction.op()) {
                    case NOP -> { }
                    case LOAD -> {
                        Integer value = locals.get(instruction.operand());
                        if (value == null) throw new IllegalStateException("Uninitialized int local " + instruction.operand());
                        stack.push(value);
                    }
                    case STORE -> locals.put(instruction.operand(), stack.pop());
                    case IINC -> {
                        Integer value = locals.get(instruction.operand());
                        if (value == null) throw new IllegalStateException("Uninitialized int local " + instruction.operand());
                        locals.put(instruction.operand(), value + instruction.value());
                    }
                    case CONST -> stack.push(instruction.value());
                    case IADD -> stack.push(binary(stack, (a, b) -> a + b));
                    case ISUB -> stack.push(binary(stack, (a, b) -> a - b));
                    case IMUL -> stack.push(binary(stack, (a, b) -> a * b));
                    case IDIV -> stack.push(binary(stack, (a, b) -> a / b));
                    case IREM -> stack.push(binary(stack, (a, b) -> a % b));
                    case INEG -> stack.push(-stack.pop());
                    case ISHL -> stack.push(binary(stack, (a, b) -> a << b));
                    case ISHR -> stack.push(binary(stack, (a, b) -> a >> b));
                    case IUSHR -> stack.push(binary(stack, (a, b) -> a >>> b));
                    case IAND -> stack.push(binary(stack, (a, b) -> a & b));
                    case IOR -> stack.push(binary(stack, (a, b) -> a | b));
                    case IXOR -> stack.push(binary(stack, (a, b) -> a ^ b));
                    case IFEQ -> { if (stack.pop() == 0) { pc = instruction.target(); continue; } }
                    case IFNE -> { if (stack.pop() != 0) { pc = instruction.target(); continue; } }
                    case IFLT -> { if (stack.pop() < 0) { pc = instruction.target(); continue; } }
                    case IFGE -> { if (stack.pop() >= 0) { pc = instruction.target(); continue; } }
                    case IFGT -> { if (stack.pop() > 0) { pc = instruction.target(); continue; } }
                    case IFLE -> { if (stack.pop() <= 0) { pc = instruction.target(); continue; } }
                    case IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE -> {
                        int right = stack.pop();
                        int left = stack.pop();
                        boolean take = switch (instruction.op()) {
                            case IF_ICMPEQ -> left == right;
                            case IF_ICMPNE -> left != right;
                            case IF_ICMPLT -> left < right;
                            case IF_ICMPGE -> left >= right;
                            case IF_ICMPGT -> left > right;
                            case IF_ICMPLE -> left <= right;
                            default -> false;
                        };
                        if (take) { pc = instruction.target(); continue; }
                    }
                    case GOTO -> { pc = instruction.target(); continue; }
                    case TABLE_SWITCH, LOOKUP_SWITCH -> {
                        int key = stack.pop();
                        int next = instruction.target();
                        for (int i = 0; i < instruction.keys().size(); i++) {
                            if (instruction.keys().get(i) == key) {
                                next = instruction.targets().get(i);
                                break;
                            }
                        }
                        pc = next;
                        continue;
                    }
                    case IRETURN -> { return stack.pop(); }
                }
                pc++;
            }
            throw new IllegalStateException("Pure int program terminated without IRETURN");
        }
    }

    public record Result(Program program, String error) {
        public boolean supported() { return program != null && error == null; }
        static Result error(String message) { return new Result(null, message); }
    }

    public Result compile(MethodNode method, int inputLocal) {
        if (inputLocal < 0) return Result.error("invalid input local");
        List<AbstractInsnNode> real = new ArrayList<>();
        Map<AbstractInsnNode, Integer> indices = new LinkedHashMap<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction.getOpcode() < 0) continue;
            if (real.size() >= MAX_INSTRUCTIONS) return Result.error("instruction budget exceeded");
            indices.put(instruction, real.size());
            real.add(instruction);
        }
        List<Instruction> output = new ArrayList<>();
        boolean returned = false;
        for (int i = 0; i < real.size(); i++) {
            AbstractInsnNode instruction = real.get(i);
            int opcode = instruction.getOpcode();
            Instruction encoded;
            if (instruction instanceof VarInsnNode variable) {
                if (variable.getOpcode() == Opcodes.ILOAD && allowedLocal(variable.var, inputLocal)) {
                    encoded = simple(Op.LOAD, variable.var);
                } else if (variable.getOpcode() == Opcodes.ISTORE && variable.var > inputLocal) {
                    encoded = simple(Op.STORE, variable.var);
                } else return Result.error("unsupported local opcode " + opcode + " local=" + variable.var);
            } else if (instruction instanceof IincInsnNode increment) {
                if (increment.var <= inputLocal) return Result.error("source input mutation is not admitted");
                encoded = new Instruction(Op.IINC, increment.var, increment.incr, -1, List.of(), List.of());
            } else if (instruction instanceof IntInsnNode value) {
                if (opcode != Opcodes.BIPUSH && opcode != Opcodes.SIPUSH) return Result.error("int opcode " + opcode);
                encoded = simple(Op.CONST, value.operand);
            } else if (instruction instanceof LdcInsnNode ldc) {
                if (!(ldc.cst instanceof Integer value)) return Result.error("non-int LDC " + ldc.cst);
                encoded = simple(Op.CONST, value);
            } else if (instruction instanceof JumpInsnNode jump) {
                int target = targetIndex(jump.label, indices);
                if (target <= i) return Result.error("backward/invalid jump target " + target);
                Op op = jumpOp(opcode);
                if (op == null) return Result.error("jump opcode " + opcode);
                encoded = jump(op, target);
            } else if (instruction instanceof TableSwitchInsnNode table) {
                int defaultTarget = targetIndex(table.dflt, indices);
                if (defaultTarget <= i) return Result.error("backward/invalid tableswitch default");
                List<Integer> keys = new ArrayList<>();
                List<Integer> targets = new ArrayList<>();
                for (int key = table.min; key <= table.max; key++) keys.add(key);
                for (LabelNode label : table.labels) {
                    int target = targetIndex(label, indices);
                    if (target <= i) return Result.error("backward/invalid tableswitch target");
                    targets.add(target);
                }
                encoded = new Instruction(Op.TABLE_SWITCH, 0, 0, defaultTarget, keys, targets);
            } else if (instruction instanceof LookupSwitchInsnNode lookup) {
                int defaultTarget = targetIndex(lookup.dflt, indices);
                if (defaultTarget <= i) return Result.error("backward/invalid lookupswitch default");
                List<Integer> targets = new ArrayList<>();
                for (LabelNode label : lookup.labels) {
                    int target = targetIndex(label, indices);
                    if (target <= i) return Result.error("backward/invalid lookupswitch target");
                    targets.add(target);
                }
                encoded = new Instruction(Op.LOOKUP_SWITCH, 0, 0, defaultTarget, lookup.keys, targets);
            } else {
                encoded = simpleOpcode(opcode);
                if (encoded == null) return Result.error("unsupported opcode " + opcode);
                if (opcode == Opcodes.IRETURN) returned = true;
            }
            output.add(encoded);
        }
        if (!returned) return Result.error("no integer return");
        return new Result(new Program(inputLocal, output), null);
    }

    private static boolean allowedLocal(int local, int inputLocal) {
        return local == inputLocal || local > inputLocal;
    }

    private static Instruction simpleOpcode(int opcode) {
        return switch (opcode) {
            case Opcodes.NOP -> simple(Op.NOP, 0);
            case Opcodes.ICONST_M1 -> simple(Op.CONST, -1);
            case Opcodes.ICONST_0 -> simple(Op.CONST, 0);
            case Opcodes.ICONST_1 -> simple(Op.CONST, 1);
            case Opcodes.ICONST_2 -> simple(Op.CONST, 2);
            case Opcodes.ICONST_3 -> simple(Op.CONST, 3);
            case Opcodes.ICONST_4 -> simple(Op.CONST, 4);
            case Opcodes.ICONST_5 -> simple(Op.CONST, 5);
            case Opcodes.IADD -> simple(Op.IADD, 0);
            case Opcodes.ISUB -> simple(Op.ISUB, 0);
            case Opcodes.IMUL -> simple(Op.IMUL, 0);
            case Opcodes.IDIV -> simple(Op.IDIV, 0);
            case Opcodes.IREM -> simple(Op.IREM, 0);
            case Opcodes.INEG -> simple(Op.INEG, 0);
            case Opcodes.ISHL -> simple(Op.ISHL, 0);
            case Opcodes.ISHR -> simple(Op.ISHR, 0);
            case Opcodes.IUSHR -> simple(Op.IUSHR, 0);
            case Opcodes.IAND -> simple(Op.IAND, 0);
            case Opcodes.IOR -> simple(Op.IOR, 0);
            case Opcodes.IXOR -> simple(Op.IXOR, 0);
            case Opcodes.IRETURN -> simple(Op.IRETURN, 0);
            default -> null;
        };
    }

    private static Op jumpOp(int opcode) {
        return switch (opcode) {
            case Opcodes.IFEQ -> Op.IFEQ;
            case Opcodes.IFNE -> Op.IFNE;
            case Opcodes.IFLT -> Op.IFLT;
            case Opcodes.IFGE -> Op.IFGE;
            case Opcodes.IFGT -> Op.IFGT;
            case Opcodes.IFLE -> Op.IFLE;
            case Opcodes.IF_ICMPEQ -> Op.IF_ICMPEQ;
            case Opcodes.IF_ICMPNE -> Op.IF_ICMPNE;
            case Opcodes.IF_ICMPLT -> Op.IF_ICMPLT;
            case Opcodes.IF_ICMPGE -> Op.IF_ICMPGE;
            case Opcodes.IF_ICMPGT -> Op.IF_ICMPGT;
            case Opcodes.IF_ICMPLE -> Op.IF_ICMPLE;
            case Opcodes.GOTO -> Op.GOTO;
            default -> null;
        };
    }

    private static int targetIndex(LabelNode label, Map<AbstractInsnNode, Integer> indices) {
        AbstractInsnNode node = label;
        while (node != null) {
            Integer index = indices.get(node);
            if (index != null) return index;
            node = node.getNext();
        }
        return -1;
    }

    private interface IntBinary { int apply(int left, int right); }

    private static int binary(ArrayDeque<Integer> stack, IntBinary operation) {
        int right = stack.pop();
        int left = stack.pop();
        return operation.apply(left, right);
    }

    private static Instruction simple(Op op, int value) {
        return new Instruction(op, 0, value, -1, List.of(), List.of());
    }

    private static Instruction jump(Op op, int target) {
        return new Instruction(op, 0, 0, target, List.of(), List.of());
    }
}
