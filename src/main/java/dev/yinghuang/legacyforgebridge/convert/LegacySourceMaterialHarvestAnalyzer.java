package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Version-locked source proof for a direct source-owned Minecraft 1.7.10 Material subclass.
 *
 * <p>MCP 908 Material initializes its private requires-no-tool flag to true; only
 * {@code setRequiresTool()/func_76221_f()} flips it false, while
 * {@code isToolNotRequired()/func_76229_l()} reads that field. This analyzer therefore proves only
 * the narrow inherited-default case: a stable source singleton whose owning class directly extends
 * Material, does not override the getter, and whose input JAR contains no source call to
 * setRequiresTool on any Material-compatible owner. Anything more dynamic remains fail-closed.</p>
 */
public final class LegacySourceMaterialHarvestAnalyzer {
    private static final String MATERIAL = "net/minecraft/block/material/Material";
    private static final String MATERIAL_RETURN_DESCRIPTOR = "()L" + MATERIAL + ";";
    private static final Set<String> TOOL_GETTER_NAMES = Set.of("func_76229_l", "isToolNotRequired");
    private static final Set<String> REQUIRE_TOOL_NAMES = Set.of("func_76221_f", "setRequiresTool");

    public record Proof(
            LegacyBlockMaterialProvenanceAnalyzer.MaterialRef material,
            boolean complete,
            Boolean toolNotRequired,
            List<String> reasons
    ) {
        public Proof { reasons = List.copyOf(reasons); }
    }

    public record Analysis(List<Proof> proofs, List<String> diagnostics) {
        public Analysis {
            proofs = List.copyOf(proofs);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    public Analysis analyze(Path jarPath) throws IOException {
        Map<String, ClassNode> classes = loadClasses(jarPath);
        LegacyBlockMaterialProvenanceAnalyzer.Analysis provenance =
                new LegacyBlockMaterialProvenanceAnalyzer().analyze(jarPath);
        LinkedHashSet<LegacyBlockMaterialProvenanceAnalyzer.MaterialRef> refs = new LinkedHashSet<>();
        for (LegacyBlockMaterialProvenanceAnalyzer.Proof proof : provenance.proofs()) {
            if (proof.complete() && proof.material() != null && !MATERIAL.equals(proof.material().owner())) {
                refs.add(proof.material());
            }
        }

        List<Proof> proofs = new ArrayList<>();
        for (LegacyBlockMaterialProvenanceAnalyzer.MaterialRef ref : refs) {
            proofs.add(prove(classes, ref));
        }
        return new Analysis(proofs, provenance.diagnostics());
    }

    private static Proof prove(
            Map<String, ClassNode> classes,
            LegacyBlockMaterialProvenanceAnalyzer.MaterialRef ref
    ) {
        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        ClassNode material = classes.get(ref.owner());
        String expectedDescriptor = "L" + ref.owner() + ";";
        if (material == null || !expectedDescriptor.equals(ref.descriptor())) {
            reasons.add("source Material singleton owner/type is not exact");
        } else if (!MATERIAL.equals(material.superName)) {
            reasons.add("source Material singleton does not directly extend Minecraft 1.7.10 Material");
        }

        if (material != null) {
            for (MethodNode method : material.methods) {
                if ((method.access & Opcodes.ACC_STATIC) == 0
                        && TOOL_GETTER_NAMES.contains(method.name)
                        && "()Z".equals(method.desc)) {
                    reasons.add("source Material overrides isToolNotRequired");
                }
            }
        }

        for (ClassNode owner : classes.values()) {
            for (MethodNode method : owner.methods) {
                for (AbstractInsnNode instruction : method.instructions) {
                    if (!(instruction instanceof MethodInsnNode call)
                            || !REQUIRE_TOOL_NAMES.contains(call.name)
                            || !MATERIAL_RETURN_DESCRIPTOR.equals(call.desc)) {
                        continue;
                    }
                    if (isMaterialCompatibleOwner(classes, call.owner)) {
                        reasons.add("source invokes Material.setRequiresTool at "
                                + owner.name + "." + method.name + method.desc);
                    }
                }
            }
        }

        boolean complete = reasons.isEmpty();
        return new Proof(ref, complete, complete ? Boolean.TRUE : null, List.copyOf(reasons));
    }

    private static boolean isMaterialCompatibleOwner(Map<String, ClassNode> classes, String owner) {
        String current = owner;
        Set<String> visited = new LinkedHashSet<>();
        while (current != null && visited.add(current)) {
            if (MATERIAL.equals(current)) return true;
            ClassNode node = classes.get(current);
            if (node == null) return false;
            current = node.superName;
        }
        return false;
    }

    private static Map<String, ClassNode> loadClasses(Path jarPath) throws IOException {
        Map<String, ClassNode> classes = new LinkedHashMap<>();
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;
                try (InputStream input = jar.getInputStream(entry)) {
                    ClassNode node = new ClassNode(Opcodes.ASM9);
                    new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    classes.put(node.name, node);
                } catch (RuntimeException ignored) {
                    // Missing source evidence remains fail-closed in the proof above.
                }
            }
        }
        return classes;
    }
}
