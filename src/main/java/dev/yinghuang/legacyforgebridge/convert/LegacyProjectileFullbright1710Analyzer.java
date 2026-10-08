package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.jar.JarFile;

/**
 * Exact source method-body evidence for a constant full-bright legacy projectile.
 *
 * <p>Only the object's own getBrightness(F)F == 1.0 and
 * getBrightnessForRender(F)I == 0x00F000F0 are recognized. Dynamic light, inherited
 * overrides, reflection and branch-dependent brightness remain unproved. This is
 * conversion evidence only, not a modern renderer or lightmap hook.</p>
 */
public final class LegacyProjectileFullbright1710Analyzer {
    private static final int LEGACY_FULL_BRIGHT = 0x00F000F0;
    private static final String BRIGHTNESS = "(F)F";
    private static final String LIGHTMAP = "(F)I";

    public record Proof(String sourceClass, float brightness, int packedLight,
                        String brightnessMethod, String lightmapMethod) { }
    public record Analysis(Optional<Proof> proof, List<String> diagnostics) {
        public Analysis {
            proof = Objects.requireNonNull(proof);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    public Analysis analyze(Path jar, String sourceClass) throws IOException {
        Objects.requireNonNull(jar, "jar");
        if (sourceClass == null || sourceClass.isBlank() || sourceClass.contains("..")
                || sourceClass.startsWith("/") || sourceClass.contains("\\"))
            return blocked("Missing/unsafe source entity class");
        try (JarFile input = new JarFile(jar.toFile(), false)) {
            var entry = input.getJarEntry(sourceClass + ".class");
            if (entry == null) return blocked("Source entity class unavailable");
            ClassNode node = new ClassNode(Opcodes.ASM9);
            try (InputStream data = input.getInputStream(entry)) {
                try { new ClassReader(data).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES); }
                catch (RuntimeException damaged) { return blocked("Source brightness class cannot be parsed"); }
            }
            if (!sourceClass.equals(node.name)) return blocked("Source class identity mismatch");
            MethodNode brightness = unique(node, "getBrightness", "func_70013_c", BRIGHTNESS);
            MethodNode light = unique(node, "getBrightnessForRender", "func_70070_b", LIGHTMAP);
            if (brightness == null || light == null)
                return blocked("Unique source-owned brightness and lightmap overrides not proven");
            Float level = staticFloat(brightness);
            Integer packed = staticInt(light);
            if (level == null || Float.compare(level, 1.0F) != 0
                    || packed == null || packed != LEGACY_FULL_BRIGHT)
                return blocked("Full-bright constants are absent, dynamic, or not the pinned 1.7.10 values");
            return new Analysis(Optional.of(new Proof(node.name, level, packed,
                    brightness.name, light.name)), List.of());
        }
    }

    private static MethodNode unique(ClassNode owner, String mcp, String srg, String desc) {
        MethodNode found = null;
        for (MethodNode m : owner.methods) {
            if ((!m.name.equals(mcp) && !m.name.equals(srg)) || !m.desc.equals(desc)) continue;
            if ((m.access & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0 || found != null)
                return null;
            found = m;
        }
        return found;
    }
    private static List<AbstractInsnNode> real(MethodNode m) {
        var out = new ArrayList<AbstractInsnNode>();
        if (m != null) for (var n : m.instructions) if (n.getOpcode() >= 0) out.add(n);
        return out;
    }
    private static Float staticFloat(MethodNode m) {
        if (!m.tryCatchBlocks.isEmpty()) return null;
        var code = real(m);
        if (code.size() != 2 || code.get(1).getOpcode() != Opcodes.FRETURN) return null;
        if (code.get(0).getOpcode() == Opcodes.FCONST_1) return 1.0F;
        if (code.get(0) instanceof LdcInsnNode ldc && ldc.cst instanceof Float f && Float.isFinite(f)) return f;
        return null;
    }
    private static Integer staticInt(MethodNode m) {
        if (!m.tryCatchBlocks.isEmpty()) return null;
        var code = real(m);
        if (code.size() != 2 || code.get(1).getOpcode() != Opcodes.IRETURN) return null;
        if (code.get(0) instanceof LdcInsnNode ldc && ldc.cst instanceof Integer n) return n;
        return null;
    }
    private static Analysis blocked(String reason) {
        return new Analysis(Optional.empty(), List.of(reason));
    }
}
