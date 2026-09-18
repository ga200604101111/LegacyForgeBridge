package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Removes one exact processor source Block allocation after registerBlock has already been
 * neutralized. The caller must supply a source-proven allocation fingerprint.
 */
public final class LegacyBlockAllocationResidueStripper {
    public record Target(
            String sourceMethod,
            String sourceDescriptor,
            String sourceBlockClass,
            String constructorDescriptor,
            List<LegacySingleInputProcessorBlockAllocationAnalyzer.Effect> effects) {
        public Target {
            effects = List.copyOf(effects);
        }
    }

    public record Result(
            byte[] bytes,
            int strippedSites,
            List<String> blockers) {
        public Result {
            blockers = List.copyOf(blockers);
        }
    }

    private record Match(
            MethodNode method,
            List<AbstractInsnNode> expression,
            TypeInsnNode allocation,
            InsnNode dup,
            MethodInsnNode finalSetter,
            InsnNode discardedBlock) { }

    public Result strip(byte[] sourceClass, Target target) {
        if (!"()V".equals(target.constructorDescriptor())) {
            return new Result(
                    sourceClass, 0,
                    List.of("source-block-allocation-constructor-args-not-supported"));
        }

        ClassNode node = new ClassNode(Opcodes.ASM9);
        try {
            new ClassReader(sourceClass).accept(
                    node,
                    ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (RuntimeException malformed) {
            return new Result(
                    sourceClass, 0,
                    List.of("source-block-allocation-class-unreadable:"
                            + malformed.getClass().getSimpleName()));
        }

        MethodNode method = findMethod(
                node, target.sourceMethod(), target.sourceDescriptor());
        if (method == null) {
            return new Result(
                    sourceClass, 0,
                    List.of("source-block-allocation-owner-method-missing"));
        }

        AnalysisState state;
        try {
            state = analyze(node.name, method);
        } catch (AnalyzerException | RuntimeException invalid) {
            return new Result(
                    sourceClass, 0,
                    List.of("source-block-allocation-staged-dataflow-unavailable:"
                            + invalid.getClass().getSimpleName()));
        }

        List<Match> matches = new ArrayList<>();
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            if (!(instruction instanceof TypeInsnNode allocation)
                    || allocation.getOpcode() != Opcodes.NEW
                    || !target.sourceBlockClass().equals(allocation.desc)) {
                continue;
            }
            Match match = match(
                    method, allocation, target, state);
            if (match != null) matches.add(match);
        }

        if (matches.isEmpty()) {
            return new Result(
                    sourceClass, 0,
                    List.of("no-exact-neutralized-inline-block-allocation-residue"));
        }
        if (matches.size() != 1) {
            return new Result(
                    sourceClass, 0,
                    List.of("ambiguous-neutralized-inline-block-allocation-residues:"
                            + matches.size()));
        }

        Match match = matches.getFirst();
        for (AbstractInsnNode instruction : match.expression()) {
            match.method().instructions.remove(instruction);
        }
        match.method().instructions.remove(match.discardedBlock());

        if (methodReferences(match.method(), target.sourceBlockClass())) {
            return new Result(
                    sourceClass, 0,
                    List.of("source-block-reference-remains-in-allocation-owner-method"));
        }

        byte[] rewritten;
        try {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            rewritten = writer.toByteArray();
            verifyPostRewrite(
                    rewritten,
                    target.sourceMethod(),
                    target.sourceDescriptor(),
                    target.sourceBlockClass());
        } catch (AnalyzerException | RuntimeException invalid) {
            return new Result(
                    sourceClass, 0,
                    List.of("source-block-allocation-post-strip-verification-failed:"
                            + invalid.getClass().getSimpleName()));
        }
        return new Result(rewritten, 1, List.of());
    }

    private static Match match(
            MethodNode method,
            TypeInsnNode allocation,
            Target target,
            AnalysisState state) {
        List<AbstractInsnNode> expression = new ArrayList<>();
        expression.add(allocation);

        AbstractInsnNode cursor = nextMeaningful(allocation);
        if (!(cursor instanceof InsnNode dup)
                || dup.getOpcode() != Opcodes.DUP) {
            return null;
        }
        expression.add(dup);

        cursor = nextMeaningful(cursor);
        if (!(cursor instanceof MethodInsnNode constructor)
                || constructor.getOpcode() != Opcodes.INVOKESPECIAL
                || !"<init>".equals(constructor.name)
                || !target.sourceBlockClass().equals(constructor.owner)
                || !target.constructorDescriptor().equals(constructor.desc)) {
            return null;
        }
        expression.add(constructor);

        MethodInsnNode finalSetter = null;
        for (var effect : target.effects()) {
            AbstractInsnNode argument = nextMeaningful(cursor);
            AbstractInsnNode rawCall = nextMeaningful(argument);
            if (!(rawCall instanceof MethodInsnNode call)
                    || call.getOpcode() != Opcodes.INVOKEVIRTUAL
                    || !effect.owner().equals(call.owner)
                    || !effect.methodName().equals(call.name)
                    || !effect.methodDescriptor().equals(call.desc)
                    || !matchesArgument(argument, effect)) {
                return null;
            }
            expression.add(argument);
            expression.add(call);
            cursor = call;
            finalSetter = call;
        }

        Set<AbstractInsnNode> expressionSet =
                new LinkedHashSet<>(expression);
        List<InsnNode> candidatePops = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (!(instruction instanceof InsnNode pop)
                    || pop.getOpcode() != Opcodes.POP) {
                continue;
            }
            Frame<SourceValue> frame = state.frame(pop);
            if (frame == null || frame.getStackSize() == 0) continue;
            SourceValue value =
                    frame.getStack(frame.getStackSize() - 1);
            if (originatesFromExpression(
                    value,
                    allocation,
                    dup,
                    finalSetter,
                    state,
                    new LinkedHashSet<>())) {
                candidatePops.add(pop);
            }
        }

        if (candidatePops.size() != 1) return null;
        InsnNode discardedBlock = candidatePops.getFirst();
        if (expressionSet.contains(discardedBlock)) return null;
        return new Match(
                method,
                List.copyOf(expression),
                allocation,
                dup,
                finalSetter,
                discardedBlock);
    }

    private static boolean originatesFromExpression(
            SourceValue value,
            TypeInsnNode allocation,
            InsnNode dup,
            MethodInsnNode finalSetter,
            AnalysisState state,
            Set<AbstractInsnNode> guard) {
        if (value == null || value.insns == null || value.insns.isEmpty()) {
            return false;
        }

        boolean saw = false;
        for (AbstractInsnNode producer : value.insns) {
            if (!guard.add(producer)) return false;
            boolean match;
            try {
                if (finalSetter != null) {
                    match = producer == finalSetter;
                } else if (producer == allocation || producer == dup) {
                    match = true;
                } else if (producer instanceof InsnNode insn
                        && insn.getOpcode() == Opcodes.DUP) {
                    Frame<SourceValue> frame = state.frame(producer);
                    match = frame != null
                            && frame.getStackSize() > 0
                            && originatesFromExpression(
                            frame.getStack(frame.getStackSize() - 1),
                            allocation, dup, finalSetter, state, guard);
                } else if (producer instanceof TypeInsnNode cast
                        && cast.getOpcode() == Opcodes.CHECKCAST) {
                    Frame<SourceValue> frame = state.frame(producer);
                    match = frame != null
                            && frame.getStackSize() > 0
                            && originatesFromExpression(
                            frame.getStack(frame.getStackSize() - 1),
                            allocation, dup, finalSetter, state, guard);
                } else {
                    match = false;
                }
            } finally {
                guard.remove(producer);
            }
            if (!match) return false;
            saw = true;
        }
        return saw;
    }

    private static boolean matchesArgument(
            AbstractInsnNode argument,
            LegacySingleInputProcessorBlockAllocationAnalyzer.Effect effect) {
        return switch (effect.kind()) {
            case HARDNESS, RESISTANCE, LIGHT_LEVEL -> {
                Float actual = floatConstant(argument);
                yield actual != null
                        && effect.floatValue() != null
                        && Float.compare(actual, effect.floatValue()) == 0;
            }
            case SOUND -> argument instanceof FieldInsnNode field
                    && field.getOpcode() == Opcodes.GETSTATIC
                    && effect.textValue() != null
                    && effect.textValue().equals(sound(field));
        };
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
        if (!"net/minecraft/block/Block".equals(field.owner)
                || !"Lnet/minecraft/block/Block$SoundType;".equals(field.desc)) {
            return null;
        }
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

    private static boolean methodReferences(
            MethodNode method, String internalName) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof TypeInsnNode type
                    && internalName.equals(type.desc)) {
                return true;
            }
            if (instruction instanceof MethodInsnNode call
                    && (internalName.equals(call.owner)
                    || descriptorReferences(call.desc, internalName))) {
                return true;
            }
            if (instruction instanceof FieldInsnNode field
                    && (internalName.equals(field.owner)
                    || descriptorReferences(field.desc, internalName))) {
                return true;
            }
            if (instruction instanceof LdcInsnNode ldc
                    && ldc.cst instanceof Type type
                    && type.getSort() == Type.OBJECT
                    && internalName.equals(type.getInternalName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean descriptorReferences(
            String descriptor, String internalName) {
        return descriptor != null
                && descriptor.contains("L" + internalName + ";");
    }

    private static void verifyPostRewrite(
            byte[] bytes,
            String methodName,
            String methodDescriptor,
            String sourceBlockClass) throws AnalyzerException {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(
                node,
                ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        MethodNode method =
                findMethod(node, methodName, methodDescriptor);
        if (method == null) {
            throw new IllegalStateException("post-strip method missing");
        }
        Analyzer<SourceValue> analyzer =
                new Analyzer<>(new SourceInterpreter());
        analyzer.analyze(node.name, method);
        if (methodReferences(method, sourceBlockClass)) {
            throw new IllegalStateException(
                    "post-strip source block reference remains");
        }
    }

    private record AnalysisState(
            Frame<SourceValue>[] frames,
            Map<AbstractInsnNode, Integer> indices) {
        Frame<SourceValue> frame(AbstractInsnNode instruction) {
            Integer index = indices.get(instruction);
            if (index == null || index < 0 || index >= frames.length) {
                return null;
            }
            return frames[index];
        }
    }

    private static AnalysisState analyze(
            String owner, MethodNode method) throws AnalyzerException {
        Analyzer<SourceValue> analyzer =
                new Analyzer<>(new SourceInterpreter());
        Frame<SourceValue>[] frames = analyzer.analyze(owner, method);
        Map<AbstractInsnNode, Integer> indices = new HashMap<>();
        for (int index = 0; index < method.instructions.size(); index++) {
            indices.put(method.instructions.get(index), index);
        }
        return new AnalysisState(frames, indices);
    }

    private static MethodNode findMethod(
            ClassNode owner, String name, String descriptor) {
        if (owner == null) return null;
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
}
