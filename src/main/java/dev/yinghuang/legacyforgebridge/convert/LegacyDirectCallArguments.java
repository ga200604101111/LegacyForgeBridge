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
 * <p>The canonical javac sequence is proven relative to the invocation itself, so consecutive
 * registrations cannot leak arguments into one another. A verifier-source frame is retained as a
 * fallback for equivalent direct bytecode shapes when complete method metadata is available.</p>
 */
final class LegacyDirectCallArguments {
    record ClassStringNew(String classInternalName, String stringValue, String newTypeInternalName) { }

    private LegacyDirectCallArguments() { }

    static ClassStringNew classStringNew(ClassNode owner, MethodNode method, MethodInsnNode call) {
        if (owner == null || method == null || call == null || call.getOpcode() != Opcodes.INVOKESTATIC) return null;

        ClassStringNew canonical = canonicalClassStringNew(call);
        if (canonical != null) return canonical;

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

    /**
     * Proves the exact local push sequence emitted by javac for
     * {@code registerTileEntity(Tile.class, "id", new Renderer())}. Labels, frames and line-number
     * nodes are skipped, but no unrelated executable instruction may appear between the pushes and
     * the target invocation.
     */
    private static ClassStringNew canonicalClassStringNew(MethodInsnNode call) {
        AbstractInsnNode initInsn = previousReal(call);
        if (!(initInsn instanceof MethodInsnNode init)
                || init.getOpcode() != Opcodes.INVOKESPECIAL
                || !"<init>".equals(init.name)
                || !"()V".equals(init.desc)) {
            return null;
        }

        AbstractInsnNode dup = previousReal(initInsn);
        if (dup == null || dup.getOpcode() != Opcodes.DUP) return null;

        AbstractInsnNode newInsn = previousReal(dup);
        if (!(newInsn instanceof TypeInsnNode created)
                || created.getOpcode() != Opcodes.NEW
                || !created.desc.equals(init.owner)) {
            return null;
        }

        AbstractInsnNode stringInsn = previousReal(newInsn);
        if (!(stringInsn instanceof LdcInsnNode stringLdc) || !(stringLdc.cst instanceof String text)) {
            return null;
        }

        AbstractInsnNode classInsn = previousReal(stringInsn);
        if (!(classInsn instanceof LdcInsnNode classLdc)
                || !(classLdc.cst instanceof Type type)
                || type.getSort() != Type.OBJECT) {
            return null;
        }

        return new ClassStringNew(type.getInternalName(), text, created.desc);
    }

    private static AbstractInsnNode previousReal(AbstractInsnNode node) {
        for (AbstractInsnNode current = node == null ? null : node.getPrevious(); current != null;
             current = current.getPrevious()) {
            if (current.getOpcode() >= 0) return current;
        }
        return null;
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
