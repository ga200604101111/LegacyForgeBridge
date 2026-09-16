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
 * Bounded source proof for legacy EntityRegistry registration metadata plus source-owned
 * DataWatcher definitions. This stage intentionally does not generate an Entity runtime: it only
 * admits watcher schemas whose index, value type, default value, and receiver are all proven from
 * bytecode. Unknown helper/dataflow shapes fail closed for that entity registration.
 */
public final class LegacyEntityDataWatcherAnalyzer {
    private static final String DATA_WATCHER = "net/minecraft/entity/DataWatcher";
    private static final String DATA_WATCHER_DESC = "Lnet/minecraft/entity/DataWatcher;";
    private static final Set<String> ENTITY_INIT_NAMES = Set.of("entityInit", "func_70088_a");
    private static final Set<String> ADD_OBJECT_NAMES = Set.of("addObject", "func_75682_a");
    private static final Set<String> ADD_TYPED_NAMES = Set.of("addObjectByDataType", "func_82709_a");
    private static final int MAX_HELPER_SCAN = 256;

    public record Entry(int index, String valueKind, Object defaultValue, String declaredBy) { }
    public record Rule(String registryName, String sourceClass, int numericId, int trackingRange,
                       int updateFrequency, boolean velocityUpdates, List<Entry> entries) {
        public Rule { entries = List.copyOf(entries); }
    }
    public record Skipped(String registryName, String sourceClass, String reason) { }
    public record Analysis(List<Rule> rules, List<Skipped> skipped, List<String> diagnostics) {
        public Analysis {
            rules = List.copyOf(rules);
            skipped = List.copyOf(skipped);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private record RegistrationProof(String registryName, String sourceClass, int numericId,
                                     int trackingRange, int updateFrequency, boolean velocityUpdates) { }
    private record MethodKey(String owner, String name, String descriptor) { }
    private record MethodContext(ClassNode owner, MethodNode method, Frame<SourceValue>[] frames,
                                 Map<AbstractInsnNode,Integer> indices) { }
    private record DefinitionResult(List<Entry> entries, String error) { }
    private record Decoded(String kind, Object value) { }

    private final Map<String,ClassNode> classes = new LinkedHashMap<>();
    private final Map<MethodKey,MethodContext> methodContexts = new HashMap<>();
    private final Map<MethodKey,Boolean> watcherHelperCache = new HashMap<>();
    private final List<String> diagnostics = new ArrayList<>();

    public Analysis analyze(Path jarPath) throws IOException {
        classes.clear();
        methodContexts.clear();
        watcherHelperCache.clear();
        diagnostics.clear();
        load(jarPath);

        LegacyLifecycleAnalyzer.Analysis lifecycle = new LegacyLifecycleAnalyzer().analyze(jarPath);
        List<Rule> rules = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        for (LegacyLifecycleAnalyzer.Registration registration : lifecycle.of(LegacyLifecycleAnalyzer.Kind.ENTITY)) {
            RegistrationProof proof = registrationProof(registration);
            String sourceClass = sourceClass(registration);
            String registryName = registryName(registration);
            if (proof == null) {
                skipped.add(new Skipped(registryName, sourceClass,
                        "registerModEntity arguments were not fully constant/proven."));
                continue;
            }
            if (!classes.containsKey(proof.sourceClass())) {
                skipped.add(new Skipped(proof.registryName(), proof.sourceClass(),
                        "Registered entity class is not present in the source JAR."));
                continue;
            }
            DefinitionResult definitions = definitions(proof.sourceClass());
            if (definitions.error() != null) {
                skipped.add(new Skipped(proof.registryName(), proof.sourceClass(), definitions.error()));
                continue;
            }
            rules.add(new Rule(proof.registryName(), proof.sourceClass(), proof.numericId(),
                    proof.trackingRange(), proof.updateFrequency(), proof.velocityUpdates(), definitions.entries()));
        }
        return new Analysis(rules, skipped, diagnostics);
    }

    private RegistrationProof registrationProof(LegacyLifecycleAnalyzer.Registration registration) {
        List<LegacyLifecycleAnalyzer.Value> args = registration.arguments();
        if (args.size() < 7) return null;
        if (!(args.get(0) instanceof LegacyLifecycleAnalyzer.TypeValue type)) return null;
        if (!(args.get(1) instanceof LegacyLifecycleAnalyzer.TextValue name) || name.value().isBlank()) return null;
        Integer numericId = integer(args.get(2));
        Integer trackingRange = integer(args.get(4));
        Integer updateFrequency = integer(args.get(5));
        Boolean velocity = bool(args.get(6));
        if (numericId == null || trackingRange == null || updateFrequency == null || velocity == null) return null;
        if (trackingRange <= 0 || updateFrequency <= 0) return null;
        return new RegistrationProof(name.value(), type.internalName(), numericId, trackingRange, updateFrequency, velocity);
    }

    private static String sourceClass(LegacyLifecycleAnalyzer.Registration registration) {
        if (!registration.arguments().isEmpty() && registration.arguments().getFirst() instanceof LegacyLifecycleAnalyzer.TypeValue type)
            return type.internalName();
        return null;
    }

    private static String registryName(LegacyLifecycleAnalyzer.Registration registration) {
        if (registration.arguments().size() > 1 && registration.arguments().get(1) instanceof LegacyLifecycleAnalyzer.TextValue text)
            return text.value();
        return null;
    }

    private static Integer integer(LegacyLifecycleAnalyzer.Value value) {
        if (!(value instanceof LegacyLifecycleAnalyzer.NumberValue number)) return null;
        double raw = number.value().doubleValue();
        if (!Double.isFinite(raw) || raw != Math.rint(raw) || raw < Integer.MIN_VALUE || raw > Integer.MAX_VALUE) return null;
        return (int) raw;
    }

    private static Boolean bool(LegacyLifecycleAnalyzer.Value value) {
        if (value instanceof LegacyLifecycleAnalyzer.BooleanValue b) return b.value();
        Integer number = integer(value);
        if (number == null || (number != 0 && number != 1)) return null;
        return number == 1;
    }

    private DefinitionResult definitions(String sourceClass) {
        MethodKey root = effectiveEntityInit(sourceClass);
        if (root == null) return new DefinitionResult(List.of(), null);
        List<Entry> entries = new ArrayList<>();
        String error = collectDefinitions(root, entries, new LinkedHashSet<>());
        if (error != null) return new DefinitionResult(List.of(), error);
        LinkedHashMap<Integer,Entry> byIndex = new LinkedHashMap<>();
        for (Entry entry : entries) {
            if (entry.index() < 0 || entry.index() > 31)
                return new DefinitionResult(List.of(), "Legacy DataWatcher index outside 0..31: " + entry.index() + ".");
            Entry previous = byIndex.putIfAbsent(entry.index(), entry);
            if (previous != null)
                return new DefinitionResult(List.of(), "Duplicate legacy DataWatcher definition for index " + entry.index() + ".");
        }
        return new DefinitionResult(List.copyOf(byIndex.values()), null);
    }

    private MethodKey effectiveEntityInit(String sourceClass) {
        String current = sourceClass;
        Set<String> seen = new HashSet<>();
        while (current != null && seen.add(current)) {
            ClassNode node = classes.get(current);
            if (node == null) return null;
            for (MethodNode method : node.methods) {
                if (ENTITY_INIT_NAMES.contains(method.name) && "()V".equals(method.desc))
                    return new MethodKey(current, method.name, method.desc);
            }
            current = node.superName;
        }
        return null;
    }

    private String collectDefinitions(MethodKey key, List<Entry> entries, Set<MethodKey> visiting) {
        if (!visiting.add(key)) return "Recursive entityInit/helper watcher definition path is unsupported: " + key + ".";
        MethodContext context;
        try {
            context = context(key);
        } catch (AnalyzerException error) {
            visiting.remove(key);
            return "Could not prove DataWatcher dataflow in " + key.owner() + "." + key.name() + key.descriptor() + ": " + error.getMessage();
        }
        if (context == null) {
            visiting.remove(key);
            return "Missing source DataWatcher method " + key + ".";
        }

        int explicitSourceSuperCalls = 0;
        for (int i = 0; i < context.method().instructions.size(); i++) {
            AbstractInsnNode instruction = context.method().instructions.get(i);
            if (!(instruction instanceof MethodInsnNode call)) continue;

            if (DATA_WATCHER.equals(call.owner)) {
                if (ADD_TYPED_NAMES.contains(call.name) && "(II)V".equals(call.desc)) {
                    visiting.remove(key);
                    return "DataWatcher.addObjectByDataType lacks a source default-value proof in " + key.owner() + "." + key.name() + ".";
                }
                if (!ADD_OBJECT_NAMES.contains(call.name) || !"(ILjava/lang/Object;)V".equals(call.desc)) continue;
                Frame<SourceValue> frame = context.frames()[i];
                if (frame == null || frame.getStackSize() < 3) {
                    visiting.remove(key);
                    return "Missing stack frame for DataWatcher.addObject in " + key.owner() + ".";
                }
                int start = frame.getStackSize() - 3;
                if (!isThisWatcher(context, frame.getStack(start))) {
                    visiting.remove(key);
                    return "DataWatcher receiver is not proven as this.dataWatcher in " + key.owner() + "." + key.name() + ".";
                }
                Integer index = scalarInt(context, frame.getStack(start + 1), 0, new HashSet<>());
                if (index == null) {
                    visiting.remove(key);
                    return "Dynamic/unproven DataWatcher index in " + key.owner() + "." + key.name() + ".";
                }
                Decoded decoded = decodeDefault(context, frame.getStack(start + 2), 0, new HashSet<>());
                if (decoded == null) {
                    visiting.remove(key);
                    return "Unsupported/unproven DataWatcher default value at index " + index + " in " + key.owner() + "." + key.name() + ".";
                }
                entries.add(new Entry(index, decoded.kind(), decoded.value(), key.owner()));
                continue;
            }

            if (call.getOpcode() == Opcodes.INVOKESPECIAL
                    && ENTITY_INIT_NAMES.contains(call.name)
                    && "()V".equals(call.desc)
                    && call.owner.equals(context.owner().superName)
                    && classes.containsKey(call.owner)) {
                MethodKey parent = directMethod(call.owner, call.name, call.desc);
                if (parent != null) {
                    explicitSourceSuperCalls++;
                    String error = collectDefinitions(parent, entries, visiting);
                    if (error != null) {
                        visiting.remove(key);
                        return error;
                    }
                }
                continue;
            }

            MethodKey helper = new MethodKey(call.owner, call.name, call.desc);
            if (classes.containsKey(call.owner) && containsWatcherDefinition(helper, new HashSet<>(), 0)) {
                visiting.remove(key);
                return "DataWatcher definition is hidden behind unsupported source helper " + helper + ".";
            }
        }
        visiting.remove(key);
        if (explicitSourceSuperCalls > 1)
            return "Multiple source super entityInit calls are outside the bounded DataWatcher proof for " + key.owner() + ".";
        return null;
    }

    private boolean containsWatcherDefinition(MethodKey key, Set<MethodKey> visiting, int depth) {
        Boolean cached = watcherHelperCache.get(key);
        if (cached != null) return cached;
        if (depth > MAX_HELPER_SCAN || !visiting.add(key)) return true;
        ClassNode owner = classes.get(key.owner());
        if (owner == null) { visiting.remove(key); return false; }
        MethodNode method = owner.methods.stream().filter(m -> m.name.equals(key.name()) && m.desc.equals(key.descriptor())).findFirst().orElse(null);
        if (method == null) { visiting.remove(key); return false; }
        boolean found = false;
        for (AbstractInsnNode instruction : method.instructions) {
            if (!(instruction instanceof MethodInsnNode call)) continue;
            if (DATA_WATCHER.equals(call.owner) && (ADD_OBJECT_NAMES.contains(call.name) || ADD_TYPED_NAMES.contains(call.name))) {
                found = true;
                break;
            }
            if (classes.containsKey(call.owner) && containsWatcherDefinition(new MethodKey(call.owner, call.name, call.desc), visiting, depth + 1)) {
                found = true;
                break;
            }
        }
        visiting.remove(key);
        watcherHelperCache.put(key, found);
        return found;
    }

    private MethodKey directMethod(String owner, String name, String descriptor) {
        ClassNode node = classes.get(owner);
        if (node == null) return null;
        return node.methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(descriptor))
                ? new MethodKey(owner, name, descriptor) : null;
    }

    private MethodContext context(MethodKey key) throws AnalyzerException {
        MethodContext cached = methodContexts.get(key);
        if (cached != null) return cached;
        ClassNode owner = classes.get(key.owner());
        if (owner == null) return null;
        MethodNode method = owner.methods.stream().filter(m -> m.name.equals(key.name()) && m.desc.equals(key.descriptor())).findFirst().orElse(null);
        if (method == null) return null;
        Analyzer<SourceValue> analyzer = new Analyzer<>(new SourceInterpreter());
        Frame<SourceValue>[] frames = analyzer.analyze(owner.name, method);
        Map<AbstractInsnNode,Integer> indices = new IdentityHashMap<>();
        for (int i = 0; i < method.instructions.size(); i++) indices.put(method.instructions.get(i), i);
        MethodContext result = new MethodContext(owner, method, frames, indices);
        methodContexts.put(key, result);
        return result;
    }

    private boolean isThisWatcher(MethodContext context, SourceValue receiver) {
        if (receiver == null || receiver.insns == null || receiver.insns.size() != 1) return false;
        AbstractInsnNode producer = receiver.insns.iterator().next();
        if (!(producer instanceof FieldInsnNode field) || field.getOpcode() != Opcodes.GETFIELD || !DATA_WATCHER_DESC.equals(field.desc)) return false;
        Integer index = context.indices().get(producer);
        if (index == null) return false;
        Frame<SourceValue> frame = context.frames()[index];
        return frame != null && frame.getStackSize() > 0 && isThis(frame.getStack(frame.getStackSize() - 1));
    }

    private static boolean isThis(SourceValue value) {
        if (value == null || value.insns == null || value.insns.size() != 1) return false;
        AbstractInsnNode producer = value.insns.iterator().next();
        return producer instanceof VarInsnNode variable && variable.getOpcode() == Opcodes.ALOAD && variable.var == 0;
    }

    private Integer scalarInt(MethodContext context, SourceValue value, int depth, Set<AbstractInsnNode> guard) {
        Object scalar = scalar(context, value, depth, guard);
        if (!(scalar instanceof Number number)) return null;
        double raw = number.doubleValue();
        if (!Double.isFinite(raw) || raw != Math.rint(raw) || raw < Integer.MIN_VALUE || raw > Integer.MAX_VALUE) return null;
        return (int) raw;
    }

    private Object scalar(MethodContext context, SourceValue value, int depth, Set<AbstractInsnNode> guard) {
        if (value == null || depth > 32 || value.insns == null || value.insns.isEmpty()) return null;
        Object resolved = null;
        boolean have = false;
        for (AbstractInsnNode producer : value.insns) {
            if (!guard.add(producer)) return null;
            Object candidate = scalarProducer(context, producer, depth + 1, guard);
            guard.remove(producer);
            if (candidate == null) return null;
            if (!have) { resolved = candidate; have = true; }
            else if (!Objects.equals(resolved, candidate)) return null;
        }
        return have ? resolved : null;
    }

    private Object scalarProducer(MethodContext context, AbstractInsnNode producer, int depth, Set<AbstractInsnNode> guard) {
        if (producer instanceof LdcInsnNode ldc && (ldc.cst instanceof Number || ldc.cst instanceof String)) return ldc.cst;
        if (producer instanceof IntInsnNode integer && (integer.getOpcode() == Opcodes.BIPUSH || integer.getOpcode() == Opcodes.SIPUSH)) return integer.operand;
        if (producer instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC) {
            ClassNode owner = classes.get(field.owner);
            if (owner == null) return null;
            FieldNode source = owner.fields.stream().filter(f -> f.name.equals(field.name) && f.desc.equals(field.desc)).findFirst().orElse(null);
            return source == null ? null : source.value;
        }
        if (producer instanceof InsnNode insn) {
            int op = insn.getOpcode();
            if (op >= Opcodes.ICONST_M1 && op <= Opcodes.ICONST_5) return op - Opcodes.ICONST_0;
            if (op == Opcodes.FCONST_0) return 0F;
            if (op == Opcodes.FCONST_1) return 1F;
            if (op == Opcodes.FCONST_2) return 2F;
            if (op == Opcodes.LCONST_0) return 0L;
            if (op == Opcodes.LCONST_1) return 1L;
            if (op == Opcodes.DCONST_0) return 0D;
            if (op == Opcodes.DCONST_1) return 1D;
            if (op == Opcodes.I2B || op == Opcodes.I2S || op == Opcodes.I2F || op == Opcodes.I2L || op == Opcodes.I2D) {
                Integer index = context.indices().get(producer);
                if (index == null) return null;
                Frame<SourceValue> frame = context.frames()[index];
                if (frame == null || frame.getStackSize() == 0) return null;
                Object input = scalar(context, frame.getStack(frame.getStackSize() - 1), depth + 1, guard);
                if (!(input instanceof Number number)) return null;
                return switch (op) {
                    case Opcodes.I2B -> number.byteValue();
                    case Opcodes.I2S -> number.shortValue();
                    case Opcodes.I2F -> number.floatValue();
                    case Opcodes.I2L -> number.longValue();
                    default -> number.doubleValue();
                };
            }
        }
        if (producer instanceof MethodInsnNode call) {
            Decoded decoded = decodeWrapper(context, call, depth + 1, guard);
            return decoded == null ? null : decoded.value();
        }
        return null;
    }

    private Decoded decodeDefault(MethodContext context, SourceValue value, int depth, Set<AbstractInsnNode> guard) {
        if (value == null || depth > 32 || value.insns == null || value.insns.isEmpty()) return null;
        Decoded resolved = null;
        for (AbstractInsnNode producer : value.insns) {
            if (!guard.add(producer)) return null;
            Decoded candidate = decodeProducer(context, producer, depth + 1, guard);
            guard.remove(producer);
            if (candidate == null) return null;
            if (resolved == null) resolved = candidate;
            else if (!resolved.equals(candidate)) return null;
        }
        return resolved;
    }

    private Decoded decodeProducer(MethodContext context, AbstractInsnNode producer, int depth, Set<AbstractInsnNode> guard) {
        if (producer instanceof LdcInsnNode ldc && ldc.cst instanceof String text) return new Decoded("string", text);
        if (producer instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC && "Ljava/lang/String;".equals(field.desc)) {
            Object value = scalarProducer(context, producer, depth + 1, guard);
            return value instanceof String text ? new Decoded("string", text) : null;
        }
        if (producer instanceof MethodInsnNode call) return decodeWrapper(context, call, depth + 1, guard);
        if (producer instanceof TypeInsnNode cast && cast.getOpcode() == Opcodes.CHECKCAST) {
            Integer index = context.indices().get(producer);
            if (index == null) return null;
            Frame<SourceValue> frame = context.frames()[index];
            return frame == null || frame.getStackSize() == 0 ? null
                    : decodeDefault(context, frame.getStack(frame.getStackSize() - 1), depth + 1, guard);
        }
        return null;
    }

    private Decoded decodeWrapper(MethodContext context, MethodInsnNode call, int depth, Set<AbstractInsnNode> guard) {
        if (call.getOpcode() != Opcodes.INVOKESTATIC || !"valueOf".equals(call.name) || Type.getArgumentTypes(call.desc).length != 1) return null;
        String kind = switch (call.owner) {
            case "java/lang/Byte" -> "byte";
            case "java/lang/Short" -> "short";
            case "java/lang/Integer" -> "int";
            case "java/lang/Float" -> "float";
            default -> null;
        };
        if (kind == null) return null;
        Integer index = context.indices().get(call);
        if (index == null) return null;
        Frame<SourceValue> frame = context.frames()[index];
        if (frame == null || frame.getStackSize() == 0) return null;
        Object raw = scalar(context, frame.getStack(frame.getStackSize() - 1), depth + 1, guard);
        if (!(raw instanceof Number number)) return null;
        Object value = switch (kind) {
            case "byte" -> number.byteValue();
            case "short" -> number.shortValue();
            case "int" -> number.intValue();
            default -> number.floatValue();
        };
        return new Decoded(kind, value);
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
                    diagnostics.add("Unreadable entity DataWatcher class " + entry.getName() + ": " + malformed.getClass().getSimpleName());
                }
            }
        }
    }
}
