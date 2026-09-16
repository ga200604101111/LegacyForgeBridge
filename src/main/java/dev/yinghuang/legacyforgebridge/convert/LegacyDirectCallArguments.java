package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

/**
 * Small fail-closed provenance helper for legacy static calls whose arguments are emitted directly
 * as {@code Class} literal, {@code String} literal, and {@code new SomeType()}.
 *
 * <p>Using the verifier frame at the invocation is materially safer than scanning a fixed number
 * of preceding instructions: consecutive registrations cannot leak arguments into one another.
 */
final class LegacyDirectCallArguments {
    record ClassStringNew(String classInternalName, String stringValue, String newTypeInternalName) { }

    private LegacyDirectCallArguments() { }

    static ClassStringNew classStringNew(ClassNode owner, MethodNode method, MethodInsnNode call) {
        if (owner == null || method == null || call == null || call.getOpcode() != Opcodes.INVOKESTATIC) return null;
        int instructionIndex = method.instructions.indexOf(call);
        if (instructionIndex < 0) return null;

        Frame<SourceValue>[] frames;
        try {
            frames = new Analyzer<>(new SourceInterpreter()).analyze(owner.name, method);
        } catch (AnalyzerException | RuntimeException ignored) {
            return null;
        }
        if (instructionIndex >= frames.length || frames[instructionIndex] == null) return null;
        Frame<SourceValue> frame = frames[instructionIndex];
        if (frame.getStackSize() < 3) return null;
        int base = frame.getStackSize() - 3;

        String className = classLiteral(frame.getStack(base));
        String stringValue = stringLiteral(frame.getStack(base + 1));
        String newType = newType(frame.getStack(base + 2));
        return className == null || stringValue == null || newType == null
                ? null
                : new ClassStringNew(className, stringValue, newType);
    }

    private static String classLiteral(SourceValue value) {
        AbstractInsnNode source = singleSource(value);
        if (!(source instanceof LdcInsnNode ldc) || !(ldc.cst instanceof Type type)
                || type.getSort() != Type.OBJECT) return null;
        return type.getInternalName();
    }

    private static String stringLiteral(SourceValue value) {
        AbstractInsnNode source = singleSource(value);
        return source instanceof LdcInsnNode ldc && ldc.cst instanceof String text ? text : null;
    }

    private static String newType(SourceValue value) {
        AbstractInsnNode source = singleSource(value);
        return source instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW ? type.desc : null;
    }

    private static AbstractInsnNode singleSource(SourceValue value) {
        if (value == null || value.insns == null || value.insns.size() != 1) return null;
        return value.insns.iterator().next();
    }
}
