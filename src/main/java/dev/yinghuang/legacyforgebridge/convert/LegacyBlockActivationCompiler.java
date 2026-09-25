package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Source-independent, read-only compiler for Minecraft 1.7 Block#onBlockActivated.
 * Only clicked side, current-position metadata, World.isRemote and Player.isSneaking
 * are admitted as typed inputs. Source classes are never defined or initialized.
 * Unsupported effects reject the entire callback, including apparently safe prefixes.
 */
public final class LegacyBlockActivationCompiler {
    private static final int MAX_INSTRUCTIONS = 96;
    private static final int MAX_STEPS = 256;
    private static final int MAX_LOCALS = 256;
    private static final String WORLD = "net/minecraft/world/World";
    private static final String ENTITY = "net/minecraft/entity/Entity";
    private static final String PLAYER = "net/minecraft/entity/player/EntityPlayer";
    private static final String DESCRIPTOR = "(Lnet/minecraft/world/World;IIILnet/minecraft/entity/player/EntityPlayer;IFFF)Z";

    public enum Op {
        NOP, LOAD_INT, LOAD_META, LOAD_CLIENT_SIDE, LOAD_SNEAKING, STORE_INT, CONST_INT,
        IADD, ISUB, IMUL, IDIV, IREM, INEG, ISHL, ISHR, IUSHR, IAND, IOR, IXOR,
        IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE,
        IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE,
        GOTO, TABLE_SWITCH, LOOKUP_SWITCH, IRETURN
    }

    public record Instruction(Op op, int operand, int target, List<Integer> keys, List<Integer> targets) {
        public Instruction {
            Objects.requireNonNull(op, "activation opcode");
            keys = keys == null ? List.of() : List.copyOf(keys);
            targets = targets == null ? List.of() : List.copyOf(targets);
        }
    }

    public record Program(String registryName, String legacyNamespace, String implementationClass,
                          String sourceOwner, String sourceMethod, String sourceDescriptor,
                          List<Instruction> instructions) {
        public Program {
            instructions = List.copyOf(instructions);
            // Also protects programs reconstructed by the runtime sidecar loader.
            validateProgram(instructions);
        }

        public boolean evaluate(int side) {
            requireAbsent(Op.LOAD_META, "legacy block metadata");
            requireAbsent(Op.LOAD_CLIENT_SIDE, "client/server side");
            requireAbsent(Op.LOAD_SNEAKING, "player sneaking state");
            return evaluate(side, 0, false, false);
        }

        public boolean evaluate(int side, int metadata) {
            requireAbsent(Op.LOAD_CLIENT_SIDE, "client/server side");
            requireAbsent(Op.LOAD_SNEAKING, "player sneaking state");
            return evaluate(side, metadata, false, false);
        }

        public boolean evaluate(int side, int metadata, boolean clientSide) {
            requireAbsent(Op.LOAD_SNEAKING, "player sneaking state");
            return evaluate(side, metadata, clientSide, false);
        }

        public boolean evaluate(int side, int metadata, boolean clientSide, boolean sneaking) {
            if (side < 0 || side > 5) throw new IllegalArgumentException("Legacy side outside 0..5: " + side);
            if (metadata < 0 || metadata > 15) throw new IllegalArgumentException("Legacy metadata outside 0..15: " + metadata);
            return execute(instructions, side, metadata, clientSide, sneaking);
        }

        private void requireAbsent(Op op, String input) {
            if (instructions.stream().anyMatch(value -> value.op() == op)) {
                throw new IllegalStateException("Activation program requires " + input);
            }
        }
    }

    public record Analysis(List<Program> programs, List<String> diagnostics, int activationCallbacks) {
        public Analysis {
            programs = List.copyOf(programs);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    public Analysis compile(Path jarPath) throws IOException {
        var behavior = new LegacyBlockBehaviorAnalyzer().analyze(jarPath);
        Map<String, ClassNode> classes = loadClasses(jarPath);
        List<Program> programs = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>(behavior.diagnostics());
        int callbacks = 0;
        for (var block : behavior.blocks()) {
            var callback = block.callbacks().stream()
                    .filter(value -> value.kind() == LegacyBlockBehaviorAnalyzer.CallbackKind.ACTIVATE)
                    .findFirst().orElse(null);
            if (callback == null) continue;
            callbacks++;
            ClassNode owner = classes.get(callback.owner());
            MethodNode method = owner == null ? null : owner.methods.stream()
                    .filter(value -> value.name.equals(callback.method()) && value.desc.equals(callback.descriptor()))
                    .findFirst().orElse(null);
            if (method == null) {
                diagnostics.add("Activation callback disappeared from source class " + callback.owner() + ".");
                continue;
            }
            try {
                programs.add(new Program(block.registryName(), block.legacyNamespace(), block.implementationClass(),
                        callback.owner(), callback.method(), callback.descriptor(), compileMethod(method)));
            } catch (IllegalArgumentException exception) {
                diagnostics.add("Unsupported pure activation callback " + callback.owner() + "." + callback.method()
                        + callback.descriptor() + ": " + exception.getMessage());
            }
        }
        return new Analysis(programs, List.copyOf(new LinkedHashSet<>(diagnostics)), callbacks);
    }

    /** Package-visible for adversarial bytecode tests; a Program must still validate the result. */
    static List<Instruction> compileMethod(MethodNode method) {
        int forbidden = Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE | Opcodes.ACC_SYNCHRONIZED;
        if (!DESCRIPTOR.equals(method.desc) || (method.access & forbidden) != 0
                || (method.access & Opcodes.ACC_PUBLIC) == 0) {
            throw invalid("callback must be a public, non-synchronized concrete instance method with the exact descriptor");
        }
        if (method.tryCatchBlocks != null && !method.tryCatchBlocks.isEmpty()) {
            throw invalid("exception handlers are not supported");
        }
        if (method.maxLocals < 10 || method.maxLocals > MAX_LOCALS
                || method.maxStack < 1 || method.maxStack > MAX_INSTRUCTIONS) {
            throw invalid("source frame budget exceeded or invalid");
        }
        List<AbstractInsnNode> real = new ArrayList<>();
        Map<AbstractInsnNode, Integer> indices = new IdentityHashMap<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction.getOpcode() < 0) continue;
            if (real.size() >= MAX_INSTRUCTIONS) throw invalid("instruction budget exceeded");
            indices.put(instruction, real.size());
            real.add(instruction);
        }
        if (real.isEmpty()) throw invalid("empty activation callback");

        // Check branch shape BEFORE ASM analysis, including overflow-prone switch ranges.
        Set<Integer> entries = new HashSet<>();
        for (int i = 0; i < real.size(); i++) {
            AbstractInsnNode value = real.get(i);
            if (value instanceof JumpInsnNode jump) {
                if (jumpOp(value.getOpcode()) == null) throw invalid("unsupported jump opcode " + value.getOpcode());
                entries.add(sourceTarget(jump.label, indices, i, real.size()));
            } else if (value instanceof TableSwitchInsnNode table) {
                long width = (long) table.max - table.min + 1L;
                if (width <= 0 || width > MAX_INSTRUCTIONS || width != table.labels.size()) {
                    throw invalid("invalid or over-budget tableswitch range");
                }
                entries.add(sourceTarget(table.dflt, indices, i, real.size()));
                for (LabelNode label : table.labels) entries.add(sourceTarget(label, indices, i, real.size()));
            } else if (value instanceof LookupSwitchInsnNode lookup) {
                validateKeys(lookup.keys, lookup.labels.size(), false);
                entries.add(sourceTarget(lookup.dflt, indices, i, real.size()));
                for (LabelNode label : lookup.labels) entries.add(sourceTarget(label, indices, i, real.size()));
            }
        }
        for (int i = 0; i < real.size(); i++) {
            int length = metadataRead(real, i) ? 5 : (clientSideRead(real, i) || sneakingRead(real, i)) ? 2 : 0;
            for (int j = 1; j < length; j++) {
                if (entries.contains(i + j)) throw invalid("branch enters typed-input sequence at " + (i + j));
            }
        }
        try {
            // BasicVerifier checks stack/local types without resolving or loading source classes.
            new Analyzer<>(new BasicVerifier()).analyze("lfb/SourceActivation", method);
        } catch (AnalyzerException | RuntimeException exception) {
            throw invalid("invalid source stack/local dataflow: " + exception.getMessage());
        }

        List<Instruction> output = new ArrayList<>();
        for (int i = 0; i < real.size(); i++) {
            Op input = null;
            int length = 0;
            if (metadataRead(real, i)) { input = Op.LOAD_META; length = 5; }
            else if (clientSideRead(real, i)) { input = Op.LOAD_CLIENT_SIDE; length = 2; }
            else if (sneakingRead(real, i)) { input = Op.LOAD_SNEAKING; length = 2; }
            if (input != null) {
                // Preserving indices is insufficient if control flow enters a collapsed sequence.
                for (int j = 1; j < length; j++) {
                    if (entries.contains(i + j)) throw invalid("branch enters typed-input sequence at " + (i + j));
                    output.add(simple(Op.NOP, 0));
                }
                output.add(simple(input, 0));
                i += length - 1;
                continue;
            }
            AbstractInsnNode instruction = real.get(i);
            int opcode = instruction.getOpcode();
            if (instruction instanceof VarInsnNode variable) {
                if (opcode == Opcodes.ILOAD && (variable.var == 6 || variable.var >= 10)) {
                    output.add(simple(Op.LOAD_INT, variable.var));
                } else if (opcode == Opcodes.ISTORE && variable.var >= 10) {
                    output.add(simple(Op.STORE_INT, variable.var));
                } else {
                    throw invalid("object/world/player/hit-vector/local access opcode " + opcode + " local=" + variable.var);
                }
            } else if (instruction instanceof IntInsnNode value) {
                if (opcode != Opcodes.BIPUSH && opcode != Opcodes.SIPUSH) throw invalid("int opcode " + opcode);
                output.add(simple(Op.CONST_INT, value.operand));
            } else if (instruction instanceof LdcInsnNode value) {
                if (!(value.cst instanceof Integer integer)) throw invalid("non-integer LDC constant");
                output.add(simple(Op.CONST_INT, integer));
            } else if (instruction instanceof JumpInsnNode jump) {
                output.add(new Instruction(jumpOp(opcode), 0, sourceTarget(jump.label, indices, i, real.size()), null, null));
            } else if (instruction instanceof TableSwitchInsnNode table) {
                List<Integer> keys = new ArrayList<>();
                List<Integer> targets = new ArrayList<>();
                // Iterate by bounded count, not key <= max: max may be Integer.MAX_VALUE.
                for (int j = 0; j < table.labels.size(); j++) {
                    keys.add(table.min + j);
                    targets.add(sourceTarget(table.labels.get(j), indices, i, real.size()));
                }
                output.add(new Instruction(Op.TABLE_SWITCH, 0, sourceTarget(table.dflt, indices, i, real.size()), keys, targets));
            } else if (instruction instanceof LookupSwitchInsnNode lookup) {
                List<Integer> targets = new ArrayList<>();
                for (LabelNode label : lookup.labels) targets.add(sourceTarget(label, indices, i, real.size()));
                output.add(new Instruction(Op.LOOKUP_SWITCH, 0, sourceTarget(lookup.dflt, indices, i, real.size()), lookup.keys, targets));
            } else {
                Instruction encoded = simpleOpcode(opcode);
                if (encoded == null) throw invalid("opcode " + opcode);
                output.add(encoded);
            }
        }
        return List.copyOf(output);
    }

    private static void validateProgram(List<Instruction> code) {
        if (code.isEmpty() || code.size() > MAX_INSTRUCTIONS) throw invalid("activation instruction budget exceeded or empty program");
        for (int i = 0; i < code.size(); i++) {
            Instruction instruction = code.get(i);
            switch (instruction.op()) {
                case LOAD_INT -> {
                    int local = instruction.operand();
                    if ((local != 6 && local < 10) || local >= MAX_LOCALS) throw invalid("invalid activation load local " + local);
                }
                case STORE_INT -> {
                    if (instruction.operand() < 10 || instruction.operand() >= MAX_LOCALS) throw invalid("invalid activation store local");
                }
                case IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE,
                     IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE, GOTO ->
                        validateTarget(instruction.target(), i, code.size());
                case TABLE_SWITCH, LOOKUP_SWITCH -> {
                    validateTarget(instruction.target(), i, code.size());
                    validateKeys(instruction.keys(), instruction.targets().size(), instruction.op() == Op.TABLE_SWITCH);
                    if (instruction.op() == Op.TABLE_SWITCH && instruction.keys().isEmpty()) throw invalid("empty tableswitch");
                    for (int target : instruction.targets()) validateTarget(target, i, code.size());
                }
                default -> { }
            }
        }
        // This is exhaustive, not sampling: the admitted input domain has exactly 384 tuples.
        for (int side = 0; side < 6; side++) {
            for (int meta = 0; meta < 16; meta++) {
                for (int flags = 0; flags < 4; flags++) {
                    try {
                        execute(code, side, meta, (flags & 1) != 0, (flags & 2) != 0);
                    } catch (RuntimeException exception) {
                        throw new IllegalArgumentException("activation input proof failed: side=" + side
                                + ", metadata=" + meta + ", clientSide=" + ((flags & 1) != 0)
                                + ", sneaking=" + ((flags & 2) != 0) + ": " + exception.getMessage(), exception);
                    }
                }
            }
        }
    }

    private static boolean execute(List<Instruction> code, int side, int metadata, boolean clientSide, boolean sneaking) {
        Map<Integer, Integer> locals = new HashMap<>();
        locals.put(6, side);
        ArrayDeque<Integer> stack = new ArrayDeque<>();
        int pc = 0;
        int steps = 0;
        while (pc >= 0 && pc < code.size()) {
            if (++steps > MAX_STEPS) throw new IllegalStateException("Activation program exceeded step budget");
            Instruction instruction = code.get(pc);
            switch (instruction.op()) {
                case NOP -> { }
                case LOAD_INT -> {
                    Integer value = locals.get(instruction.operand());
                    if (value == null) throw new IllegalStateException("Uninitialized activation local " + instruction.operand());
                    stack.push(value);
                }
                case LOAD_META -> stack.push(metadata);
                case LOAD_CLIENT_SIDE -> stack.push(clientSide ? 1 : 0);
                case LOAD_SNEAKING -> stack.push(sneaking ? 1 : 0);
                case STORE_INT -> locals.put(instruction.operand(), stack.pop());
                case CONST_INT -> stack.push(instruction.operand());
                case INEG -> stack.push(-stack.pop());
                case IADD, ISUB, IMUL, IDIV, IREM, ISHL, ISHR, IUSHR, IAND, IOR, IXOR -> {
                    int right = stack.pop();
                    int left = stack.pop();
                    stack.push(switch (instruction.op()) {
                        case IADD -> left + right;
                        case ISUB -> left - right;
                        case IMUL -> left * right;
                        case IDIV -> left / right;
                        case IREM -> left % right;
                        case ISHL -> left << right;
                        case ISHR -> left >> right;
                        case IUSHR -> left >>> right;
                        case IAND -> left & right;
                        case IOR -> left | right;
                        case IXOR -> left ^ right;
                        default -> throw new IllegalStateException("not an integer operator");
                    });
                }
                case IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE -> {
                    int value = stack.pop();
                    boolean take = switch (instruction.op()) {
                        case IFEQ -> value == 0;
                        case IFNE -> value != 0;
                        case IFLT -> value < 0;
                        case IFGE -> value >= 0;
                        case IFGT -> value > 0;
                        case IFLE -> value <= 0;
                        default -> false;
                    };
                    if (take) { pc = instruction.target(); continue; }
                }
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
                    int index = instruction.keys().indexOf(key);
                    pc = index < 0 ? instruction.target() : instruction.targets().get(index);
                    continue;
                }
                // JVMS 6.5.ireturn narrows int to boolean with & 1, not != 0.
                case IRETURN -> { return (stack.pop() & 1) != 0; }
            }
            pc++;
        }
        throw new IllegalStateException("Activation program terminated without IRETURN");
    }

    private static boolean metadataRead(List<AbstractInsnNode> code, int i) {
        return i + 4 < code.size() && isVar(code.get(i), Opcodes.ALOAD, 1)
                && isVar(code.get(i + 1), Opcodes.ILOAD, 2) && isVar(code.get(i + 2), Opcodes.ILOAD, 3)
                && isVar(code.get(i + 3), Opcodes.ILOAD, 4) && code.get(i + 4) instanceof MethodInsnNode call
                && call.getOpcode() == Opcodes.INVOKEVIRTUAL && !call.itf && WORLD.equals(call.owner)
                && ("getBlockMetadata".equals(call.name) || "func_72805_g".equals(call.name)) && "(III)I".equals(call.desc);
    }

    private static boolean clientSideRead(List<AbstractInsnNode> code, int i) {
        return i + 1 < code.size() && isVar(code.get(i), Opcodes.ALOAD, 1)
                && code.get(i + 1) instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETFIELD
                && WORLD.equals(field.owner) && ("isRemote".equals(field.name) || "field_72995_K".equals(field.name))
                && "Z".equals(field.desc);
    }

    private static boolean sneakingRead(List<AbstractInsnNode> code, int i) {
        return i + 1 < code.size() && isVar(code.get(i), Opcodes.ALOAD, 5)
                && code.get(i + 1) instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                && !call.itf && (PLAYER.equals(call.owner) || ENTITY.equals(call.owner))
                && ("isSneaking".equals(call.name) || "func_70093_af".equals(call.name)) && "()Z".equals(call.desc);
    }

    private static boolean isVar(AbstractInsnNode value, int opcode, int local) {
        return value instanceof VarInsnNode variable && value.getOpcode() == opcode && variable.var == local;
    }

    private static Instruction simpleOpcode(int opcode) {
        if (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5) return simple(Op.CONST_INT, opcode - Opcodes.ICONST_0);
        Op op = switch (opcode) {
            case Opcodes.NOP -> Op.NOP;
            case Opcodes.IADD -> Op.IADD;
            case Opcodes.ISUB -> Op.ISUB;
            case Opcodes.IMUL -> Op.IMUL;
            case Opcodes.IDIV -> Op.IDIV;
            case Opcodes.IREM -> Op.IREM;
            case Opcodes.INEG -> Op.INEG;
            case Opcodes.ISHL -> Op.ISHL;
            case Opcodes.ISHR -> Op.ISHR;
            case Opcodes.IUSHR -> Op.IUSHR;
            case Opcodes.IAND -> Op.IAND;
            case Opcodes.IOR -> Op.IOR;
            case Opcodes.IXOR -> Op.IXOR;
            case Opcodes.IRETURN -> Op.IRETURN;
            default -> null;
        };
        return op == null ? null : simple(op, 0);
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

    private static int sourceTarget(LabelNode label, Map<AbstractInsnNode, Integer> indices, int from, int size) {
        for (AbstractInsnNode cursor = label; cursor != null; cursor = cursor.getNext()) {
            Integer target = indices.get(cursor);
            if (target != null) { validateTarget(target, from, size); return target; }
        }
        throw invalid("invalid source branch target");
    }

    private static void validateTarget(int target, int from, int size) {
        if (target <= from || target >= size) throw invalid("backward/invalid jump target " + target + " from " + from);
    }

    private static void validateKeys(List<Integer> keys, int targetCount, boolean contiguous) {
        if (keys.size() != targetCount || keys.size() > MAX_INSTRUCTIONS) throw invalid("invalid or over-budget switch table");
        for (int i = 1; i < keys.size(); i++) {
            long delta = (long) keys.get(i) - keys.get(i - 1);
            if (delta <= 0 || (contiguous && delta != 1)) throw invalid("invalid switch key order/range");
        }
    }

    private static Instruction simple(Op op, int operand) { return new Instruction(op, operand, -1, null, null); }
    private static IllegalArgumentException invalid(String reason) { return new IllegalArgumentException(reason); }

    private static Map<String, ClassNode> loadClasses(Path path) throws IOException {
        Map<String, ClassNode> classes = new LinkedHashMap<>();
        try (JarFile jar = new JarFile(path.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;
                try (InputStream input = jar.getInputStream(entry)) {
                    ClassNode node = new ClassNode(Opcodes.ASM9);
                    new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    classes.put(node.name, node);
                } catch (RuntimeException ignored) {
                    // Registry/behavior analyzers own malformed-class diagnostics.
                }
            }
        }
        return classes;
    }
}
