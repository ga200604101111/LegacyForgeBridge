package dev.longyu.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Compiles a bounded source-independent subset of Minecraft 1.7 {@code Block#onBlockActivated}.
 *
 * <p>The admitted slice may make a boolean decision from the clicked legacy side, integer
 * temporaries/constants, the raw metadata of the block being activated, and the legacy world's
 * client/server-side flag. Metadata is admitted only for the exact bytecode sequence
 * {@code world.getBlockMetadata(x, y, z)} using the callback's own World/x/y/z arguments. The
 * client-side flag is admitted only for the exact {@code world.isRemote} field read from that same
 * callback World. Neighbor reads, source Block instance state, player state, hit-vector floats,
 * arbitrary fields/methods, allocations, world mutation and loops remain fail-closed.</p>
 */
public final class LegacyBlockActivationCompiler {
    private static final int MAX_INSTRUCTIONS = 96;
    private static final int MAX_STEPS = 256;
    private static final String WORLD = "net/minecraft/world/World";
    private static final String METADATA_DESCRIPTOR = "(III)I";

    public enum Op {
        NOP,
        LOAD_INT,
        LOAD_META,
        LOAD_CLIENT_SIDE,
        STORE_INT,
        CONST_INT,
        IADD,
        ISUB,
        IMUL,
        IDIV,
        IREM,
        INEG,
        ISHL,
        ISHR,
        IUSHR,
        IAND,
        IOR,
        IXOR,
        IFEQ,
        IFNE,
        IFLT,
        IFGE,
        IFGT,
        IFLE,
        IF_ICMPEQ,
        IF_ICMPNE,
        IF_ICMPLT,
        IF_ICMPGE,
        IF_ICMPGT,
        IF_ICMPLE,
        GOTO,
        TABLE_SWITCH,
        LOOKUP_SWITCH,
        IRETURN
    }

    public record Instruction(Op op, int operand, int target, List<Integer> keys, List<Integer> targets) {
        public Instruction {
            keys = keys == null ? List.of() : List.copyOf(keys);
            targets = targets == null ? List.of() : List.copyOf(targets);
        }
    }

    public record Program(
            String registryName,
            String legacyNamespace,
            String implementationClass,
            String sourceOwner,
            String sourceMethod,
            String sourceDescriptor,
            List<Instruction> instructions
    ) {
        public Program { instructions = List.copyOf(instructions); }

        /** Convenience evaluator for programs that depend only on clicked side and integer locals. */
        public boolean evaluate(int side) {
            if (requires(Op.LOAD_META)) {
                throw new IllegalStateException("Activation program requires legacy block metadata");
            }
            if (requires(Op.LOAD_CLIENT_SIDE)) {
                throw new IllegalStateException("Activation program requires client/server side");
            }
            return evaluate(side, 0, false);
        }

        /** Convenience evaluator for programs that do not depend on client/server side. */
        public boolean evaluate(int side, int metadata) {
            if (requires(Op.LOAD_CLIENT_SIDE)) {
                throw new IllegalStateException("Activation program requires client/server side");
            }
            return evaluate(side, metadata, false);
        }

        /** Pure evaluator shared by tests and the modern runtime adapter. */
        public boolean evaluate(int side, int metadata, boolean clientSide) {
            if (side < 0 || side > 5) {
                throw new IllegalArgumentException("Legacy side outside 0..5: " + side);
            }
            if (metadata < 0 || metadata > 15) {
                throw new IllegalArgumentException("Legacy metadata outside 0..15: " + metadata);
            }
            Map<Integer, Integer> locals = new HashMap<>();
            locals.put(6, side);
            ArrayDeque<Integer> stack = new ArrayDeque<>();
            int pc = 0;
            int steps = 0;
            while (pc >= 0 && pc < instructions.size()) {
                if (++steps > MAX_STEPS) {
                    throw new IllegalStateException("Activation program exceeded step budget");
                }
                Instruction instruction = instructions.get(pc);
                switch (instruction.op()) {
                    case NOP -> { }
                    case LOAD_INT -> {
                        Integer value = locals.get(instruction.operand());
                        if (value == null) throw new IllegalStateException("Uninitialized activation local " + instruction.operand());
                        stack.push(value);
                    }
                    case LOAD_META -> stack.push(metadata);
                    case LOAD_CLIENT_SIDE -> stack.push(clientSide ? 1 : 0);
                    case STORE_INT -> locals.put(instruction.operand(), stack.pop());
                    case CONST_INT -> stack.push(instruction.operand());
                    case IADD -> stack.push(binary(stack, (left, right) -> left + right));
                    case ISUB -> stack.push(binary(stack, (left, right) -> left - right));
                    case IMUL -> stack.push(binary(stack, (left, right) -> left * right));
                    case IDIV -> stack.push(binary(stack, (left, right) -> left / right));
                    case IREM -> stack.push(binary(stack, (left, right) -> left % right));
                    case INEG -> stack.push(-stack.pop());
                    case ISHL -> stack.push(binary(stack, (left, right) -> left << right));
                    case ISHR -> stack.push(binary(stack, (left, right) -> left >> right));
                    case IUSHR -> stack.push(binary(stack, (left, right) -> left >>> right));
                    case IAND -> stack.push(binary(stack, (left, right) -> left & right));
                    case IOR -> stack.push(binary(stack, (left, right) -> left | right));
                    case IXOR -> stack.push(binary(stack, (left, right) -> left ^ right));
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
                    case IRETURN -> { return stack.pop() != 0; }
                }
                pc++;
            }
            throw new IllegalStateException("Activation program terminated without IRETURN");
        }

        private boolean requires(Op op) {
            return instructions.stream().anyMatch(instruction -> instruction.op() == op);
        }
    }

    public record Analysis(List<Program> programs, List<String> diagnostics, int activationCallbacks) {
        public Analysis {
            programs = List.copyOf(programs);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    public Analysis compile(Path jarPath) throws IOException {
        LegacyBlockBehaviorAnalyzer.Analysis behavior = new LegacyBlockBehaviorAnalyzer().analyze(jarPath);
        Map<String, ClassNode> classes = loadClasses(jarPath);
        List<Program> programs = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>(behavior.diagnostics());
        int activationCallbacks = 0;

        for (LegacyBlockBehaviorAnalyzer.BlockBehavior block : behavior.blocks()) {
            LegacyBlockBehaviorAnalyzer.Callback callback = block.callbacks().stream()
                    .filter(value -> value.kind() == LegacyBlockBehaviorAnalyzer.CallbackKind.ACTIVATE)
                    .findFirst().orElse(null);
            if (callback == null) continue;
            activationCallbacks++;
            ClassNode owner = classes.get(callback.owner());
            MethodNode method = owner == null ? null : owner.methods.stream()
                    .filter(value -> value.name.equals(callback.method()) && value.desc.equals(callback.descriptor()))
                    .findFirst().orElse(null);
            if (method == null) {
                diagnostics.add("Activation callback disappeared from source class " + callback.owner() + ".");
                continue;
            }
            CompileResult compiled = compileMethod(method);
            if (compiled.error() != null) {
                diagnostics.add("Unsupported pure activation callback " + callback.owner() + "." + callback.method()
                        + callback.descriptor() + ": " + compiled.error());
                continue;
            }
            programs.add(new Program(block.registryName(), block.legacyNamespace(), block.implementationClass(),
                    callback.owner(), callback.method(), callback.descriptor(), compiled.instructions()));
        }
        return new Analysis(programs, List.copyOf(new LinkedHashSet<>(diagnostics)), activationCallbacks);
    }

    private static CompileResult compileMethod(MethodNode method) {
        List<AbstractInsnNode> real = new ArrayList<>();
        Map<AbstractInsnNode, Integer> indices = new LinkedHashMap<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction.getOpcode() < 0) continue;
            if (real.size() >= MAX_INSTRUCTIONS) return CompileResult.error("instruction budget exceeded");
            indices.put(instruction, real.size());
            real.add(instruction);
        }

        List<Instruction> output = new ArrayList<>();
        boolean returned = false;
        for (int i = 0; i < real.size(); i++) {
            if (matchesCurrentMetadataRead(real, i)) {
                // Preserve one output slot per source instruction so all proven forward jump targets
                // retain their indices while the legacy object/coordinate stack sequence becomes a
                // single typed modern input.
                output.add(simple(Op.NOP, 0));
                output.add(simple(Op.NOP, 0));
                output.add(simple(Op.NOP, 0));
                output.add(simple(Op.NOP, 0));
                output.add(simple(Op.LOAD_META, 0));
                i += 4;
                continue;
            }
            if (matchesClientSideRead(real, i)) {
                output.add(simple(Op.NOP, 0));
                output.add(simple(Op.LOAD_CLIENT_SIDE, 0));
                i += 1;
                continue;
            }

            AbstractInsnNode instruction = real.get(i);
            int opcode = instruction.getOpcode();
            Instruction encoded;
            if (instruction instanceof VarInsnNode variable) {
                if (opcode == Opcodes.ILOAD && (variable.var == 6 || variable.var >= 10)) {
                    encoded = simple(Op.LOAD_INT, variable.var);
                } else if (opcode == Opcodes.ISTORE && variable.var >= 10) {
                    encoded = simple(Op.STORE_INT, variable.var);
                } else {
                    return CompileResult.error("object/world/player/hit-vector/local access opcode " + opcode + " local=" + variable.var);
                }
            } else if (instruction instanceof IntInsnNode value) {
                if (opcode != Opcodes.BIPUSH && opcode != Opcodes.SIPUSH) {
                    return CompileResult.error("int opcode " + opcode);
                }
                encoded = simple(Op.CONST_INT, value.operand);
            } else if (instruction instanceof JumpInsnNode jump) {
                int target = targetIndex(jump.label, indices);
                if (target <= i) return CompileResult.error("backward/invalid jump target " + target);
                Op op = jumpOp(opcode);
                if (op == null) return CompileResult.error("jump opcode " + opcode);
                encoded = jump(op, target);
            } else if (instruction instanceof TableSwitchInsnNode table) {
                int defaultTarget = targetIndex(table.dflt, indices);
                if (defaultTarget <= i) return CompileResult.error("backward/invalid tableswitch default");
                List<Integer> keys = new ArrayList<>();
                List<Integer> targets = new ArrayList<>();
                for (int key = table.min; key <= table.max; key++) keys.add(key);
                for (LabelNode label : table.labels) {
                    int target = targetIndex(label, indices);
                    if (target <= i) return CompileResult.error("backward/invalid tableswitch target");
                    targets.add(target);
                }
                encoded = new Instruction(Op.TABLE_SWITCH, 0, defaultTarget, keys, targets);
            } else if (instruction instanceof LookupSwitchInsnNode lookup) {
                int defaultTarget = targetIndex(lookup.dflt, indices);
                if (defaultTarget <= i) return CompileResult.error("backward/invalid lookupswitch default");
                List<Integer> targets = new ArrayList<>();
                for (LabelNode label : lookup.labels) {
                    int target = targetIndex(label, indices);
                    if (target <= i) return CompileResult.error("backward/invalid lookupswitch target");
                    targets.add(target);
                }
                encoded = new Instruction(Op.LOOKUP_SWITCH, 0, defaultTarget, lookup.keys, targets);
            } else {
                encoded = encodeSimpleOpcode(opcode);
                if (encoded == null) return CompileResult.error("opcode " + opcode);
                if (opcode == Opcodes.IRETURN) returned = true;
            }
            output.add(encoded);
        }
        return returned ? new CompileResult(List.copyOf(output), null) : CompileResult.error("no boolean return");
    }

    private static boolean matchesCurrentMetadataRead(List<AbstractInsnNode> instructions, int index) {
        if (index + 4 >= instructions.size()) return false;
        return isVar(instructions.get(index), Opcodes.ALOAD, 1)
                && isVar(instructions.get(index + 1), Opcodes.ILOAD, 2)
                && isVar(instructions.get(index + 2), Opcodes.ILOAD, 3)
                && isVar(instructions.get(index + 3), Opcodes.ILOAD, 4)
                && instructions.get(index + 4) instanceof MethodInsnNode call
                && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                && WORLD.equals(call.owner)
                && ("getBlockMetadata".equals(call.name) || "func_72805_g".equals(call.name))
                && METADATA_DESCRIPTOR.equals(call.desc);
    }

    private static boolean matchesClientSideRead(List<AbstractInsnNode> instructions, int index) {
        if (index + 1 >= instructions.size()) return false;
        return isVar(instructions.get(index), Opcodes.ALOAD, 1)
                && instructions.get(index + 1) instanceof FieldInsnNode field
                && field.getOpcode() == Opcodes.GETFIELD
                && WORLD.equals(field.owner)
                && ("isRemote".equals(field.name) || "field_72995_K".equals(field.name))
                && "Z".equals(field.desc);
    }

    private static boolean isVar(AbstractInsnNode instruction, int opcode, int local) {
        return instruction instanceof VarInsnNode variable
                && variable.getOpcode() == opcode
                && variable.var == local;
    }

    private static Instruction encodeSimpleOpcode(int opcode) {
        return switch (opcode) {
            case Opcodes.NOP -> simple(Op.NOP, 0);
            case Opcodes.ICONST_M1 -> simple(Op.CONST_INT, -1);
            case Opcodes.ICONST_0 -> simple(Op.CONST_INT, 0);
            case Opcodes.ICONST_1 -> simple(Op.CONST_INT, 1);
            case Opcodes.ICONST_2 -> simple(Op.CONST_INT, 2);
            case Opcodes.ICONST_3 -> simple(Op.CONST_INT, 3);
            case Opcodes.ICONST_4 -> simple(Op.CONST_INT, 4);
            case Opcodes.ICONST_5 -> simple(Op.CONST_INT, 5);
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
        AbstractInsnNode cursor = label;
        while (cursor != null) {
            Integer index = indices.get(cursor);
            if (index != null) return index;
            cursor = cursor.getNext();
        }
        return -1;
    }

    private static Instruction simple(Op op, int operand) {
        return new Instruction(op, operand, -1, List.of(), List.of());
    }

    private static Instruction jump(Op op, int target) {
        return new Instruction(op, 0, target, List.of(), List.of());
    }

    private static int binary(ArrayDeque<Integer> stack, IntBinary operator) {
        int right = stack.pop();
        int left = stack.pop();
        return operator.apply(left, right);
    }

    @FunctionalInterface
    private interface IntBinary { int apply(int left, int right); }

    private record CompileResult(List<Instruction> instructions, String error) {
        static CompileResult error(String error) { return new CompileResult(List.of(), error); }
    }

    private static Map<String, ClassNode> loadClasses(Path jarPath) throws IOException {
        Map<String, ClassNode> classes = new LinkedHashMap<>();
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;
                try (InputStream input = jar.getInputStream(entry)) {
                    ClassNode node = new ClassNode(Opcodes.ASM9);
                    new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    classes.put(node.name, node);
                } catch (RuntimeException ignored) {
                    // The registry/behavior analyzers own malformed-class diagnostics. Never guess.
                }
            }
        }
        return classes;
    }
}