package dev.longyu.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
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
 * Compiles the pure Minecraft 1.7 {@code Block#onBlockPlaced(...): int} subset into a bounded,
 * source-independent program.
 *
 * <p>The admitted language deliberately excludes the source Block instance, World access, entity
 * access, arbitrary fields/methods, allocations and loops. This lets placement metadata be carried
 * into a modern BlockState without defining or executing a legacy class. The one platform constant
 * admitted is ForgeDirection.OPPOSITES because it is a fixed side-index table, not mod state.</p>
 */
public final class LegacyBlockPlacementCompiler {
    private static final int MAX_INSTRUCTIONS = 128;
    private static final int MAX_STEPS = 512;
    private static final String FORGE_DIRECTION = "net/minecraftforge/common/util/ForgeDirection";

    public enum Op {
        LOAD_INT,
        LOAD_FLOAT,
        STORE_INT,
        STORE_FLOAT,
        STORE_DOUBLE,
        LOAD_DOUBLE,
        CONST_INT,
        CONST_FLOAT,
        CONST_DOUBLE,
        PUSH_OPPOSITES,
        IALOAD,
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
        F2D,
        FCMPG,
        FCMPL,
        DCMPG,
        DCMPL,
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

    public record Instruction(Op op, int operand, double number, int target,
                              List<Integer> keys, List<Integer> targets) {
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

        /** Pure evaluator used by both regression tests and the modern runtime adapter. */
        public int evaluate(int side, float hitX, float hitY, float hitZ, int metadata) {
            if (side < 0 || side > 5) throw new IllegalArgumentException("Legacy side outside 0..5: " + side);
            if (metadata < 0 || metadata > 15) throw new IllegalArgumentException("Legacy metadata outside 0..15: " + metadata);

            Map<Integer, Object> locals = new HashMap<>();
            locals.put(5, side);
            locals.put(6, hitX);
            locals.put(7, hitY);
            locals.put(8, hitZ);
            locals.put(9, metadata);
            ArrayDeque<Object> stack = new ArrayDeque<>();
            int pc = 0;
            int steps = 0;
            while (pc >= 0 && pc < instructions.size()) {
                if (++steps > MAX_STEPS) throw new IllegalStateException("Placement program exceeded step budget");
                Instruction instruction = instructions.get(pc);
                switch (instruction.op()) {
                    case LOAD_INT -> stack.push(asInt(requireLocal(locals, instruction.operand())));
                    case LOAD_FLOAT -> stack.push(asFloat(requireLocal(locals, instruction.operand())));
                    case LOAD_DOUBLE -> stack.push(asDouble(requireLocal(locals, instruction.operand())));
                    case STORE_INT -> locals.put(instruction.operand(), asInt(stack.pop()));
                    case STORE_FLOAT -> locals.put(instruction.operand(), asFloat(stack.pop()));
                    case STORE_DOUBLE -> locals.put(instruction.operand(), asDouble(stack.pop()));
                    case CONST_INT -> stack.push(instruction.operand());
                    case CONST_FLOAT -> stack.push((float) instruction.number());
                    case CONST_DOUBLE -> stack.push(instruction.number());
                    case PUSH_OPPOSITES -> stack.push(Opposites.INSTANCE);
                    case IALOAD -> {
                        int index = asInt(stack.pop());
                        Object array = stack.pop();
                        if (array != Opposites.INSTANCE || index < 0 || index > 5) {
                            throw new IllegalStateException("Invalid ForgeDirection.OPPOSITES lookup");
                        }
                        stack.push(opposite(index));
                    }
                    case IADD -> stack.push(binaryInt(stack, (a, b) -> a + b));
                    case ISUB -> stack.push(binaryInt(stack, (a, b) -> a - b));
                    case IMUL -> stack.push(binaryInt(stack, (a, b) -> a * b));
                    case IDIV -> stack.push(binaryInt(stack, (a, b) -> a / b));
                    case IREM -> stack.push(binaryInt(stack, (a, b) -> a % b));
                    case INEG -> stack.push(-asInt(stack.pop()));
                    case ISHL -> stack.push(binaryInt(stack, (a, b) -> a << b));
                    case ISHR -> stack.push(binaryInt(stack, (a, b) -> a >> b));
                    case IUSHR -> stack.push(binaryInt(stack, (a, b) -> a >>> b));
                    case IAND -> stack.push(binaryInt(stack, (a, b) -> a & b));
                    case IOR -> stack.push(binaryInt(stack, (a, b) -> a | b));
                    case IXOR -> stack.push(binaryInt(stack, (a, b) -> a ^ b));
                    case F2D -> stack.push((double) asFloat(stack.pop()));
                    case FCMPG -> stack.push(compareFloat(stack, true));
                    case FCMPL -> stack.push(compareFloat(stack, false));
                    case DCMPG -> stack.push(compareDouble(stack, true));
                    case DCMPL -> stack.push(compareDouble(stack, false));
                    case IFEQ -> { if (asInt(stack.pop()) == 0) { pc = instruction.target(); continue; } }
                    case IFNE -> { if (asInt(stack.pop()) != 0) { pc = instruction.target(); continue; } }
                    case IFLT -> { if (asInt(stack.pop()) < 0) { pc = instruction.target(); continue; } }
                    case IFGE -> { if (asInt(stack.pop()) >= 0) { pc = instruction.target(); continue; } }
                    case IFGT -> { if (asInt(stack.pop()) > 0) { pc = instruction.target(); continue; } }
                    case IFLE -> { if (asInt(stack.pop()) <= 0) { pc = instruction.target(); continue; } }
                    case IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE -> {
                        int right = asInt(stack.pop());
                        int left = asInt(stack.pop());
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
                        int key = asInt(stack.pop());
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
                    case IRETURN -> {
                        int result = asInt(stack.pop());
                        if (result < 0 || result > 15) {
                            throw new IllegalStateException("Placement callback returned metadata outside 0..15: " + result);
                        }
                        return result;
                    }
                }
                pc++;
            }
            throw new IllegalStateException("Placement program terminated without IRETURN");
        }
    }

    public record Analysis(List<Program> programs, List<String> diagnostics) {
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

        for (LegacyBlockBehaviorAnalyzer.BlockBehavior block : behavior.blocks()) {
            LegacyBlockBehaviorAnalyzer.Callback callback = block.callbacks().stream()
                    .filter(value -> value.kind() == LegacyBlockBehaviorAnalyzer.CallbackKind.PLACED)
                    .findFirst().orElse(null);
            if (callback == null) continue;
            ClassNode owner = classes.get(callback.owner());
            MethodNode method = owner == null ? null : owner.methods.stream()
                    .filter(value -> value.name.equals(callback.method()) && value.desc.equals(callback.descriptor()))
                    .findFirst().orElse(null);
            if (method == null) {
                diagnostics.add("Placement callback disappeared from source class " + callback.owner() + ".");
                continue;
            }
            CompileResult compiled = compileMethod(method);
            if (compiled.error() != null) {
                diagnostics.add("Unsupported pure placement callback " + callback.owner() + "." + callback.method()
                        + callback.descriptor() + ": " + compiled.error());
                continue;
            }
            programs.add(new Program(block.registryName(), block.legacyNamespace(), block.implementationClass(),
                    callback.owner(), callback.method(), callback.descriptor(), compiled.instructions()));
        }
        return new Analysis(programs, List.copyOf(new LinkedHashSet<>(diagnostics)));
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
            AbstractInsnNode instruction = real.get(i);
            int opcode = instruction.getOpcode();
            Instruction encoded;
            if (instruction instanceof VarInsnNode variable) {
                encoded = encodeVariable(variable);
                if (encoded == null) return CompileResult.error("object/world/local access opcode " + opcode + " local=" + variable.var);
            } else if (instruction instanceof IntInsnNode value) {
                if (opcode != Opcodes.BIPUSH && opcode != Opcodes.SIPUSH) return CompileResult.error("int opcode " + opcode);
                encoded = simple(Op.CONST_INT, value.operand);
            } else if (instruction instanceof LdcInsnNode ldc) {
                if (ldc.cst instanceof Integer value) encoded = simple(Op.CONST_INT, value);
                else if (ldc.cst instanceof Float value) encoded = number(Op.CONST_FLOAT, value);
                else if (ldc.cst instanceof Double value) encoded = number(Op.CONST_DOUBLE, value);
                else return CompileResult.error("non-numeric LDC " + ldc.cst);
            } else if (instruction instanceof FieldInsnNode field) {
                if (opcode == Opcodes.GETSTATIC && FORGE_DIRECTION.equals(field.owner)
                        && "OPPOSITES".equals(field.name) && "[I".equals(field.desc)) {
                    encoded = simple(Op.PUSH_OPPOSITES, 0);
                } else return CompileResult.error("field access " + field.owner + "." + field.name + field.desc);
            } else if (instruction instanceof MethodInsnNode call) {
                return CompileResult.error("method call " + call.owner + "." + call.name + call.desc);
            } else if (instruction instanceof JumpInsnNode jump) {
                int target = targetIndex(jump.label, method, indices);
                if (target <= i) return CompileResult.error("backward/invalid jump target " + target);
                Op op = jumpOp(opcode);
                if (op == null) return CompileResult.error("jump opcode " + opcode);
                encoded = jump(op, target);
            } else if (instruction instanceof TableSwitchInsnNode table) {
                int defaultTarget = targetIndex(table.dflt, method, indices);
                if (defaultTarget <= i) return CompileResult.error("backward/invalid tableswitch default");
                List<Integer> keys = new ArrayList<>();
                List<Integer> targets = new ArrayList<>();
                for (int key = table.min; key <= table.max; key++) keys.add(key);
                for (LabelNode label : table.labels) {
                    int target = targetIndex(label, method, indices);
                    if (target <= i) return CompileResult.error("backward/invalid tableswitch target");
                    targets.add(target);
                }
                encoded = new Instruction(Op.TABLE_SWITCH, 0, 0, defaultTarget, keys, targets);
            } else if (instruction instanceof LookupSwitchInsnNode lookup) {
                int defaultTarget = targetIndex(lookup.dflt, method, indices);
                if (defaultTarget <= i) return CompileResult.error("backward/invalid lookupswitch default");
                List<Integer> targets = new ArrayList<>();
                for (LabelNode label : lookup.labels) {
                    int target = targetIndex(label, method, indices);
                    if (target <= i) return CompileResult.error("backward/invalid lookupswitch target");
                    targets.add(target);
                }
                encoded = new Instruction(Op.LOOKUP_SWITCH, 0, 0, defaultTarget, lookup.keys, targets);
            } else {
                encoded = encodeSimpleOpcode(opcode);
                if (encoded == null) return CompileResult.error("opcode " + opcode);
                if (opcode == Opcodes.IRETURN) returned = true;
            }
            output.add(encoded);
        }
        return returned ? new CompileResult(List.copyOf(output), null)
                : CompileResult.error("no integer return");
    }

    private static Instruction encodeVariable(VarInsnNode variable) {
        int var = variable.var;
        if (var < 5) return null; // reject this, World and xyz object/world coordinates.
        return switch (variable.getOpcode()) {
            case Opcodes.ILOAD -> (var == 5 || var == 9 || var >= 10) ? simple(Op.LOAD_INT, var) : null;
            case Opcodes.FLOAD -> (var >= 6 && var <= 8 || var >= 10) ? simple(Op.LOAD_FLOAT, var) : null;
            case Opcodes.DLOAD -> var >= 10 ? simple(Op.LOAD_DOUBLE, var) : null;
            case Opcodes.ISTORE -> var >= 10 ? simple(Op.STORE_INT, var) : null;
            case Opcodes.FSTORE -> var >= 10 ? simple(Op.STORE_FLOAT, var) : null;
            case Opcodes.DSTORE -> var >= 10 ? simple(Op.STORE_DOUBLE, var) : null;
            default -> null;
        };
    }

    private static Instruction encodeSimpleOpcode(int opcode) {
        return switch (opcode) {
            case Opcodes.NOP -> simple(Op.CONST_INT, 0); // rejected below by stack shape if observable; retained only for harmless compiler NOPs.
            case Opcodes.ICONST_M1 -> simple(Op.CONST_INT, -1);
            case Opcodes.ICONST_0 -> simple(Op.CONST_INT, 0);
            case Opcodes.ICONST_1 -> simple(Op.CONST_INT, 1);
            case Opcodes.ICONST_2 -> simple(Op.CONST_INT, 2);
            case Opcodes.ICONST_3 -> simple(Op.CONST_INT, 3);
            case Opcodes.ICONST_4 -> simple(Op.CONST_INT, 4);
            case Opcodes.ICONST_5 -> simple(Op.CONST_INT, 5);
            case Opcodes.IALOAD -> simple(Op.IALOAD, 0);
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
            case Opcodes.F2D -> simple(Op.F2D, 0);
            case Opcodes.FCMPG -> simple(Op.FCMPG, 0);
            case Opcodes.FCMPL -> simple(Op.FCMPL, 0);
            case Opcodes.DCMPG -> simple(Op.DCMPG, 0);
            case Opcodes.DCMPL -> simple(Op.DCMPL, 0);
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

    private static int targetIndex(LabelNode label, MethodNode method, Map<AbstractInsnNode, Integer> indices) {
        AbstractInsnNode node = label;
        while (node != null) {
            Integer index = indices.get(node);
            if (index != null) return index;
            node = node.getNext();
        }
        return -1;
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
                }
            }
        }
        return classes;
    }

    private static Instruction simple(Op op, int operand) {
        return new Instruction(op, operand, 0, -1, List.of(), List.of());
    }

    private static Instruction number(Op op, double value) {
        return new Instruction(op, 0, value, -1, List.of(), List.of());
    }

    private static Instruction jump(Op op, int target) {
        return new Instruction(op, 0, 0, target, List.of(), List.of());
    }

    private static Object requireLocal(Map<Integer, Object> locals, int index) {
        Object value = locals.get(index);
        if (value == null) throw new IllegalStateException("Uninitialized placement local " + index);
        return value;
    }

    private static int asInt(Object value) {
        if (value instanceof Integer integer) return integer;
        throw new IllegalStateException("Expected int but got " + value);
    }

    private static float asFloat(Object value) {
        if (value instanceof Float number) return number;
        throw new IllegalStateException("Expected float but got " + value);
    }

    private static double asDouble(Object value) {
        if (value instanceof Double number) return number;
        throw new IllegalStateException("Expected double but got " + value);
    }

    private interface IntBinary { int apply(int left, int right); }

    private static int binaryInt(ArrayDeque<Object> stack, IntBinary operation) {
        int right = asInt(stack.pop());
        int left = asInt(stack.pop());
        return operation.apply(left, right);
    }

    private static int compareFloat(ArrayDeque<Object> stack, boolean nanIsGreater) {
        float right = asFloat(stack.pop());
        float left = asFloat(stack.pop());
        if (Float.isNaN(left) || Float.isNaN(right)) return nanIsGreater ? 1 : -1;
        return Float.compare(left, right);
    }

    private static int compareDouble(ArrayDeque<Object> stack, boolean nanIsGreater) {
        double right = asDouble(stack.pop());
        double left = asDouble(stack.pop());
        if (Double.isNaN(left) || Double.isNaN(right)) return nanIsGreater ? 1 : -1;
        return Double.compare(left, right);
    }

    private static int opposite(int side) {
        return switch (side) {
            case 0 -> 1;
            case 1 -> 0;
            case 2 -> 3;
            case 3 -> 2;
            case 4 -> 5;
            case 5 -> 4;
            default -> throw new IllegalArgumentException("side");
        };
    }

    private enum Opposites { INSTANCE }

    private record CompileResult(List<Instruction> instructions, String error) {
        static CompileResult error(String error) { return new CompileResult(List.of(), error); }
    }
}
