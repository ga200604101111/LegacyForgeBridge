package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Proves one inline source Block allocation feeding the uniquely admitted GameRegistry.registerBlock
 * call for a runtime-complete single-input processor.
 *
 * <p>This proof deliberately excludes local-variable/static-field allocations and source helper
 * return values. Those shapes require a separate provenance proof. The admitted inline expression
 * is NEW/DUP/no-arg constructor followed by zero or more source-safe constant Block property
 * setters.</p>
 */
public final class LegacySingleInputProcessorBlockAllocationAnalyzer {
    private static final String GAME_REGISTRY =
            "cpw/mods/fml/common/registry/GameRegistry";
    private static final String BLOCK = "net/minecraft/block/Block";
    private static final String BLOCK_CONTAINER =
            "net/minecraft/block/BlockContainer";
    private static final String SOUND_TYPE =
            "net/minecraft/block/Block$SoundType";
    private static final String SOUND_DESC = "L" + SOUND_TYPE + ";";

    public enum EffectKind {
        HARDNESS,
        RESISTANCE,
        LIGHT_LEVEL,
        SOUND
    }

    public record Effect(
            EffectKind kind,
            String owner,
            String methodName,
            String methodDescriptor,
            Float floatValue,
            String textValue) { }

    public record Proof(
            String registryName,
            String sourceBlockClass,
            String sourceOwner,
            String sourceMethod,
            String sourceDescriptor,
            String registerBlockDescriptor,
            String sourceConstructor,
            boolean inlineAllocationProven,
            boolean allocationControlFlowSimple,
            boolean allocationSetterEffectsSupported,
            boolean allocationProofComplete,
            List<Effect> effects,
            List<String> blockers) {
        public Proof {
            effects = List.copyOf(effects);
            blockers = List.copyOf(blockers);
        }
    }

    public record Analysis(List<Proof> proofs, List<String> diagnostics) {
        public Analysis {
            proofs = List.copyOf(proofs);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private record MethodKey(String owner, String name, String descriptor) { }

    private record Trace(
            TypeInsnNode allocation,
            InsnNode dup,
            MethodInsnNode constructor,
            List<EffectStep> effects) { }

    private record EffectStep(
            Effect effect,
            AbstractInsnNode argument,
            MethodInsnNode call) { }

    private final Map<String, ClassNode> classes = new LinkedHashMap<>();
    private final Map<MethodKey, Frame<SourceValue>[]> frames = new HashMap<>();
    private final Map<MethodKey, Map<AbstractInsnNode, Integer>> indices =
            new HashMap<>();
    private final List<String> diagnostics = new ArrayList<>();

    public Analysis analyze(Path sourceJar) throws IOException {
        classes.clear();
        frames.clear();
        indices.clear();
        diagnostics.clear();
        load(sourceJar);
        analyzeFrames();

        LegacySingleInputProcessorAnalyzer.Analysis processors =
                new LegacySingleInputProcessorAnalyzer().analyze(sourceJar);
        LegacyRegistryAnalyzer.Analysis registry =
                new LegacyRegistryAnalyzer().analyze(sourceJar);
        diagnostics.addAll(processors.diagnostics());
        diagnostics.addAll(registry.diagnostics());

        List<Proof> proofs = new ArrayList<>();
        for (var rule : processors.rules()) {
            proofs.add(prove(rule, registry));
        }
        return new Analysis(
                List.copyOf(proofs),
                List.copyOf(new LinkedHashSet<>(diagnostics)));
    }

    public Proof prove(
            Path sourceJar,
            LegacySingleInputProcessorAnalyzer.Rule rule) throws IOException {
        classes.clear();
        frames.clear();
        indices.clear();
        diagnostics.clear();
        load(sourceJar);
        analyzeFrames();
        return prove(rule, new LegacyRegistryAnalyzer().analyze(sourceJar));
    }

    private Proof prove(
            LegacySingleInputProcessorAnalyzer.Rule rule,
            LegacyRegistryAnalyzer.Analysis registry) {
        List<LegacyRegistryAnalyzer.Registration> registrations =
                registry.blocks().stream()
                        .filter(value -> rule.sourceBlockClass().equals(
                                value.implementationClass()))
                        .toList();

        if (registrations.size() != 1) {
            return blocked(
                    rule,
                    registrations.isEmpty()
                            ? "exact-block-registration-proof-missing"
                            : "ambiguous-block-registration-proof:"
                            + registrations.size());
        }

        LegacyRegistryAnalyzer.Registration registration =
                registrations.getFirst();
        String sourceOwner = registration.sourceOwner();
        String sourceMethod = registration.sourceMethod();
        String sourceDescriptor = registration.sourceDescriptor();
        String sourceConstructor = registration.constructorDescriptor();

        List<String> blockers = new ArrayList<>();
        if (sourceOwner == null || sourceMethod == null
                || sourceDescriptor == null) {
            blockers.add("source-allocation-owner-identity-incomplete");
        }
        if (!"()V".equals(sourceConstructor)) {
            blockers.add(sourceConstructor == null
                    ? "source-allocation-inline-constructor-not-proven"
                    : "source-allocation-constructor-args-not-supported:"
                    + sourceConstructor);
        }

        ClassNode owner = sourceOwner == null ? null : classes.get(sourceOwner);
        MethodNode method = findMethod(owner, sourceMethod, sourceDescriptor);
        if (method == null) {
            blockers.add("source-allocation-owner-method-missing");
        }
        if (!blockers.isEmpty()) {
            return proof(
                    registration,
                    false, false, false,
                    null, sourceConstructor, List.of(), blockers);
        }

        List<MethodInsnNode> calls = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && call.getOpcode() == Opcodes.INVOKESTATIC
                    && GAME_REGISTRY.equals(call.owner)
                    && "registerBlock".equals(call.name)
                    && supportedRegisterBlock(call.desc)) {
                calls.add(call);
            }
        }
        if (calls.size() != 1) {
            blockers.add(calls.isEmpty()
                    ? "source-registerBlock-callsite-missing"
                    : "source-registerBlock-callsite-ambiguous:"
                    + calls.size());
            return proof(
                    registration,
                    false, false, false,
                    null, sourceConstructor, List.of(), blockers);
        }

        MethodInsnNode register = calls.getFirst();
        MethodKey key = new MethodKey(owner.name, method.name, method.desc);
        Frame<SourceValue>[] methodFrames = frames.get(key);
        Map<AbstractInsnNode, Integer> methodIndices = indices.get(key);
        Integer callIndex = methodIndices == null ? null : methodIndices.get(register);
        if (methodFrames == null || callIndex == null
                || callIndex < 0 || callIndex >= methodFrames.length) {
            blockers.add("source-registerBlock-dataflow-unavailable");
            return proof(
                    registration,
                    false, false, false,
                    register.desc, sourceConstructor, List.of(), blockers);
        }

        Frame<SourceValue> frame = methodFrames[callIndex];
        Type[] arguments = Type.getArgumentTypes(register.desc);
        if (frame == null || frame.getStackSize() < arguments.length) {
            blockers.add("source-registerBlock-frame-incomplete");
            return proof(
                    registration,
                    false, false, false,
                    register.desc, sourceConstructor, List.of(), blockers);
        }

        int firstArgument = frame.getStackSize() - arguments.length;
        SourceValue blockValue = frame.getStack(firstArgument);
        Trace trace = trace(
                key, method, blockValue, rule.sourceBlockClass(),
                new LinkedHashSet<>(), blockers);

        boolean inline = trace != null;
        boolean simple = inline && contiguous(method, trace);
        if (!simple) blockers.add(
                "source-block-allocation-expression-not-contiguous");
        boolean effectsSupported = inline
                && trace.effects().stream().allMatch(step -> step.effect() != null);
        if (!effectsSupported) {
            blockers.add("source-block-allocation-setter-effect-unsupported");
        }
        if (inline && !rangeControlFlowSafe(method, trace)) {
            blockers.add("source-block-allocation-control-flow-not-simple");
            simple = false;
        }

        List<Effect> effects = inline
                ? trace.effects().stream().map(EffectStep::effect).toList()
                : List.of();
        boolean complete = inline && simple && effectsSupported
                && blockers.isEmpty();

        return proof(
                registration,
                inline,
                simple,
                effectsSupported,
                register.desc,
                sourceConstructor,
                effects,
                blockers);
    }

    private Trace trace(
            MethodKey key,
            MethodNode method,
            SourceValue value,
            String sourceBlockClass,
            Set<AbstractInsnNode> guard,
            List<String> blockers) {
        if (value == null || value.insns == null || value.insns.size() != 1) {
            blockers.add("source-block-allocation-value-merged-or-missing");
            return null;
        }

        AbstractInsnNode producer = value.insns.iterator().next();
        if (!guard.add(producer)) {
            blockers.add("source-block-allocation-value-cycle");
            return null;
        }
        try {
            if (producer instanceof TypeInsnNode allocation
                    && allocation.getOpcode() == Opcodes.NEW
                    && sourceBlockClass.equals(allocation.desc)) {
                AbstractInsnNode dupNode = nextMeaningful(allocation);
                AbstractInsnNode constructorNode = nextMeaningful(dupNode);
                if (!(dupNode instanceof InsnNode dup)
                        || dup.getOpcode() != Opcodes.DUP
                        || !(constructorNode instanceof MethodInsnNode constructor)
                        || constructor.getOpcode() != Opcodes.INVOKESPECIAL
                        || !"<init>".equals(constructor.name)
                        || !sourceBlockClass.equals(constructor.owner)
                        || !"()V".equals(constructor.desc)) {
                    blockers.add("source-block-inline-allocation-shape-not-proven");
                    return null;
                }
                return new Trace(
                        allocation, dup, constructor, new ArrayList<>());
            }

            if (producer instanceof InsnNode insn
                    && insn.getOpcode() == Opcodes.DUP) {
                SourceValue receiver = stackTopBefore(key, producer);
                return trace(
                        key, method, receiver, sourceBlockClass, guard, blockers);
            }

            if (producer instanceof TypeInsnNode cast
                    && cast.getOpcode() == Opcodes.CHECKCAST) {
                SourceValue receiver = stackTopBefore(key, producer);
                return trace(
                        key, method, receiver, sourceBlockClass, guard, blockers);
            }

            if (producer instanceof MethodInsnNode call
                    && call.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                EffectStep step = effectStep(key, call, blockers);
                if (step == null) return null;

                Frame<SourceValue> frame = frame(key, call);
                Type[] args = Type.getArgumentTypes(call.desc);
                if (frame == null || frame.getStackSize() < args.length + 1) {
                    blockers.add("source-block-allocation-setter-frame-incomplete");
                    return null;
                }
                SourceValue receiver = frame.getStack(
                        frame.getStackSize() - args.length - 1);
                Trace base = trace(
                        key, method, receiver, sourceBlockClass, guard, blockers);
                if (base == null) return null;
                List<EffectStep> effects = new ArrayList<>(base.effects());
                effects.add(step);
                return new Trace(
                        base.allocation(),
                        base.dup(),
                        base.constructor(),
                        effects);
            }

            blockers.add("source-block-allocation-not-inline");
            return null;
        } finally {
            guard.remove(producer);
        }
    }

    private EffectStep effectStep(
            MethodKey key,
            MethodInsnNode call,
            List<String> blockers) {
        if (!safeBlockSetterOwner(call)) {
            blockers.add("source-block-allocation-setter-owner-not-safe:"
                    + call.owner + "." + call.name + call.desc);
            return null;
        }

        Type[] args = Type.getArgumentTypes(call.desc);
        if (args.length != 1) {
            blockers.add("source-block-allocation-setter-arity-not-supported:"
                    + call.name + call.desc);
            return null;
        }

        Frame<SourceValue> frame = frame(key, call);
        if (frame == null || frame.getStackSize() < 2) {
            blockers.add("source-block-allocation-setter-frame-incomplete");
            return null;
        }
        SourceValue argumentValue =
                frame.getStack(frame.getStackSize() - 1);
        AbstractInsnNode argument = soleProducer(argumentValue);
        if (argument == null) {
            blockers.add("source-block-allocation-setter-argument-not-constant:"
                    + call.name + call.desc);
            return null;
        }

        if (method(call, "setHardness", "func_149711_c",
                "(F)Lnet/minecraft/block/Block;")) {
            Float value = floatConstant(argument);
            if (value == null || !Float.isFinite(value) || value < 0.0F) {
                blockers.add("source-block-allocation-hardness-not-supported");
                return null;
            }
            return new EffectStep(
                    new Effect(
                            EffectKind.HARDNESS,
                            call.owner, call.name, call.desc,
                            value, null),
                    argument,
                    call);
        }

        if (method(call, "setResistance", "func_149752_b",
                "(F)Lnet/minecraft/block/Block;")) {
            Float value = floatConstant(argument);
            if (value == null || !Float.isFinite(value) || value < 0.0F) {
                blockers.add("source-block-allocation-resistance-not-supported");
                return null;
            }
            return new EffectStep(
                    new Effect(
                            EffectKind.RESISTANCE,
                            call.owner, call.name, call.desc,
                            value, null),
                    argument,
                    call);
        }

        if (method(call, "setLightLevel", "func_149715_a",
                "(F)Lnet/minecraft/block/Block;")) {
            Float value = floatConstant(argument);
            if (value == null || !Float.isFinite(value)
                    || value < 0.0F || value > 1.0F) {
                blockers.add("source-block-allocation-light-level-not-supported");
                return null;
            }
            return new EffectStep(
                    new Effect(
                            EffectKind.LIGHT_LEVEL,
                            call.owner, call.name, call.desc,
                            value, null),
                    argument,
                    call);
        }

        if (method(call,
                "setStepSound", "setSoundType", "func_149672_a",
                "(" + SOUND_DESC + ")Lnet/minecraft/block/Block;")) {
            if (!(argument instanceof FieldInsnNode field)
                    || field.getOpcode() != Opcodes.GETSTATIC
                    || !SOUND_DESC.equals(field.desc)) {
                blockers.add("source-block-allocation-sound-not-static");
                return null;
            }
            String sound = sound(field);
            if (sound == null) {
                blockers.add("source-block-allocation-sound-unsupported:"
                        + field.owner + "." + field.name);
                return null;
            }
            return new EffectStep(
                    new Effect(
                            EffectKind.SOUND,
                            call.owner, call.name, call.desc,
                            null, sound),
                    argument,
                    call);
        }

        blockers.add("source-block-allocation-setter-not-mapped:"
                + call.owner + "." + call.name + call.desc);
        return null;
    }

    private boolean safeBlockSetterOwner(MethodInsnNode call) {
        if (BLOCK.equals(call.owner) || BLOCK_CONTAINER.equals(call.owner)) {
            return true;
        }

        String current = call.owner;
        Set<String> visited = new LinkedHashSet<>();
        while (current != null && visited.add(current)) {
            ClassNode node = classes.get(current);
            if (node == null) return false;
            for (MethodNode method : node.methods) {
                if (call.name.equals(method.name)
                        && call.desc.equals(method.desc)) {
                    return false;
                }
            }
            if (BLOCK.equals(node.superName)
                    || BLOCK_CONTAINER.equals(node.superName)) {
                return true;
            }
            current = node.superName;
        }
        return false;
    }

    private static boolean contiguous(MethodNode method, Trace trace) {
        List<AbstractInsnNode> expected = new ArrayList<>();
        expected.add(trace.allocation());
        expected.add(trace.dup());
        expected.add(trace.constructor());
        for (EffectStep step : trace.effects()) {
            expected.add(step.argument());
            expected.add(step.call());
        }

        AbstractInsnNode cursor = expected.getFirst();
        for (int index = 1; index < expected.size(); index++) {
            cursor = nextMeaningful(cursor);
            if (cursor != expected.get(index)) return false;
        }
        return true;
    }

    private static boolean rangeControlFlowSafe(MethodNode method, Trace trace) {
        AbstractInsnNode last = trace.effects().isEmpty()
                ? trace.constructor()
                : trace.effects().getLast().call();

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

        for (AbstractInsnNode cursor = trace.allocation();
             cursor != null;
             cursor = cursor.getNext()) {
            if (cursor instanceof LabelNode label && targeted.contains(label)) {
                return false;
            }
            if (cursor == last) break;
        }
        return true;
    }

    private SourceValue stackTopBefore(
            MethodKey key, AbstractInsnNode instruction) {
        Frame<SourceValue> frame = frame(key, instruction);
        if (frame == null || frame.getStackSize() == 0) return null;
        return frame.getStack(frame.getStackSize() - 1);
    }

    private Frame<SourceValue> frame(
            MethodKey key, AbstractInsnNode instruction) {
        Frame<SourceValue>[] methodFrames = frames.get(key);
        Map<AbstractInsnNode, Integer> methodIndices = indices.get(key);
        Integer index = methodIndices == null
                ? null : methodIndices.get(instruction);
        if (methodFrames == null || index == null
                || index < 0 || index >= methodFrames.length) {
            return null;
        }
        return methodFrames[index];
    }

    private static AbstractInsnNode soleProducer(SourceValue value) {
        return value != null && value.insns != null
                && value.insns.size() == 1
                ? value.insns.iterator().next()
                : null;
    }

    private static Float floatConstant(AbstractInsnNode instruction) {
        if (instruction instanceof InsnNode insn) {
            return switch (insn.getOpcode()) {
                case Opcodes.FCONST_0 -> 0.0F;
                case Opcodes.FCONST_1 -> 1.0F;
                case Opcodes.FCONST_2 -> 2.0F;
                default -> null;
            };
        }
        if (instruction instanceof LdcInsnNode ldc
                && ldc.cst instanceof Number number) {
            return number.floatValue();
        }
        return null;
    }

    private static String sound(FieldInsnNode field) {
        if (!BLOCK.equals(field.owner)) return null;
        return switch (field.name) {
            case "field_149769_e", "soundTypeStone" -> "STONE";
            case "field_149766_f", "soundTypeWood" -> "WOOD";
            case "field_149767_g", "soundTypeGravel" -> "GRAVEL";
            case "field_149779_h", "soundTypeGrass" -> "GRASS";
            case "field_149777_j", "soundTypeMetal" -> "METAL";
            case "field_149778_k", "soundTypeGlass" -> "GLASS";
            case "field_149775_l", "soundTypeCloth" -> "WOOL";
            case "field_149776_m", "soundTypeSand" -> "SAND";
            case "field_149773_n", "soundTypeSnow" -> "SNOW";
            case "field_149774_o", "soundTypeLadder" -> "LADDER";
            case "field_149788_p", "soundTypeAnvil" -> "ANVIL";
            default -> null;
        };
    }

    private static boolean method(
            MethodInsnNode call,
            String deobfuscated,
            String srg,
            String descriptor) {
        return (deobfuscated.equals(call.name) || srg.equals(call.name))
                && descriptor.equals(call.desc);
    }

    private static boolean method(
            MethodInsnNode call,
            String deobfuscatedA,
            String deobfuscatedB,
            String srg,
            String descriptor) {
        return (deobfuscatedA.equals(call.name)
                || deobfuscatedB.equals(call.name)
                || srg.equals(call.name))
                && descriptor.equals(call.desc);
    }

    private static boolean supportedRegisterBlock(String descriptor) {
        Type method;
        try {
            method = Type.getMethodType(descriptor);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
        Type[] args = method.getArgumentTypes();
        if (args.length == 2) {
            return object(args[0], BLOCK)
                    && object(args[1], "java/lang/String");
        }
        if (args.length == 4) {
            return object(args[0], BLOCK)
                    && object(args[1], "java/lang/Class")
                    && object(args[2], "java/lang/String")
                    && args[3].getSort() == Type.ARRAY
                    && args[3].getElementType().getSort() == Type.OBJECT
                    && "java/lang/Object".equals(
                    args[3].getElementType().getInternalName());
        }
        return false;
    }

    private static boolean object(Type type, String internalName) {
        return type.getSort() == Type.OBJECT
                && internalName.equals(type.getInternalName());
    }

    private static MethodNode findMethod(
            ClassNode owner, String name, String descriptor) {
        if (owner == null || name == null || descriptor == null) return null;
        for (MethodNode method : owner.methods) {
            if (name.equals(method.name) && descriptor.equals(method.desc)) {
                return method;
            }
        }
        return null;
    }

    private static AbstractInsnNode nextMeaningful(AbstractInsnNode node) {
        if (node == null) return null;
        AbstractInsnNode cursor = node.getNext();
        while (cursor instanceof LabelNode
                || cursor instanceof LineNumberNode
                || cursor instanceof FrameNode) {
            cursor = cursor.getNext();
        }
        return cursor;
    }

    private Proof blocked(
            LegacySingleInputProcessorAnalyzer.Rule rule,
            String blocker) {
        return new Proof(
                rule.registryName(),
                rule.sourceBlockClass(),
                null, null, null, null, null,
                false, false, false, false,
                List.of(), List.of(blocker));
    }

    private static Proof proof(
            LegacyRegistryAnalyzer.Registration registration,
            boolean inline,
            boolean simple,
            boolean effectsSupported,
            String registerBlockDescriptor,
            String sourceConstructor,
            List<Effect> effects,
            List<String> blockers) {
        boolean complete = inline && simple
                && effectsSupported && blockers.isEmpty();
        return new Proof(
                registration.registryName(),
                registration.implementationClass(),
                registration.sourceOwner(),
                registration.sourceMethod(),
                registration.sourceDescriptor(),
                registerBlockDescriptor,
                sourceConstructor,
                inline,
                simple,
                effectsSupported,
                complete,
                effects,
                List.copyOf(new LinkedHashSet<>(blockers)));
    }

    private void load(Path jarPath) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()
                        || !entry.getName().endsWith(".class")
                        || entry.getName().equals("module-info.class")) {
                    continue;
                }
                try (InputStream input = jar.getInputStream(entry)) {
                    ClassNode node = new ClassNode(Opcodes.ASM9);
                    new ClassReader(input).accept(
                            node,
                            ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    classes.put(node.name, node);
                } catch (RuntimeException malformed) {
                    diagnostics.add(
                            "Unreadable processor block-allocation class "
                                    + entry.getName() + ": "
                                    + malformed.getClass().getSimpleName());
                }
            }
        }
    }

    private void analyzeFrames() {
        for (ClassNode owner : classes.values()) {
            for (MethodNode method : owner.methods) {
                MethodKey key =
                        new MethodKey(owner.name, method.name, method.desc);
                try {
                    Analyzer<SourceValue> analyzer =
                            new Analyzer<>(new SourceInterpreter());
                    Frame<SourceValue>[] result =
                            analyzer.analyze(owner.name, method);
                    frames.put(key, result);
                    Map<AbstractInsnNode, Integer> map = new HashMap<>();
                    for (int index = 0;
                         index < method.instructions.size();
                         index++) {
                        map.put(method.instructions.get(index), index);
                    }
                    indices.put(key, map);
                } catch (AnalyzerException | RuntimeException unsupported) {
                    diagnostics.add(
                            "Processor block-allocation dataflow unavailable for "
                                    + owner.name + "." + method.name + method.desc
                                    + ": " + unsupported.getMessage());
                }
            }
        }
    }
}
