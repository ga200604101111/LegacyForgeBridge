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
 * Non-executing proof/inventory for registered legacy Entity construction. The first slice proves
 * constant legacy setSize(width,height) only for an exact source-owned constructor chain that
 * reaches vanilla Entity, has simple control flow, and does not override setSize in source code.
 */
public final class LegacyEntityConstructionAnalyzer {
    private static final String WORLD_DESC = "Lnet/minecraft/world/World;";
    private static final String WORLD_CONSTRUCTOR = "(" + WORLD_DESC + ")V";
    private static final String VANILLA_ENTITY = "net/minecraft/entity/Entity";
    private static final Set<String> SET_SIZE_NAMES = Set.of("setSize", "func_70105_a");
    private static final String SET_SIZE_DESC = "(FF)V";

    public record ConstructorSite(String owner, String descriptor, boolean simpleControlFlow) { }
    public record Effect(String owner, String constructorDescriptor, int instructionIndex, String kind,
                         String targetOwner, String member, String descriptor) { }
    public record Rule(String registryName, String sourceClass, String externalBaseClass,
                       boolean worldConstructorPresent, List<ConstructorSite> constructorChain,
                       boolean constructorChainComplete, boolean constructorControlFlowSimple,
                       boolean sourceSetSizeOverridePresent, boolean sizeProofComplete,
                       Float width, Float height, String sizeProofReason, List<Effect> effects) {
        public Rule {
            constructorChain = List.copyOf(constructorChain);
            effects = List.copyOf(effects);
        }
    }
    public record Analysis(List<Rule> rules, List<String> diagnostics) {
        public Analysis {
            rules = List.copyOf(rules);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private record MethodKey(String owner, String descriptor) { }
    private record MethodContext(ClassNode owner, MethodNode method, Frame<SourceValue>[] frames) { }
    private record ChainResult(List<ConstructorSite> chain, List<Effect> effects, List<float[]> sizes,
                               boolean complete, boolean simple, String error) { }

    private final Map<String,ClassNode> classes = new LinkedHashMap<>();
    private final Map<MethodKey,MethodContext> contexts = new HashMap<>();
    private final List<String> diagnostics = new ArrayList<>();

    public Analysis analyze(Path jarPath) throws IOException {
        classes.clear();
        contexts.clear();
        diagnostics.clear();
        load(jarPath);

        LegacyEntityDataWatcherAnalyzer.Analysis definitions = new LegacyEntityDataWatcherAnalyzer().analyze(jarPath);
        diagnostics.addAll(definitions.diagnostics());
        List<Rule> rules = new ArrayList<>();
        for (LegacyEntityDataWatcherAnalyzer.Rule definition : definitions.rules()) {
            String sourceClass = definition.sourceClass();
            String externalBase = externalBase(sourceClass);
            boolean setSizeOverride = sourceSetSizeOverride(sourceClass);
            ClassNode concrete = classes.get(sourceClass);
            MethodNode root = findConstructor(concrete, WORLD_CONSTRUCTOR);
            if (root == null) {
                rules.add(new Rule(definition.registryName(), sourceClass, externalBase, false,
                        List.of(), false, false, setSizeOverride, false, null, null,
                        "Registered source entity has no proven (World)V constructor.", List.of()));
                continue;
            }

            ChainResult chain = collectChain(new MethodKey(sourceClass, WORLD_CONSTRUCTOR), new LinkedHashSet<>());
            boolean sizeProof = false;
            Float width = null;
            Float height = null;
            String reason;
            if (chain.error() != null) reason = chain.error();
            else if (!chain.complete()) reason = "Source constructor chain did not resolve exactly to an external superclass constructor.";
            else if (!chain.simple()) reason = "Source constructor chain contains branching/switch/exception control flow.";
            else if (!VANILLA_ENTITY.equals(externalBase))
                reason = "First external superclass is " + String.valueOf(externalBase) + ", not vanilla Entity; inherited construction semantics are not proven yet.";
            else if (setSizeOverride)
                reason = "A source-owned class overrides setSize/func_70105_a; vanilla size-call semantics are not proven.";
            else if (chain.sizes().isEmpty())
                reason = "No proven constant setSize/func_70105_a call exists in the source constructor chain.";
            else {
                float[] last = chain.sizes().getLast();
                if (!(Float.isFinite(last[0]) && Float.isFinite(last[1]) && last[0] > 0.0F && last[1] > 0.0F))
                    reason = "Proven setSize dimensions are non-finite or non-positive.";
                else {
                    sizeProof = true;
                    width = last[0];
                    height = last[1];
                    reason = null;
                }
            }
            rules.add(new Rule(definition.registryName(), sourceClass, externalBase, true,
                    chain.chain(), chain.complete(), chain.simple(), setSizeOverride,
                    sizeProof, width, height, reason, chain.effects()));
        }
        return new Analysis(rules, diagnostics);
    }

    private ChainResult collectChain(MethodKey key, Set<MethodKey> visiting) {
        if (!visiting.add(key))
            return new ChainResult(List.of(), List.of(), List.of(), false, false,
                    "Recursive source constructor delegation is unsupported: " + key.owner() + key.descriptor() + ".");
        ClassNode owner = classes.get(key.owner());
        MethodNode method = findConstructor(owner, key.descriptor());
        if (owner == null || method == null) {
            visiting.remove(key);
            return new ChainResult(List.of(), List.of(), List.of(), false, false,
                    "Missing source constructor " + key.owner() + key.descriptor() + ".");
        }

        MethodContext context;
        try { context = context(owner, method); }
        catch (AnalyzerException error) {
            visiting.remove(key);
            return new ChainResult(List.of(), List.of(), List.of(), false, false,
                    "Could not prove constructor dataflow in " + owner.name + method.desc + ": " + error.getMessage());
        }

        boolean simple = simpleControlFlow(method);
        List<ConstructorSite> localChain = new ArrayList<>();
        localChain.add(new ConstructorSite(owner.name, method.desc, simple));
        List<Effect> localEffects = new ArrayList<>();
        List<float[]> localSizes = new ArrayList<>();
        MethodKey delegatedSourceConstructor = null;
        boolean externalSuperReached = false;
        String error = null;

        for (int i = 0; i < method.instructions.size(); i++) {
            AbstractInsnNode instruction = method.instructions.get(i);
            if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTFIELD) {
                if (putFieldReceiverIsThis(context, i))
                    localEffects.add(new Effect(owner.name, method.desc, i, "this-field-write",
                            field.owner, field.name, field.desc));
                continue;
            }
            if (!(instruction instanceof MethodInsnNode call)) continue;

            if (call.getOpcode() == Opcodes.INVOKESPECIAL && "<init>".equals(call.name)
                    && constructorReceiverIsThis(context, i, call.desc)) {
                if (classes.containsKey(call.owner)) {
                    if (delegatedSourceConstructor != null) {
                        error = "Multiple source constructor delegation targets in " + owner.name + method.desc + ".";
                        break;
                    }
                    delegatedSourceConstructor = new MethodKey(call.owner, call.desc);
                    localEffects.add(new Effect(owner.name, method.desc, i, "source-constructor-delegation",
                            call.owner, call.name, call.desc));
                } else if (call.owner.equals(owner.superName)) {
                    externalSuperReached = true;
                    localEffects.add(new Effect(owner.name, method.desc, i, "external-super-constructor",
                            call.owner, call.name, call.desc));
                }
                continue;
            }

            if (SET_SIZE_NAMES.contains(call.name) && SET_SIZE_DESC.equals(call.desc)) {
                if (!isThisReceiver(context, i, 2)) {
                    error = "setSize receiver is not proven as this in " + owner.name + method.desc + ".";
                    break;
                }
                Float width = floatArgument(context, i, 2, 0);
                Float height = floatArgument(context, i, 2, 1);
                localEffects.add(new Effect(owner.name, method.desc, i,
                        width != null && height != null ? "set-size" : "unproven-set-size",
                        call.owner, call.name, call.desc));
                if (width == null || height == null) {
                    error = "Dynamic/unproven setSize dimensions in " + owner.name + method.desc + ".";
                    break;
                }
                localSizes.add(new float[]{width, height});
                continue;
            }

            if (!"<init>".equals(call.name))
                localEffects.add(new Effect(owner.name, method.desc, i, "method-call",
                        call.owner, call.name, call.desc));
        }

        if (error != null) {
            visiting.remove(key);
            return new ChainResult(localChain, localEffects, localSizes, false, simple, error);
        }

        List<ConstructorSite> chain = new ArrayList<>(localChain);
        List<Effect> effects = new ArrayList<>();
        List<float[]> sizes = new ArrayList<>();
        boolean complete = externalSuperReached;
        if (delegatedSourceConstructor != null) {
            ChainResult parent = collectChain(delegatedSourceConstructor, visiting);
            chain.addAll(parent.chain());
            effects.addAll(parent.effects());
            sizes.addAll(parent.sizes());
            complete = parent.complete();
            simple &= parent.simple();
            if (parent.error() != null) error = parent.error();
        }
        // Constructor delegation executes before the remainder of the current constructor, so
        // inherited size/effects precede local ones. This makes the last size the effective value.
        effects.addAll(localEffects);
        sizes.addAll(localSizes);
        visiting.remove(key);
        return new ChainResult(chain, effects, sizes, complete, simple, error);
    }

    private boolean putFieldReceiverIsThis(MethodContext context, int instructionIndex) {
        Frame<SourceValue> frame = context.frames()[instructionIndex];
        if (frame == null || frame.getStackSize() < 2) return false;
        return isThis(frame.getStack(frame.getStackSize() - 2));
    }

    private boolean constructorReceiverIsThis(MethodContext context, int instructionIndex, String descriptor) {
        int args = Type.getArgumentTypes(descriptor).length;
        return isThisReceiver(context, instructionIndex, args);
    }

    private boolean isThisReceiver(MethodContext context, int instructionIndex, int argumentCount) {
        Frame<SourceValue> frame = context.frames()[instructionIndex];
        if (frame == null || frame.getStackSize() < argumentCount + 1) return false;
        return isThis(frame.getStack(frame.getStackSize() - argumentCount - 1));
    }

    private Float floatArgument(MethodContext context, int instructionIndex, int argumentCount, int argument) {
        Frame<SourceValue> frame = context.frames()[instructionIndex];
        if (frame == null || frame.getStackSize() < argumentCount + 1) return null;
        int start = frame.getStackSize() - argumentCount;
        return scalarFloat(frame.getStack(start + argument), 0, new HashSet<>());
    }

    private Float scalarFloat(SourceValue value, int depth, Set<AbstractInsnNode> guard) {
        if (value == null || depth > 24 || value.insns == null || value.insns.isEmpty()) return null;
        Float result = null;
        for (AbstractInsnNode producer : value.insns) {
            if (!guard.add(producer)) return null;
            Float candidate = scalarFloatProducer(producer);
            guard.remove(producer);
            if (candidate == null) return null;
            if (result == null) result = candidate;
            else if (Float.compare(result, candidate) != 0) return null;
        }
        return result;
    }

    private Float scalarFloatProducer(AbstractInsnNode producer) {
        if (producer instanceof LdcInsnNode ldc && ldc.cst instanceof Float value) return value;
        if (producer instanceof InsnNode insn) {
            if (insn.getOpcode() == Opcodes.FCONST_0) return 0.0F;
            if (insn.getOpcode() == Opcodes.FCONST_1) return 1.0F;
            if (insn.getOpcode() == Opcodes.FCONST_2) return 2.0F;
        }
        if (producer instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC && "F".equals(field.desc)) {
            ClassNode owner = classes.get(field.owner);
            if (owner == null) return null;
            for (FieldNode source : owner.fields)
                if (source.name.equals(field.name) && source.desc.equals(field.desc) && source.value instanceof Float value) return value;
        }
        return null;
    }

    private static boolean isThis(SourceValue value) {
        if (value == null || value.insns == null || value.insns.size() != 1) return false;
        AbstractInsnNode producer = value.insns.iterator().next();
        return producer instanceof VarInsnNode variable && variable.getOpcode() == Opcodes.ALOAD && variable.var == 0;
    }

    private boolean sourceSetSizeOverride(String sourceClass) {
        String current = sourceClass;
        Set<String> seen = new HashSet<>();
        while (current != null && seen.add(current)) {
            ClassNode node = classes.get(current);
            if (node == null) return false;
            for (MethodNode method : node.methods)
                if ((method.access & Opcodes.ACC_STATIC) == 0 && SET_SIZE_NAMES.contains(method.name) && SET_SIZE_DESC.equals(method.desc))
                    return true;
            current = node.superName;
        }
        return false;
    }

    private String externalBase(String sourceClass) {
        String current = sourceClass;
        Set<String> seen = new HashSet<>();
        while (current != null && seen.add(current)) {
            ClassNode node = classes.get(current);
            if (node == null) return current;
            current = node.superName;
        }
        return current;
    }

    private MethodContext context(ClassNode owner, MethodNode method) throws AnalyzerException {
        MethodKey key = new MethodKey(owner.name, method.desc);
        MethodContext cached = contexts.get(key);
        if (cached != null) return cached;
        Analyzer<SourceValue> analyzer = new Analyzer<>(new SourceInterpreter());
        Frame<SourceValue>[] frames = analyzer.analyze(owner.name, method);
        MethodContext result = new MethodContext(owner, method, frames);
        contexts.put(key, result);
        return result;
    }

    private static boolean simpleControlFlow(MethodNode method) {
        if (method.tryCatchBlocks != null && !method.tryCatchBlocks.isEmpty()) return false;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof JumpInsnNode || instruction instanceof LookupSwitchInsnNode
                    || instruction instanceof TableSwitchInsnNode) return false;
        }
        return true;
    }

    private static MethodNode findConstructor(ClassNode owner, String descriptor) {
        if (owner == null) return null;
        for (MethodNode method : owner.methods)
            if ("<init>".equals(method.name) && descriptor.equals(method.desc)) return method;
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
                    diagnostics.add("Unreadable entity construction class " + entry.getName() + ": "
                            + malformed.getClass().getSimpleName());
                }
            }
        }
    }
}
