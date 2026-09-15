package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
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
 * Non-executing proof for the bounded Minecraft 1.7.x ItemFood constructor contract.
 *
 * <p>This first slice deliberately supports only source classes whose active constructor path is
 * straight-line and reaches the vanilla ItemFood (int,float,boolean) or (int,boolean) constructor
 * with constants that can be proven from the registration allocation. Custom consume callbacks,
 * wolf-favorite food and potion-effect food remain fail-closed rather than silently losing legacy
 * semantics.</p>
 */
public final class LegacyFoodItemAnalyzer {
    private static final String ITEM_FOOD = "net/minecraft/item/ItemFood";
    private static final Set<String> ALWAYS_EDIBLE = Set.of("setAlwaysEdible", "func_77848_i");
    private static final Set<String> POTION_EFFECT = Set.of("setPotionEffect", "func_77844_a");
    private static final Set<String> CONSUME_OVERRIDES = Set.of(
            "onEaten", "func_77654_b",
            "onItemRightClick", "func_77659_a",
            "getMaxItemUseDuration", "func_77626_a",
            "getItemUseAction", "func_77661_b",
            "getHealAmount", "func_150905_g",
            "getSaturationModifier", "func_150906_h"
    );

    public record Rule(
            String registryName,
            String sourceClass,
            int nutrition,
            float saturationModifier,
            boolean alwaysEdible
    ) { }

    public record Skipped(String registryName, String sourceClass, String reason) { }

    public record Analysis(List<Rule> rules, List<Skipped> skipped, List<String> diagnostics) {
        public Analysis {
            rules = List.copyOf(rules);
            skipped = List.copyOf(skipped);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private record ConstructorTrace(int nutrition, float saturationModifier, boolean wolfFavorite,
                                    boolean alwaysEdible, boolean potionEffect, String error) {
        static ConstructorTrace error(String message) {
            return new ConstructorTrace(0, 0F, false, false, false, message);
        }
        boolean complete() { return error == null; }
    }

    private record ConstructorCall(String owner, String descriptor, List<Object> arguments) { }

    private final Map<String, ClassNode> classes = new LinkedHashMap<>();

    public Analysis analyze(Path jarPath) throws IOException {
        classes.clear();
        load(jarPath);
        LegacyRegistryAnalyzer.Analysis registry = new LegacyRegistryAnalyzer().analyze(jarPath);
        List<Rule> rules = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();

        for (LegacyRegistryAnalyzer.Registration registration : registry.items()) {
            String sourceClass = registration.implementationClass();
            if (sourceClass == null || !reachesItemFood(sourceClass)) continue;
            ConstructorTrace trace = prove(registration);
            if (!trace.complete()) {
                skipped.add(new Skipped(registration.registryName(), sourceClass, trace.error()));
                continue;
            }
            if (trace.potionEffect()) {
                skipped.add(new Skipped(registration.registryName(), sourceClass,
                        "Legacy ItemFood potion-effect semantics are not yet materialized."));
                continue;
            }
            if (trace.wolfFavorite()) {
                skipped.add(new Skipped(registration.registryName(), sourceClass,
                        "Legacy wolf-favorite food semantics are not yet materialized."));
                continue;
            }
            if (trace.nutrition() < 0 || !Float.isFinite(trace.saturationModifier()) || trace.saturationModifier() < 0F) {
                skipped.add(new Skipped(registration.registryName(), sourceClass,
                        "Legacy ItemFood nutrition/saturation is outside the bounded modern food contract."));
                continue;
            }
            rules.add(new Rule(registration.registryName(), sourceClass, trace.nutrition(),
                    trace.saturationModifier(), trace.alwaysEdible()));
        }
        return new Analysis(rules, skipped, diagnostics);
    }

    private ConstructorTrace prove(LegacyRegistryAnalyzer.Registration registration) {
        List<String> lineage = lineage(registration.implementationClass());
        for (String sourceClass : lineage) {
            ClassNode node = classes.get(sourceClass);
            for (MethodNode method : node.methods) {
                if (!"<init>".equals(method.name) && CONSUME_OVERRIDES.contains(method.name)) {
                    return ConstructorTrace.error("Custom ItemFood consume callback " + sourceClass + "." + method.name
                            + method.desc + " is outside the bounded food adapter.");
                }
            }
        }

        String descriptor = registration.constructorDescriptor() == null ? "()V" : registration.constructorDescriptor();
        List<Object> arguments = new ArrayList<>();
        for (LegacyRegistryAnalyzer.ConstructorArgument argument : registration.constructorArguments()) {
            if (argument.value() == null) {
                return ConstructorTrace.error("Registered ItemFood constructor argument is not a proven constant.");
            }
            arguments.add(argument.value());
        }
        if (Type.getArgumentTypes(descriptor).length != arguments.size()) {
            return ConstructorTrace.error("Registered ItemFood constructor descriptor/argument proof is incomplete.");
        }
        return traceConstructor(registration.implementationClass(), descriptor, arguments, new HashSet<>(), false, false);
    }

    private ConstructorTrace traceConstructor(String owner, String descriptor, List<Object> supplied,
                                              Set<String> visiting, boolean alwaysEdible, boolean potionEffect) {
        String key = owner + descriptor;
        if (!visiting.add(key)) return ConstructorTrace.error("Recursive ItemFood constructor delegation is unsupported.");
        ClassNode node = classes.get(owner);
        if (node == null) return ConstructorTrace.error("Missing source class on ItemFood constructor path: " + owner);
        MethodNode constructor = node.methods.stream()
                .filter(method -> "<init>".equals(method.name) && descriptor.equals(method.desc))
                .findFirst().orElse(null);
        if (constructor == null) return ConstructorTrace.error("Missing source constructor " + owner + descriptor + ".");
        if (!constructor.tryCatchBlocks.isEmpty() || hasBranch(constructor)) {
            return ConstructorTrace.error("Conditional/exceptional ItemFood constructor flow is outside the bounded proof.");
        }

        boolean sawAlways = alwaysEdible;
        boolean sawPotion = potionEffect;
        for (AbstractInsnNode instruction : constructor.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                if (ALWAYS_EDIBLE.contains(call.name)) sawAlways = true;
                if (POTION_EFFECT.contains(call.name)) sawPotion = true;
            }
        }

        ConstructorCall delegation;
        try {
            delegation = constructorDelegation(node, constructor, supplied);
        } catch (AnalyzerException exception) {
            return ConstructorTrace.error("Could not prove ItemFood constructor values: " + exception.getMessage());
        }
        if (delegation == null) return ConstructorTrace.error("No unique this/super constructor delegation was proven.");

        if (ITEM_FOOD.equals(delegation.owner())) {
            if ("(IFZ)V".equals(delegation.descriptor())) {
                if (delegation.arguments().size() != 3) return ConstructorTrace.error("Malformed ItemFood(IFZ) proof.");
                Number nutrition = number(delegation.arguments().get(0));
                Number saturation = number(delegation.arguments().get(1));
                Boolean wolf = bool(delegation.arguments().get(2));
                if (nutrition == null || saturation == null || wolf == null) {
                    return ConstructorTrace.error("ItemFood(IFZ) arguments are not source-proven constants.");
                }
                return new ConstructorTrace(nutrition.intValue(), saturation.floatValue(), wolf,
                        sawAlways, sawPotion, null);
            }
            if ("(IZ)V".equals(delegation.descriptor())) {
                if (delegation.arguments().size() != 2) return ConstructorTrace.error("Malformed ItemFood(IZ) proof.");
                Number nutrition = number(delegation.arguments().get(0));
                Boolean wolf = bool(delegation.arguments().get(1));
                if (nutrition == null || wolf == null) {
                    return ConstructorTrace.error("ItemFood(IZ) arguments are not source-proven constants.");
                }
                return new ConstructorTrace(nutrition.intValue(), 0.6F, wolf,
                        sawAlways, sawPotion, null);
            }
            return ConstructorTrace.error("Unsupported legacy ItemFood constructor " + delegation.descriptor() + ".");
        }
        if (!delegation.owner().equals(owner) && !delegation.owner().equals(node.superName)) {
            return ConstructorTrace.error("Unexpected constructor delegation owner " + delegation.owner() + ".");
        }
        if (!classes.containsKey(delegation.owner())) {
            return ConstructorTrace.error("Constructor path leaves source JAR before reaching ItemFood: "
                    + delegation.owner() + ".");
        }
        return traceConstructor(delegation.owner(), delegation.descriptor(), delegation.arguments(), visiting,
                sawAlways, sawPotion);
    }

    private ConstructorCall constructorDelegation(ClassNode owner, MethodNode method, List<Object> supplied)
            throws AnalyzerException {
        Analyzer<SourceValue> analyzer = new Analyzer<>(new SourceInterpreter());
        Frame<SourceValue>[] frames = analyzer.analyze(owner.name, method);
        Map<Integer, Object> parameters = parameterLocals(method.desc, supplied);
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

    private static Map<Integer, Object> parameterLocals(String descriptor, List<Object> supplied) {
        Type[] arguments = Type.getArgumentTypes(descriptor);
        if (arguments.length != supplied.size()) return Map.of();
        Map<Integer, Object> locals = new HashMap<>();
        int local = 1;
        for (int i = 0; i < arguments.length; i++) {
            locals.put(local, supplied.get(i));
            local += arguments[i].getSize();
        }
        return locals;
    }

    private static Object resolve(SourceValue value, Map<Integer, Object> parameters) {
        if (value == null || value.insns == null || value.insns.size() != 1) return Unresolved.INSTANCE;
        AbstractInsnNode source = value.insns.iterator().next();
        if (source instanceof LdcInsnNode ldc && ldc.cst instanceof Number number) return number;
        if (source instanceof IntInsnNode integer) return integer.operand;
        if (source instanceof VarInsnNode variable) return parameters.getOrDefault(variable.var, Unresolved.INSTANCE);
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

    private static Number number(Object value) {
        return value instanceof Number number ? number : null;
    }

    private static Boolean bool(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number && (number.intValue() == 0 || number.intValue() == 1)) {
            return number.intValue() != 0;
        }
        return null;
    }

    private boolean reachesItemFood(String sourceClass) {
        return !lineage(sourceClass).isEmpty();
    }

    private List<String> lineage(String sourceClass) {
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String current = sourceClass;
        while (current != null && seen.add(current)) {
            ClassNode node = classes.get(current);
            if (node == null) return List.of();
            result.add(current);
            if (ITEM_FOOD.equals(node.superName)) return result;
            if (!classes.containsKey(node.superName)) return List.of();
            current = node.superName;
        }
        return List.of();
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
