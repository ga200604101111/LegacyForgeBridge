package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
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
 * Proves the source-owned portion of Minecraft/Forge 1.7.10 explosion block semantics.
 *
 * <p>The legacy explosion loop asks {@code canDropFromExplosion(Explosion)} before routing an
 * affected block through {@code dropBlockAsItemWithChance(..., 1/explosionSize, 0)}. Forge then
 * replaces direct removal with {@code onBlockExploded(...)}. The vanilla/Forge base Block defaults
 * are admitted only when the complete source-owned hierarchy ends directly at {@code Block}; a
 * specialized external superclass is deliberately not guessed.</p>
 *
 * <p>Drop eligibility and destruction callbacks are tracked separately. A custom destruction
 * callback does not retroactively change the already-evaluated drop chance, while a custom
 * {@code canDropFromExplosion} directly changes whether the drop path runs at all.</p>
 */
public final class LegacyBlockExplosionAnalyzer {
    private static final String VANILLA_BLOCK = "net/minecraft/block/Block";
    private static final String EXPLOSION = "Lnet/minecraft/world/Explosion;";
    private static final String WORLD = "Lnet/minecraft/world/World;";

    private static final MethodSpec CAN_DROP = new MethodSpec(
            "canDropFromExplosion",
            Set.of("canDropFromExplosion", "func_149659_a"),
            "(" + EXPLOSION + ")Z"
    );
    private static final List<MethodSpec> DESTRUCTION_CALLBACKS = List.of(
            new MethodSpec(
                    "onBlockExploded",
                    Set.of("onBlockExploded"),
                    "(" + WORLD + "III" + EXPLOSION + ")V"
            ),
            new MethodSpec(
                    "onBlockDestroyedByExplosion",
                    Set.of("onBlockDestroyedByExplosion", "func_149723_a"),
                    "(" + WORLD + "III" + EXPLOSION + ")V"
            )
    );

    public record Proof(
            String registryName,
            String legacyNamespace,
            String implementationClass,
            boolean dropEligibilityProofComplete,
            boolean sourceDestructionOverrideFree,
            List<String> dropEligibilityReasons,
            List<String> destructionReasons
    ) {
        public Proof {
            dropEligibilityReasons = List.copyOf(dropEligibilityReasons);
            destructionReasons = List.copyOf(destructionReasons);
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
            List<String> dropReasons = new ArrayList<>();
            List<String> destructionReasons = new ArrayList<>();
            String externalBase = firstExternalSuperclass(classes, block.implementationClass());
            boolean directBlockDefaults = VANILLA_BLOCK.equals(externalBase);
            if (externalBase == null) {
                dropReasons.add("source hierarchy could not prove its first external superclass");
                destructionReasons.add("source hierarchy could not prove its first external superclass");
            } else if (!directBlockDefaults) {
                dropReasons.add("external superclass " + externalBase
                        + " may override canDropFromExplosion");
                destructionReasons.add("external superclass " + externalBase
                        + " may override explosion destruction callbacks");
            }

            if (block.implementationClass() != null) {
                String current = block.implementationClass();
                Set<String> visited = new LinkedHashSet<>();
                while (current != null && visited.add(current)) {
                    ClassNode node = classes.get(current);
                    if (node == null) break;
                    if (declares(node, CAN_DROP)) {
                        dropReasons.add("source overrides canDropFromExplosion; explosion drop eligibility is not compiled");
                    }
                    for (MethodSpec spec : DESTRUCTION_CALLBACKS) {
                        if (declares(node, spec)) {
                            destructionReasons.add("source overrides " + spec.label()
                                    + "; explosion destruction callback is not compiled");
                        }
                    }
                    current = node.superName;
                }
            }

            dropReasons = new ArrayList<>(new LinkedHashSet<>(dropReasons));
            destructionReasons = new ArrayList<>(new LinkedHashSet<>(destructionReasons));
            proofs.add(new Proof(
                    block.registryName(),
                    block.legacyNamespace(),
                    block.implementationClass(),
                    directBlockDefaults && dropReasons.isEmpty(),
                    directBlockDefaults && destructionReasons.isEmpty(),
                    dropReasons,
                    destructionReasons
            ));
        }

        return new Analysis(proofs, registry.diagnostics());
    }

    private static boolean declares(ClassNode owner, MethodSpec spec) {
        for (MethodNode method : owner.methods) {
            if ((method.access & Opcodes.ACC_STATIC) == 0
                    && spec.names().contains(method.name)
                    && spec.descriptor().equals(method.desc)) return true;
        }
        return false;
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
                    // Registry analysis owns malformed-class diagnostics; missing hierarchy evidence
                    // remains fail-closed through firstExternalSuperclass().
                }
            }
        }
        return classes;
    }

    private record MethodSpec(String label, Set<String> names, String descriptor) { }
}
