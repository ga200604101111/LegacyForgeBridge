package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Proves exact constant-return legacy Entity callback bodies without loading source classes. */
public final class LegacyEntityConstantOverrideAnalyzer {
    public record BooleanProof(boolean proven, Boolean value, String reason) { }
    public record FloatProof(boolean proven, Float value, String reason) { }

    public BooleanProof proveBoolean(Path sourceJar, String owner, String method, String descriptor) throws IOException {
        if (owner == null || owner.isBlank() || method == null || method.isBlank()
                || !("()Z".equals(descriptor) || "(D)Z".equals(descriptor)))
            return new BooleanProof(false, null, "unsupported-boolean-callback-identity");
        MethodNode match = find(sourceJar, owner, method, descriptor);
        if (match == null) return new BooleanProof(false, null, "source-callback-method-missing-or-ambiguous");

        List<AbstractInsnNode> opcodes = executable(match);
        if (opcodes.size() != 2 || opcodes.get(1).getOpcode() != Opcodes.IRETURN)
            return new BooleanProof(false, null, "boolean-callback-not-exact-constant-return");
        int opcode = opcodes.get(0).getOpcode();
        if (opcode == Opcodes.ICONST_0) return new BooleanProof(true, false, "exact-iconst-boolean-return");
        if (opcode == Opcodes.ICONST_1) return new BooleanProof(true, true, "exact-iconst-boolean-return");
        return new BooleanProof(false, null, "boolean-callback-not-exact-constant-return");
    }

    public FloatProof proveFloat(Path sourceJar, String owner, String method, String descriptor) throws IOException {
        if (owner == null || owner.isBlank() || method == null || method.isBlank() || !"()F".equals(descriptor))
            return new FloatProof(false, null, "unsupported-float-callback-identity");
        MethodNode match = find(sourceJar, owner, method, descriptor);
        if (match == null) return new FloatProof(false, null, "source-callback-method-missing-or-ambiguous");

        List<AbstractInsnNode> opcodes = executable(match);
        if (opcodes.size() != 2 || opcodes.get(1).getOpcode() != Opcodes.FRETURN)
            return new FloatProof(false, null, "float-callback-not-exact-constant-return");
        AbstractInsnNode constant = opcodes.get(0);
        Float value = switch (constant.getOpcode()) {
            case Opcodes.FCONST_0 -> 0.0F;
            case Opcodes.FCONST_1 -> 1.0F;
            case Opcodes.FCONST_2 -> 2.0F;
            default -> constant instanceof LdcInsnNode ldc && ldc.cst instanceof Float number ? number : null;
        };
        if (value == null || !Float.isFinite(value))
            return new FloatProof(false, null, "float-callback-not-exact-finite-constant-return");
        return new FloatProof(true, value, "exact-float-constant-return");
    }

    private static MethodNode find(Path sourceJar, String owner, String method, String descriptor) throws IOException {
        ClassNode node = load(sourceJar, owner);
        if (node == null) return null;
        MethodNode match = null;
        for (MethodNode candidate : node.methods) {
            if (!candidate.name.equals(method) || !candidate.desc.equals(descriptor)) continue;
            if (match != null) return null;
            match = candidate;
        }
        return match;
    }

    private static List<AbstractInsnNode> executable(MethodNode method) {
        List<AbstractInsnNode> output = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) if (instruction.getOpcode() >= 0) output.add(instruction);
        return output;
    }

    private static ClassNode load(Path sourceJar, String owner) throws IOException {
        try (JarFile jar = new JarFile(sourceJar.toFile())) {
            JarEntry entry = jar.getJarEntry(owner + ".class");
            if (entry == null) return null;
            try (InputStream input = jar.getInputStream(entry)) {
                ClassNode node = new ClassNode(Opcodes.ASM9);
                new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                return node;
            } catch (RuntimeException malformed) {
                return null;
            }
        }
    }
}
