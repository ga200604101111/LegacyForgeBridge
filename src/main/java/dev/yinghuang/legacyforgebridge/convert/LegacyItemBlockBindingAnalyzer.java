package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Proves legacy item-to-block constructor bindings without enabling placement runtime.
 *
 * <p>The first bounded families are ItemSeeds, ItemSeedFood and ItemReed. Constructor flow must be
 * straight-line. Source static block fields are tied back to proven GameRegistry block identities
 * when possible; external static fields are retained as provenance but are never guessed into a
 * modern block id.</p>
 */
public final class LegacyItemBlockBindingAnalyzer {
    private static final String ITEM_SEEDS = "net/minecraft/item/ItemSeeds";
    private static final String ITEM_SEED_FOOD = "net/minecraft/item/ItemSeedFood";
    private static final String ITEM_REED = "net/minecraft/item/ItemReed";

    public enum Family { SEEDS, SEED_FOOD, REED }

    public record BlockReference(
            String registryName,
            String legacyNamespace,
            String implementationClass,
            String sourceFieldOwner,
            String sourceFieldName,
            String sourceFieldDescriptor,
            boolean registered
    ) { }

    public record Rule(
            String registryName,
            String sourceClass,
            Family family,
            BlockReference targetBlock,
            BlockReference soilBlock,
            Integer nutrition,
            Float saturationModifier
    ) { }

    public record Skipped(String registryName, String sourceClass, String reason) { }

    public record Analysis(List<Rule> rules, List<Skipped> skipped, List<String> diagnostics) {
        public Analysis {
            rules = List.copyOf(rules);
            skipped = List.copyOf(skipped);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private record FieldKey(String owner, String name, String descriptor) { }
    private record ConstructorCall(String owner, String descriptor, List<Object> arguments) { }
    private record Trace(BlockReference target, BlockReference soil, Integer nutrition, Float saturation, String error) {
        static Trace error(String message) { return new Trace(null, null, null, null, message); }
        boolean complete() { return error == null; }
    }

    private final Map<String,ClassNode> classes = new LinkedHashMap<>();
    private final Map<FieldKey,BlockReference> registeredFields = new LinkedHashMap<>();
    private final Map<String,LegacyRegistryAnalyzer.Registration> uniqueBlocksByClass = new LinkedHashMap<>();

    public Analysis analyze(Path jarPath) throws IOException {
        classes.clear(); registeredFields.clear(); uniqueBlocksByClass.clear();
        load(jarPath);
        LegacyRegistryAnalyzer.Analysis registry = new LegacyRegistryAnalyzer().analyze(jarPath);
        indexBlocks(registry);

        List<Rule> rules = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        for (LegacyRegistryAnalyzer.Registration registration : registry.items()) {
            Family family = familyOf(registration.implementationClass());
            if (family == null) continue;
            String sourceClass = registration.implementationClass();
            if (sourceClass == null || !classes.containsKey(sourceClass)) {
                skipped.add(new Skipped(registration.registryName(), sourceClass,
                        "Direct vanilla seed/reed allocation is not yet source-constructor proven."));
                continue;
            }
            String descriptor = registration.constructorDescriptor() == null ? "()V" : registration.constructorDescriptor();
            Type[] types = Type.getArgumentTypes(descriptor);
            if (types.length != registration.constructorArguments().size()) {
                skipped.add(new Skipped(registration.registryName(), sourceClass,
                        "Registered constructor descriptor/argument proof is incomplete."));
                continue;
            }
            List<Object> supplied = new ArrayList<>();
            boolean unresolved = false;
            for (LegacyRegistryAnalyzer.ConstructorArgument argument : registration.constructorArguments()) {
                if (argument.value() == null && argument.descriptor().startsWith("L")) unresolved = true;
                supplied.add(argument.value() == null ? Unresolved.INSTANCE : argument.value());
            }
            if (unresolved) {
                skipped.add(new Skipped(registration.registryName(), sourceClass,
                        "Registered constructor contains an unresolved object argument before the source item constructor."));
                continue;
            }
            Trace trace = traceConstructor(sourceClass, descriptor, supplied, family, new HashSet<>());
            if (!trace.complete()) {
                skipped.add(new Skipped(registration.registryName(), sourceClass, trace.error()));
                continue;
            }
            rules.add(new Rule(registration.registryName(), sourceClass, family, trace.target(), trace.soil(),
                    trace.nutrition(), trace.saturation()));
        }
        return new Analysis(rules, skipped, List.of());
    }

    private void indexBlocks(LegacyRegistryAnalyzer.Analysis registry) {
        Map<String,Integer> classCounts = new HashMap<>();
        for (var block : registry.blocks()) if (block.implementationClass() != null) {
            classCounts.merge(block.implementationClass(), 1, Integer::sum);
            uniqueBlocksByClass.put(block.implementationClass(), block);
        }
        classCounts.forEach((type,count) -> { if (count != 1) uniqueBlocksByClass.remove(type); });
        for (var binding : registry.fieldBindings()) {
            if (binding.kind() != LegacyRegistryAnalyzer.Kind.BLOCK) continue;
            registeredFields.put(new FieldKey(binding.owner(), binding.name(), binding.descriptor()),
                    new BlockReference(binding.registryName(), binding.legacyNamespace(), binding.implementationClass(),
                            binding.owner(), binding.name(), binding.descriptor(), true));
        }
    }

    private Trace traceConstructor(String owner, String descriptor, List<Object> supplied, Family family,
                                   Set<String> visiting) {
        String key = owner + descriptor;
        if (!visiting.add(key)) return Trace.error("Recursive item constructor delegation is unsupported.");
        ClassNode node = classes.get(owner);
        if (node == null) return Trace.error("Missing source constructor owner " + owner + ".");
        MethodNode constructor = node.methods.stream()
                .filter(method -> "<init>".equals(method.name) && descriptor.equals(method.desc))
                .findFirst().orElse(null);
        if (constructor == null) return Trace.error("Missing source constructor " + owner + descriptor + ".");
        if (!constructor.tryCatchBlocks.isEmpty() || hasBranch(constructor)) {
            return Trace.error("Conditional/exceptional seed/reed constructor flow is outside the bounded proof.");
        }

        ConstructorCall call;
        try {
            call = constructorDelegation(node, constructor, supplied);
        } catch (AnalyzerException exception) {
            return Trace.error("Could not prove seed/reed constructor values: " + exception.getMessage());
        }
        if (call == null) return Trace.error("No unique this/super constructor delegation was proven.");
        String targetBase = baseClass(family);
        if (targetBase.equals(call.owner())) return materializeBase(family, call);
        if (!call.owner().equals(owner) && !call.owner().equals(node.superName)) {
            return Trace.error("Unexpected constructor delegation owner " + call.owner() + ".");
        }
        if (!classes.containsKey(call.owner())) {
            return Trace.error("Constructor path leaves the source JAR before reaching " + targetBase + ": " + call.owner() + ".");
        }
        return traceConstructor(call.owner(), call.descriptor(), call.arguments(), family, visiting);
    }

    private static Trace materializeBase(Family family, ConstructorCall call) {
        return switch (family) {
            case SEEDS -> {
                if (!"(Lnet/minecraft/block/Block;Lnet/minecraft/block/Block;)V".equals(call.descriptor())
                        || call.arguments().size() != 2
                        || !(call.arguments().get(0) instanceof BlockReference target)
                        || !(call.arguments().get(1) instanceof BlockReference soil)) {
                    yield Trace.error("ItemSeeds target/soil block bindings were not source-proven.");
                }
                yield new Trace(target, soil, null, null, null);
            }
            case SEED_FOOD -> {
                if (!"(IFLnet/minecraft/block/Block;Lnet/minecraft/block/Block;)V".equals(call.descriptor())
                        || call.arguments().size() != 4
                        || !(call.arguments().get(0) instanceof Number nutrition)
                        || !(call.arguments().get(1) instanceof Number saturation)
                        || !(call.arguments().get(2) instanceof BlockReference target)
                        || !(call.arguments().get(3) instanceof BlockReference soil)) {
                    yield Trace.error("ItemSeedFood food/target/soil bindings were not source-proven.");
                }
                yield new Trace(target, soil, nutrition.intValue(), saturation.floatValue(), null);
            }
            case REED -> {
                if (!"(Lnet/minecraft/block/Block;)V".equals(call.descriptor())
                        || call.arguments().size() != 1
                        || !(call.arguments().getFirst() instanceof BlockReference target)) {
                    yield Trace.error("ItemReed target block binding was not source-proven.");
                }
                yield new Trace(target, null, null, null, null);
            }
        };
    }

    private ConstructorCall constructorDelegation(ClassNode owner, MethodNode method, List<Object> supplied)
            throws AnalyzerException {
        Analyzer<SourceValue> analyzer = new Analyzer<>(new SourceInterpreter());
        Frame<SourceValue>[] frames = analyzer.analyze(owner.name, method);
        Map<Integer,Object> parameters = parameterLocals(method.desc, supplied);
        ConstructorCall result = null;
        int index = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESPECIAL
                    && "<init>".equals(call.name)
                    && (owner.name.equals(call.owner) || owner.superName.equals(call.owner))) {
                if (result != null) return null;
                Frame<SourceValue> frame = frames[index];
                if (frame == null) return null;
                Type[] types = Type.getArgumentTypes(call.desc);
                int start = frame.getStackSize() - types.length;
                if (start <= 0) return null;
                List<Object> arguments = new ArrayList<>();
                for (int i = 0; i < types.length; i++) {
                    Object value = resolve(frame.getStack(start + i), parameters);
                    if (value == Unresolved.INSTANCE) return null;
                    arguments.add(value);
                }
                result = new ConstructorCall(call.owner, call.desc, List.copyOf(arguments));
            }
            index++;
        }
        return result;
    }

    private Object resolve(SourceValue value, Map<Integer,Object> parameters) {
        if (value == null || value.insns == null || value.insns.size() != 1) return Unresolved.INSTANCE;
        AbstractInsnNode source = value.insns.iterator().next();
        if (source instanceof LdcInsnNode ldc && ldc.cst instanceof Number number) return number;
        if (source instanceof IntInsnNode integer) return integer.operand;
        if (source instanceof VarInsnNode variable) return parameters.getOrDefault(variable.var, Unresolved.INSTANCE);
        if (source instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC) {
            FieldKey key = new FieldKey(field.owner, field.name, field.desc);
            BlockReference registered = registeredFields.get(key);
            if (registered != null) return registered;
            Type type = Type.getType(field.desc);
            if (type.getSort() == Type.OBJECT) {
                var unique = uniqueBlocksByClass.get(type.getInternalName());
                if (unique != null) return new BlockReference(unique.registryName(), unique.legacyNamespace(),
                        unique.implementationClass(), field.owner, field.name, field.desc, true);
            }
            return new BlockReference(null, null, null, field.owner, field.name, field.desc, false);
        }
        if (source instanceof InsnNode instruction) {
            return switch (instruction.getOpcode()) {
                case Opcodes.ICONST_M1 -> -1;
                case Opcodes.ICONST_0 -> 0;
                case Opcodes.ICONST_1 -> 1;
                case Opcodes.ICONST_2 -> 2;
                case Opcodes.ICONST_3 -> 3;
                case Opcodes.ICONST_4 -> 4;
                case Opcodes.ICONST_5 -> 5;
                case Opcodes.FCONST_0 -> 0F;
                case Opcodes.FCONST_1 -> 1F;
                case Opcodes.FCONST_2 -> 2F;
                default -> Unresolved.INSTANCE;
            };
        }
        return Unresolved.INSTANCE;
    }

    private static Map<Integer,Object> parameterLocals(String descriptor, List<Object> supplied) {
        Type[] types = Type.getArgumentTypes(descriptor);
        if (types.length != supplied.size()) return Map.of();
        Map<Integer,Object> result = new HashMap<>();
        int local = 1;
        for (int i = 0; i < types.length; i++) {
            result.put(local, supplied.get(i));
            local += types[i].getSize();
        }
        return result;
    }

    private Family familyOf(String implementationClass) {
        if (implementationClass == null) return null;
        String current = implementationClass;
        Set<String> seen = new HashSet<>();
        while (current != null && seen.add(current)) {
            if (ITEM_SEEDS.equals(current)) return Family.SEEDS;
            if (ITEM_SEED_FOOD.equals(current)) return Family.SEED_FOOD;
            if (ITEM_REED.equals(current)) return Family.REED;
            ClassNode node = classes.get(current);
            if (node == null) return null;
            current = node.superName;
        }
        return null;
    }

    private static String baseClass(Family family) {
        return switch (family) {
            case SEEDS -> ITEM_SEEDS;
            case SEED_FOOD -> ITEM_SEED_FOOD;
            case REED -> ITEM_REED;
        };
    }

    private static boolean hasBranch(MethodNode method) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof JumpInsnNode || instruction instanceof TableSwitchInsnNode
                    || instruction instanceof LookupSwitchInsnNode) return true;
        }
        return false;
    }

    private void load(Path jarPath) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class") || entry.getName().equals("module-info.class")) continue;
                try (InputStream input = jar.getInputStream(entry)) {
                    ClassNode node = new ClassNode();
                    new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    classes.put(node.name, node);
                }
            }
        }
    }

    private enum Unresolved { INSTANCE }
}
