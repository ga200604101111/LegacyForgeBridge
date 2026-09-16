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
 * Proves source-owned Minecraft/Forge 1.7.10 Block harvest customization boundaries.
 *
 * <p>The complete tool/player route rejects source overrides of the Block harvest callbacks and
 * source mutations of the per-metadata harvest-tool table. The Material fast-path is narrower:
 * Forge 1.7.10 checks {@code block.getMaterial().isToolNotRequired()} before consulting
 * {@code getHarvestTool}, {@code getHarvestLevel}, the held item or {@code player.canHarvestBlock}.
 * Therefore the fast-path source proof only needs to exclude source overrides that can replace
 * {@code canHarvestBlock} itself or change {@code getMaterial}. This distinction lets later proof
 * stages admit exact no-tool-required Materials without pretending the tool route is compiled.</p>
 */
public final class LegacyBlockHarvestEligibilityAnalyzer {
    private static final String VANILLA_BLOCK = "net/minecraft/block/Block";
    private static final List<MethodSpec> HARVEST_CALLBACKS = List.of(
            new MethodSpec(
                    "canHarvestBlock",
                    Set.of("canHarvestBlock"),
                    "(Lnet/minecraft/entity/player/EntityPlayer;I)Z",
                    true
            ),
            new MethodSpec(
                    "getHarvestTool",
                    Set.of("getHarvestTool"),
                    "(I)Ljava/lang/String;",
                    false
            ),
            new MethodSpec(
                    "getHarvestLevel",
                    Set.of("getHarvestLevel"),
                    "(I)I",
                    false
            ),
            new MethodSpec(
                    "isToolEffective",
                    Set.of("isToolEffective"),
                    "(Ljava/lang/String;I)Z",
                    false
            ),
            new MethodSpec(
                    "getMaterial",
                    Set.of("getMaterial", "func_149688_o"),
                    "()Lnet/minecraft/block/material/Material;",
                    true
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
            List<String> reasons,
            boolean materialFastPathSourceSafe,
            List<String> materialFastPathReasons
    ) {
        public Proof {
            reasons = List.copyOf(reasons);
            materialFastPathReasons = List.copyOf(materialFastPathReasons);
        }
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
            LinkedHashSet<String> fastPathReasons = new LinkedHashSet<>();
            String externalBase = firstExternalSuperclass(classes, block.implementationClass());
            boolean directBlockDefaults = VANILLA_BLOCK.equals(externalBase);
            if (externalBase == null) {
                String reason = "source hierarchy could not prove its first external superclass";
                reasons.add(reason);
                fastPathReasons.add(reason);
            } else if (!directBlockDefaults) {
                String reason = "external superclass " + externalBase
                        + " may customize harvest eligibility";
                reasons.add(reason);
                fastPathReasons.add(reason);
            }

            String current = block.implementationClass();
            Set<String> visited = new LinkedHashSet<>();
            while (current != null && visited.add(current)) {
                ClassNode node = classes.get(current);
                if (node == null) break;
                for (MethodNode method : node.methods) {
                    if ((method.access & Opcodes.ACC_STATIC) == 0) {
                        for (MethodSpec spec : HARVEST_CALLBACKS) {
                            if (!spec.matches(method)) continue;
                            String reason = "source overrides " + spec.label()
                                    + "; harvest eligibility callback is not compiled";
                            reasons.add(reason);
                            if (spec.materialFastPathRelevant()) fastPathReasons.add(reason);
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
                    List.copyOf(reasons),
                    directBlockDefaults && fastPathReasons.isEmpty(),
                    List.copyOf(fastPathReasons)
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

    private record MethodSpec(
            String label,
            Set<String> names,
            String descriptor,
            boolean materialFastPathRelevant
    ) {
        boolean matches(MethodNode method) {
            return names.contains(method.name) && descriptor.equals(method.desc);
        }
    }
}
