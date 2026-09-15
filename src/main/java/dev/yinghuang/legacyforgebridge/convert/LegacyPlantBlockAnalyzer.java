package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Classifies source-proven legacy plant block families without enabling modern growth/placement.
 *
 * <p>This is intentionally a topology gate. A block inheriting vanilla 1.7 BlockCrops/BlockReed/
 * BlockBush is identified, and every source override before that vanilla base is recorded. Later
 * runtime adapters may only claim inherited vanilla lifecycle behavior when this proof says there
 * are no source overrides; custom growth, survival, drops and rendering remain explicit work.</p>
 */
public final class LegacyPlantBlockAnalyzer {
    private static final String CROPS = "net/minecraft/block/BlockCrops";
    private static final String REED = "net/minecraft/block/BlockReed";
    private static final String BUSH = "net/minecraft/block/BlockBush";

    public enum Family { CROPS, REED, BUSH }

    public record Rule(
            String registryName,
            String legacyNamespace,
            String sourceClass,
            Family family,
            boolean inheritedVanillaLifecycleOnly,
            List<String> sourceOverrides
    ) {
        public Rule { sourceOverrides = List.copyOf(sourceOverrides); }
    }

    public record Analysis(List<Rule> rules, List<String> diagnostics) {
        public Analysis { rules = List.copyOf(rules); diagnostics = List.copyOf(diagnostics); }
    }

    private final Map<String,ClassNode> classes = new LinkedHashMap<>();

    public Analysis analyze(Path jarPath) throws IOException {
        classes.clear();
        load(jarPath);
        LegacyRegistryAnalyzer.Analysis registry = new LegacyRegistryAnalyzer().analyze(jarPath);
        List<Rule> rules = new ArrayList<>();
        for (var block : registry.blocks()) {
            Family family = familyOf(block.implementationClass());
            if (family == null) continue;
            List<String> overrides = sourceOverrides(block.implementationClass(), baseClass(family));
            rules.add(new Rule(block.registryName(), block.legacyNamespace(), block.implementationClass(),
                    family, overrides.isEmpty(), overrides));
        }
        return new Analysis(rules, List.of());
    }

    private Family familyOf(String sourceClass) {
        if (sourceClass == null) return null;
        String current = sourceClass;
        Set<String> seen = new HashSet<>();
        while (current != null && seen.add(current)) {
            if (CROPS.equals(current)) return Family.CROPS;
            if (REED.equals(current)) return Family.REED;
            if (BUSH.equals(current)) return Family.BUSH;
            ClassNode node = classes.get(current);
            if (node == null) return null;
            current = node.superName;
        }
        return null;
    }

    private List<String> sourceOverrides(String sourceClass, String baseClass) {
        if (sourceClass == null || sourceClass.equals(baseClass)) return List.of();
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String current = sourceClass;
        while (current != null && seen.add(current) && !baseClass.equals(current)) {
            ClassNode node = classes.get(current);
            if (node == null) break;
            for (MethodNode method : node.methods) {
                if ("<init>".equals(method.name) || "<clinit>".equals(method.name)) continue;
                result.add(current + "#" + method.name + method.desc);
            }
            current = node.superName;
        }
        return List.copyOf(result);
    }

    private static String baseClass(Family family) {
        return switch (family) {
            case CROPS -> CROPS;
            case REED -> REED;
            case BUSH -> BUSH;
        };
    }

    private void load(Path jarPath) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class") || entry.getName().equals("module-info.class")) continue;
                try (InputStream input = jar.getInputStream(entry)) {
                    ClassNode node = new ClassNode();
                    new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    classes.put(node.name, node);
                }
            }
        }
    }
}
