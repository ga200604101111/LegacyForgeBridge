package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Proves the source-owned DataWatcher read/write surface for registrations already admitted by
 * {@link LegacyEntityDataWatcherAnalyzer}. The result is still proof IR: it does not execute or
 * rewrite entity methods. Every custom watcher call in the source entity lineage or an exactly
 * dispatchable reachable helper must use a constant source-owned index and a type compatible with
 * the proven definition, otherwise that entity's access surface remains closed.
 */
public final class LegacyEntityDataWatcherAccessAnalyzer {
    private static final String DATA_WATCHER = "net/minecraft/entity/DataWatcher";
    private static final String DATA_WATCHER_DESC = "Lnet/minecraft/entity/DataWatcher;";
    private static final Set<String> ENTITY_INIT_NAMES = Set.of("entityInit", "func_70088_a");
    private static final Set<String> ADD_NAMES = Set.of("addObject", "func_75682_a", "addObjectByDataType", "func_82709_a");
    private static final Set<String> UPDATE_NAMES = Set.of("updateObject", "func_75692_b");
    private static final Set<String> FORCE_DIRTY_NAMES = Set.of("setObjectWatched", "func_82708_h");
    private static final Set<String> WATCHER_ACCESSOR_NAMES = Set.of("getDataWatcher", "func_70096_w");

    private static final Map<String,String> GETTERS = Map.ofEntries(
            Map.entry("getWatchableObjectByte(I)B", "byte"), Map.entry("func_75683_a(I)B", "byte"),
            Map.entry("getWatchableObjectShort(I)S", "short"), Map.entry("func_75693_b(I)S", "short"),
            Map.entry("getWatchableObjectInt(I)I", "int"), Map.entry("func_75679_c(I)I", "int"),
            Map.entry("getWatchableObjectFloat(I)F", "float"), Map.entry("func_111145_d(I)F", "float"),
            Map.entry("getWatchableObjectString(I)Ljava/lang/String;", "string"), Map.entry("func_75681_e(I)Ljava/lang/String;", "string")
    );
    private static final Set<String> UNSUPPORTED_TYPED_GETTERS = Set.of(
            "getWatchableObjectItemStack(I)Lnet/minecraft/item/ItemStack;",
            "func_82710_f(I)Lnet/minecraft/item/ItemStack;"
    );

    public record Access(int index, String operation, String valueKind, String sourceOwner,
                         String sourceMethod, String sourceDescriptor) { }
    public record Rule(String registryName, String sourceClass, List<Access> accesses) {
        public Rule { accesses = List.copyOf(accesses); }
    }
    public record Skipped(String registryName, String sourceClass, String reason) { }
    public record Analysis(List<Rule> rules, List<Skipped> skipped, List<String> diagnostics) {
        public Analysis { rules = List.copyOf(rules); skipped = List.copyOf(skipped); diagnostics = List.copyOf(diagnostics); }
    }

    private record MethodContext(ClassNode owner, MethodNode method, Frame<SourceValue>[] frames,
                                 Map<AbstractInsnNode,Integer> indices) { }
    private record MethodTarget(String owner, String name, String descriptor, Set<Integer> entityLocals) {
        MethodTarget {
            entityLocals = Set.copyOf(entityLocals);
        }
    }
    private record Scan(List<Access> accesses, String error) { }

    private final Map<String,ClassNode> classes = new LinkedHashMap<>();
    private final Map<String,MethodContext> contexts = new HashMap<>();
    private final List<String> diagnostics = new ArrayList<>();

    public Analysis analyze(Path jarPath) throws IOException {
        classes.clear(); contexts.clear(); diagnostics.clear(); load(jarPath);
        LegacyEntityDataWatcherAnalyzer.Analysis definitions = new LegacyEntityDataWatcherAnalyzer().analyze(jarPath);
        List<Rule> rules = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        for (LegacyEntityDataWatcherAnalyzer.Rule definition : definitions.rules()) {
            LinkedHashMap<Integer,String> schema = new LinkedHashMap<>();
            definition.entries().forEach(entry -> schema.put(entry.index(), entry.valueKind()));
            Scan scan = scanLineage(definition.sourceClass(), schema);
            if (scan.error() != null) skipped.add(new Skipped(definition.registryName(), definition.sourceClass(), scan.error()));
            else rules.add(new Rule(definition.registryName(), definition.sourceClass(), scan.accesses()));
        }
        diagnostics.addAll(definitions.diagnostics());
        return new Analysis(rules, skipped, diagnostics);
    }

    private Scan scanLineage(String sourceClass, Map<Integer,String> schema) {
        List<Access> accesses = new ArrayList<>();
        Deque<MethodTarget> pending = new ArrayDeque<>();
        Set<MethodTarget> seenMethods = new LinkedHashSet<>();

        String current = sourceClass;
        Set<String> seenOwners = new HashSet<>();
        while (current != null && seenOwners.add(current)) {
            ClassNode node = classes.get(current);
            if (node == null) break;
            for (MethodNode method : node.methods) {
                if ("<init>".equals(method.name) || "<clinit>".equals(method.name) || ENTITY_INIT_NAMES.contains(method.name)) continue;
                if ((method.access & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) continue;
                pending.addLast(new MethodTarget(node.name, method.name, method.desc, Set.of(0)));
            }
            current = node.superName;
        }

        while (!pending.isEmpty()) {
            MethodTarget target = pending.removeFirst();
            if (!seenMethods.add(target)) continue;
            ClassNode owner = classes.get(target.owner());
            MethodNode method = findMethod(owner, target.name(), target.descriptor());
            if (owner == null || method == null) continue;

            MethodContext context;
            try { context = context(owner, method); }
            catch (AnalyzerException error) {
                return new Scan(List.of(), "Could not prove DataWatcher access dataflow in "
                        + owner.name + "." + method.name + method.desc + ": " + error.getMessage());
            }

            String error = scanMethod(context, target.entityLocals(), schema, accesses, pending);
            if (error != null) return new Scan(List.of(), error);
        }
        return new Scan(List.copyOf(accesses), null);
    }

    private String scanMethod(MethodContext context, Set<Integer> entityLocals, Map<Integer,String> schema,
                              List<Access> accesses, Deque<MethodTarget> pending) {
        MethodNode method = context.method();
        for (int i = 0; i < method.instructions.size(); i++) {
            AbstractInsnNode instruction = method.instructions.get(i);
            if (!(instruction instanceof MethodInsnNode call)) continue;

            if (DATA_WATCHER.equals(call.owner)) {
                if (ADD_NAMES.contains(call.name))
                    return "DataWatcher definition outside entityInit is unsupported in " + source(context) + ".";
                if (FORCE_DIRTY_NAMES.contains(call.name))
                    return "DataWatcher.setObjectWatched/force-dirty semantics are not mapped yet in " + source(context) + ".";
                String getterKind = GETTERS.get(call.name + call.desc);
                if (getterKind != null) {
                    String error = readAccess(context, i, getterKind, schema, accesses, entityLocals);
                    if (error != null) return error;
                    continue;
                }
                if (UNSUPPORTED_TYPED_GETTERS.contains(call.name + call.desc))
                    return "ItemStack DataWatcher access is outside the current primitive/string mapping in " + source(context) + ".";
                if (UPDATE_NAMES.contains(call.name) && "(ILjava/lang/Object;)V".equals(call.desc)) {
                    String error = writeAccess(context, i, schema, accesses, entityLocals);
                    if (error != null) return error;
                    continue;
                }
                return "Unsupported DataWatcher call " + call.name + call.desc + " in " + source(context) + ".";
            }

            MethodTarget helper = exactHelperTarget(context, i, call, entityLocals);
            if (helper != null) pending.addLast(helper);
        }
        return null;
    }

    private MethodTarget exactHelperTarget(MethodContext context, int instructionIndex, MethodInsnNode call,
                                           Set<Integer> entityLocals) {
        ClassNode helperOwner = classes.get(call.owner);
        MethodNode helper = findMethod(helperOwner, call.name, call.desc);
        if (helperOwner == null || helper == null) return null;

        boolean targetStatic = (helper.access & Opcodes.ACC_STATIC) != 0;
        int opcode = call.getOpcode();
        boolean exact = switch (opcode) {
            case Opcodes.INVOKESTATIC -> targetStatic;
            case Opcodes.INVOKESPECIAL -> !targetStatic;
            case Opcodes.INVOKEVIRTUAL -> !targetStatic
                    && (((helper.access & Opcodes.ACC_FINAL) != 0) || ((helperOwner.access & Opcodes.ACC_FINAL) != 0));
            default -> false;
        };
        if (!exact) return null;

        Type[] argumentTypes = Type.getArgumentTypes(call.desc);
        Frame<SourceValue> frame = context.frames()[instructionIndex];
        int requiredStack = argumentTypes.length + (targetStatic ? 0 : 1);
        if (frame == null || frame.getStackSize() < requiredStack) return null;

        int argumentStart = frame.getStackSize() - argumentTypes.length;
        LinkedHashSet<Integer> boundLocals = new LinkedHashSet<>();
        int local = targetStatic ? 0 : 1;
        if (!targetStatic) {
            int receiverIndex = argumentStart - 1;
            if (receiverIndex < 0) return null;
            if (isEntityValue(context, frame.getStack(receiverIndex), entityLocals, 0, new HashSet<>()))
                boundLocals.add(0);
        }
        for (int argument = 0; argument < argumentTypes.length; argument++) {
            Type type = argumentTypes[argument];
            if ((type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY)
                    && isEntityValue(context, frame.getStack(argumentStart + argument), entityLocals, 0, new HashSet<>())) {
                boundLocals.add(local);
            }
            local += type.getSize();
        }
        if (boundLocals.isEmpty()) return null;
        return new MethodTarget(call.owner, call.name, call.desc, boundLocals);
    }

    private String readAccess(MethodContext context, int instructionIndex, String getterKind,
                              Map<Integer,String> schema, List<Access> accesses, Set<Integer> entityLocals) {
        Frame<SourceValue> frame = context.frames()[instructionIndex];
        if (frame == null || frame.getStackSize() < 2) return "Missing DataWatcher getter frame in " + source(context) + ".";
        int start = frame.getStackSize() - 2;
        if (!isEntityWatcher(context, frame.getStack(start), entityLocals))
            return "DataWatcher getter receiver is not proven as the source entity DataWatcher in " + source(context) + ".";
        Integer index = scalarInt(context, frame.getStack(start + 1), 0, new HashSet<>());
        if (index == null) return "Dynamic/unproven DataWatcher read index in " + source(context) + ".";
        String definedKind = schema.get(index);
        if (definedKind == null) return "DataWatcher read index " + index + " is not source-owned/proven by this entity schema in " + source(context) + ".";
        if (!definedKind.equals(getterKind)) return "DataWatcher getter type mismatch at index " + index + ": defined=" + definedKind + ", read=" + getterKind + ".";
        accesses.add(new Access(index, "read", getterKind, context.owner().name, context.method().name, context.method().desc));
        return null;
    }

    private String writeAccess(MethodContext context, int instructionIndex, Map<Integer,String> schema,
                               List<Access> accesses, Set<Integer> entityLocals) {
        Frame<SourceValue> frame = context.frames()[instructionIndex];
        if (frame == null || frame.getStackSize() < 3) return "Missing DataWatcher update frame in " + source(context) + ".";
        int start = frame.getStackSize() - 3;
        if (!isEntityWatcher(context, frame.getStack(start), entityLocals))
            return "DataWatcher update receiver is not proven as the source entity DataWatcher in " + source(context) + ".";
        Integer index = scalarInt(context, frame.getStack(start + 1), 0, new HashSet<>());
        if (index == null) return "Dynamic/unproven DataWatcher write index in " + source(context) + ".";
        String definedKind = schema.get(index);
        if (definedKind == null) return "DataWatcher write index " + index + " is not source-owned/proven by this entity schema in " + source(context) + ".";
        String writtenKind = objectKind(context, frame.getStack(start + 2));
        if (writtenKind == null) return "DataWatcher write value type is unproven at index " + index + " in " + source(context) + ".";
        if (!definedKind.equals(writtenKind)) return "DataWatcher write type mismatch at index " + index + ": defined=" + definedKind + ", write=" + writtenKind + ".";
        accesses.add(new Access(index, "write", writtenKind, context.owner().name, context.method().name, context.method().desc));
        return null;
    }

    private static String source(MethodContext context) {
        return context.owner().name + "." + context.method().name + context.method().desc;
    }

    private String objectKind(MethodContext context, SourceValue value) {
        return objectKind(context, value, 0, new HashSet<>());
    }

    private String objectKind(
            MethodContext context,
            SourceValue value,
            int depth,
            Set<AbstractInsnNode> guard
    ) {
        if (value == null || depth > 24 || value.insns == null || value.insns.isEmpty()) return null;
        String kind = null;
        for (AbstractInsnNode producer : value.insns) {
            if (!guard.add(producer)) return null;
            String candidate = objectProducerKind(context, producer, depth + 1, guard);
            guard.remove(producer);
            if (candidate == null) return null;
            if (kind == null) kind = candidate;
            else if (!kind.equals(candidate)) return null;
        }
        return kind;
    }

    private String objectProducerKind(
            MethodContext context,
            AbstractInsnNode producer,
            int depth,
            Set<AbstractInsnNode> guard
    ) {
        if (producer instanceof LdcInsnNode ldc && ldc.cst instanceof String) return "string";
        if (producer instanceof MethodInsnNode call) {
            if (call.getOpcode() == Opcodes.INVOKESTATIC && "valueOf".equals(call.name)) {
                return switch (call.owner) {
                    case "java/lang/Byte" -> "byte";
                    case "java/lang/Short" -> "short";
                    case "java/lang/Integer" -> "int";
                    case "java/lang/Float" -> "float";
                    default -> returnKind(call.desc);
                };
            }
            return returnKind(call.desc);
        }
        if (producer instanceof FieldInsnNode field) return descriptorKind(field.desc);
        if (producer instanceof TypeInsnNode cast && cast.getOpcode() == Opcodes.CHECKCAST) {
            String castKind = descriptorKind("L" + cast.desc + ";");
            if (castKind != null) return castKind;
            Integer index = context.indices().get(producer);
            if (index == null) return null;
            Frame<SourceValue> frame = context.frames()[index];
            return frame == null || frame.getStackSize() == 0 ? null
                    : objectKind(context, frame.getStack(frame.getStackSize() - 1), depth + 1, guard);
        }
        if (producer instanceof VarInsnNode variable && variable.getOpcode() == Opcodes.ALOAD) {
            String parameterKind = parameterReferenceKind(context.method(), variable.var);
            if (parameterKind != null) return parameterKind;
            Integer index = context.indices().get(producer);
            if (index == null) return null;
            Frame<SourceValue> frame = context.frames()[index];
            return frame == null || variable.var >= frame.getLocals() ? null
                    : objectKind(context, frame.getLocal(variable.var), depth + 1, guard);
        }
        return null;
    }

    private static String parameterReferenceKind(MethodNode method, int local) {
        int slot = (method.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
        for (Type type : Type.getArgumentTypes(method.desc)) {
            if (slot == local) {
                return switch (type.getSort()) {
                    case Type.OBJECT -> descriptorKind(type.getDescriptor());
                    default -> null;
                };
            }
            slot += type.getSize();
        }
        return null;
    }

    private static String returnKind(String descriptor) { return descriptorKind(Type.getReturnType(descriptor).getDescriptor()); }
    private static String descriptorKind(String descriptor) {
        return switch (descriptor) {
            case "Ljava/lang/Byte;" -> "byte";
            case "Ljava/lang/Short;" -> "short";
            case "Ljava/lang/Integer;" -> "int";
            case "Ljava/lang/Float;" -> "float";
            case "Ljava/lang/String;" -> "string";
            default -> null;
        };
    }

    private MethodContext context(ClassNode owner, MethodNode method) throws AnalyzerException {
        String key = owner.name + '\u0000' + method.name + '\u0000' + method.desc;
        MethodContext cached = contexts.get(key);
        if (cached != null) return cached;
        Analyzer<SourceValue> analyzer = new Analyzer<>(new SourceInterpreter());
        Frame<SourceValue>[] frames = analyzer.analyze(owner.name, method);
        Map<AbstractInsnNode,Integer> indices = new IdentityHashMap<>();
        for (int i = 0; i < method.instructions.size(); i++) indices.put(method.instructions.get(i), i);
        MethodContext result = new MethodContext(owner, method, frames, indices);
        contexts.put(key, result);
        return result;
    }

    private boolean isEntityWatcher(MethodContext context, SourceValue receiver, Set<Integer> entityLocals) {
        if (receiver == null || receiver.insns == null || receiver.insns.size() != 1) return false;
        AbstractInsnNode producer = receiver.insns.iterator().next();
        Integer index = context.indices().get(producer);
        if (index == null) return false;
        Frame<SourceValue> frame = context.frames()[index];
        if (producer instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETFIELD && DATA_WATCHER_DESC.equals(field.desc))
            return frame != null && frame.getStackSize() > 0
                    && isEntityValue(context, frame.getStack(frame.getStackSize() - 1), entityLocals, 0, new HashSet<>());
        if (producer instanceof MethodInsnNode call && call.getOpcode() != Opcodes.INVOKESTATIC
                && WATCHER_ACCESSOR_NAMES.contains(call.name) && ("()" + DATA_WATCHER_DESC).equals(call.desc))
            return frame != null && frame.getStackSize() > 0
                    && isEntityValue(context, frame.getStack(frame.getStackSize() - 1), entityLocals, 0, new HashSet<>());
        return false;
    }

    private boolean isEntityValue(MethodContext context, SourceValue value, Set<Integer> entityLocals,
                                  int depth, Set<AbstractInsnNode> guard) {
        if (value == null || depth > 24 || value.insns == null || value.insns.isEmpty()) return false;
        for (AbstractInsnNode producer : value.insns) {
            if (!guard.add(producer)) return false;
            boolean proven = isEntityProducer(context, producer, entityLocals, depth + 1, guard);
            guard.remove(producer);
            if (!proven) return false;
        }
        return true;
    }

    private boolean isEntityProducer(MethodContext context, AbstractInsnNode producer, Set<Integer> entityLocals,
                                     int depth, Set<AbstractInsnNode> guard) {
        if (producer instanceof VarInsnNode variable && variable.getOpcode() == Opcodes.ALOAD)
            return entityLocals.contains(variable.var);
        if (producer instanceof TypeInsnNode type && type.getOpcode() == Opcodes.CHECKCAST) {
            Integer index = context.indices().get(producer);
            if (index == null) return false;
            Frame<SourceValue> frame = context.frames()[index];
            return frame != null && frame.getStackSize() > 0
                    && isEntityValue(context, frame.getStack(frame.getStackSize() - 1), entityLocals, depth, guard);
        }
        return false;
    }

    private Integer scalarInt(MethodContext context, SourceValue value, int depth, Set<AbstractInsnNode> guard) {
        if (value == null || depth > 24 || value.insns == null || value.insns.isEmpty()) return null;
        Integer result = null;
        for (AbstractInsnNode producer : value.insns) {
            if (!guard.add(producer)) return null;
            Integer candidate = scalarProducer(context, producer, depth + 1, guard);
            guard.remove(producer);
            if (candidate == null) return null;
            if (result == null) result = candidate;
            else if (!result.equals(candidate)) return null;
        }
        return result;
    }

    private Integer scalarProducer(MethodContext context, AbstractInsnNode producer, int depth, Set<AbstractInsnNode> guard) {
        if (producer instanceof IntInsnNode integer && (integer.getOpcode() == Opcodes.BIPUSH || integer.getOpcode() == Opcodes.SIPUSH)) return integer.operand;
        if (producer instanceof LdcInsnNode ldc && ldc.cst instanceof Integer integer) return integer;
        if (producer instanceof InsnNode insn && insn.getOpcode() >= Opcodes.ICONST_M1 && insn.getOpcode() <= Opcodes.ICONST_5)
            return insn.getOpcode() - Opcodes.ICONST_0;
        if (producer instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC && "I".equals(field.desc)) {
            ClassNode owner = classes.get(field.owner);
            if (owner == null) return null;
            FieldNode source = owner.fields.stream().filter(f -> f.name.equals(field.name) && f.desc.equals(field.desc)).findFirst().orElse(null);
            return source != null && source.value instanceof Integer value ? value : null;
        }
        return null;
    }

    private static MethodNode findMethod(ClassNode owner, String name, String descriptor) {
        if (owner == null) return null;
        for (MethodNode method : owner.methods)
            if (method.name.equals(name) && method.desc.equals(descriptor)) return method;
        return null;
    }

    private void load(Path jarPath) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class") || entry.getName().equals("module-info.class")) continue;
                try (InputStream input = jar.getInputStream(entry)) {
                    ClassNode node = new ClassNode(Opcodes.ASM9);
                    new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    classes.put(node.name, node);
                } catch (RuntimeException malformed) {
                    diagnostics.add("Unreadable DataWatcher access class " + entry.getName() + ": " + malformed.getClass().getSimpleName());
                }
            }
        }
    }
}
