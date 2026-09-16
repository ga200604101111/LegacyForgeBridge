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
 * Proves whether a source-owned Minecraft/Forge 1.7.10 Block hierarchy customizes harvest
 * eligibility before LegacyForgeBridge attempts to reproduce the platform/tool decision.
 *
 * <p>This analyzer deliberately does not claim complete harvest equivalence. Forge 1.7.10
 * {@code Block.canHarvestBlock(player, metadata)} delegates to {@code ForgeHooks.canHarvestBlock},
 * whose answer also depends on material, held-item tool classes/levels and the player fallback.
 * Those platform facts remain a separate runtime proof. This class only establishes that the
 * source mod did not replace the Block-side eligibility callbacks or mutate the per-metadata
 * harvest-tool table through {@code setHarvestLevel}.</p>
 */
public final class LegacyBlockHarvestEligibilityAnalyzer {
    private static final String VANILLA_BLOCK = "net/minecraft/block/Block";
    private static final List<MethodSpec> HARVEST_CALLBACKS = List.of(
            new MethodSpec(
                    "canHarvestBlock",
                    Set.of("canHarvestBlock"),
                    "(Lnet/minecraft/entity/player/EntityPlayer;I)Z"
            ),
            new MethodSpec(
                    "getHarvestTool",
                    Set.of("getHarvestTool"),
                    "(I)Ljava/lang/String;"
            ),
            new MethodSpec(
                    "getHarvestLevel",
                    Set.of("getHarvestLevel"),
                    "(I)I"
            ),
            new MethodSpec(
                    "isToolEffective",
                    Set.of("isToolEffective"),
                    "(Ljava/lang/String;I)Z"
            ),
            new MethodSpec(
                    "getMaterial",
                    Set.of("getMaterial", "func_149688_o"),
                    "()Lnet/minecraft/block/material/Material;"
            )
    );
    private static final Set<String> SET_HARVEST_LEVEL_DESCRIPTORS = Set.of(
            "(Ljava/lang/String;I)V",
            "(Ljava/lang/String;II)V"
    );

    public record Proof(
            String registryName,
            String legacyNamespace,
            String implementationClass,
            boolean sourceCustomizationFree,
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
        LegacyRegistryAnalyzer.Analysis registry = new LegacyRegistryAnalyzer().analyze(jarPath);
        List<Proof> proofs = new ArrayList<>();

        for (LegacyRegistryAnalyzer.Registration block : registry.blocks()) {
            LinkedHashSet<String> reasons = new LinkedHashSet<>();
            String externalBase = firstExternalSuperclass(classes, block.implementationClass());
            boolean directBlockDefaults = VANILLA_BLOCK.equals(externalBase);
            if (externalBase == null) {
                reasons.add("source hierarchy could not prove its first external superclass");
            } else if (!directBlockDefaults) {
                reasons.add("external superclass " + externalBase
                        + " may customize harvest eligibility");
            }

            String current = block.implementationClass();
            Set<String> visited = new LinkedHashSet<>();
            while (current != null && visited.add(current)) {
                ClassNode node = classes.get(current);
                if (node == null) break;
                for (MethodNode method : node.methods) {
                    if ((method.access & Opcodes.ACC_STATIC) == 0) {
                        for (MethodSpec spec : HARVEST_CALLBACKS) {
                            if (spec.matches(method)) {
                                reasons.add("source overrides " + spec.label()
                                        + "; harvest eligibility callback is not compiled");
                            }
                        }
                    }
                    for (AbstractInsnNode instruction : method.instructions) {
                        if (!(instruction instanceof MethodInsnNode call)) continue;
                        if (!"setHarvestLevel".equals(call.name)
                                || !SET_HARVEST_LEVEL_DESCRIPTORS.contains(call.desc)) continue;
                        reasons.add("source invokes setHarvestLevel in " + node.name + "."
                                + method.name + method.desc
                                + "; per-metadata harvest requirements are not compiled");
                    }
                }
                current = node.superName;
            }

            proofs.add(new Proof(
                    block.registryName(),
                    block.legacyNamespace(),
                    block.implementationClass(),
                    directBlockDefaults && reasons.isEmpty(),
                    List.copyOf(reasons)
            ));
        }

        return new Analysis(proofs, registry.diagnostics());
    }

    private static String firstExternalSuperclass(Map<String, ClassNode> classes, String implementationClass) {
        if (implementationClass == null || implementationClass.isBlank()) return null;
        String current = implementationClass;
        Set<String> visited = new LinkedHashSet<>();
        while (current != null && visited.add(current)) {
            ClassNode node = classes.get(current);
            if (node == null) return current.equals(implementationClass) ? null : current;
            current = node.superName;
        }
        return null;
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
                    // Registry analysis owns malformed-class diagnostics. Missing hierarchy evidence
                    // remains fail-closed through firstExternalSuperclass().
                }
            }
        }
        return classes;
    }

    private record MethodSpec(String label, Set<String> names, String descriptor) {
        boolean matches(MethodNode method) {
            return names.contains(method.name) && descriptor.equals(method.desc);
        }
    }
}
