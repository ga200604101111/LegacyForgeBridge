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
 * Proves that a source ItemSeeds / ItemSeedFood / ItemReed lineage does not replace inherited
 * vanilla placement behavior. The initial bridge is intentionally strict: any source instance
 * method before the vanilla base blocks placement runtime instead of guessing whether it is benign.
 */
public final class LegacyPlantingItemBehaviorAnalyzer {
    private static final String ITEM_SEEDS = "net/minecraft/item/ItemSeeds";
    private static final String ITEM_SEED_FOOD = "net/minecraft/item/ItemSeedFood";
    private static final String ITEM_REED = "net/minecraft/item/ItemReed";

    public record Rule(String registryName, String sourceClass, LegacyItemBlockBindingAnalyzer.Family family,
                       List<String> sourceInstanceMethods, boolean inheritedVanillaPlacement) {
        public Rule { sourceInstanceMethods = List.copyOf(sourceInstanceMethods); }
    }

    public record Analysis(List<Rule> rules, List<String> diagnostics) {
        public Analysis { rules = List.copyOf(rules); diagnostics = List.copyOf(diagnostics); }
    }

    private final Map<String,ClassNode> classes = new LinkedHashMap<>();

    public Analysis analyze(Path jarPath) throws IOException {
        classes.clear();
        load(jarPath);
        var bindings = new LegacyItemBlockBindingAnalyzer().analyze(jarPath);
        List<Rule> rules = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>(bindings.diagnostics());
        for (var binding : bindings.rules()) {
            List<String> methods = sourceInstanceMethods(binding.sourceClass(), binding.family());
            if (methods == null) {
                diagnostics.add("Could not prove vanilla placement lineage for " + binding.registryName()
                        + " (" + binding.sourceClass() + ").");
                continue;
            }
            rules.add(new Rule(binding.registryName(), binding.sourceClass(), binding.family(), methods, methods.isEmpty()));
        }
        return new Analysis(rules, diagnostics);
    }

    private List<String> sourceInstanceMethods(String sourceClass, LegacyItemBlockBindingAnalyzer.Family family) {
        String base = baseClass(family);
        String current = sourceClass;
        Set<String> seen = new LinkedHashSet<>();
        List<String> methods = new ArrayList<>();
        while (current != null && seen.add(current)) {
            if (base.equals(current)) return List.copyOf(methods);
            ClassNode node = classes.get(current);
            if (node == null) return null;
            for (MethodNode method : node.methods) {
                if ("<init>".equals(method.name) || "<clinit>".equals(method.name)) continue;
                if ((method.access & Opcodes.ACC_STATIC) != 0) continue;
                methods.add(node.name + "#" + method.name + method.desc);
            }
            current = node.superName;
        }
        return null;
    }

    private static String baseClass(LegacyItemBlockBindingAnalyzer.Family family) {
        return switch (family) {
            case SEEDS -> ITEM_SEEDS;
            case SEED_FOOD -> ITEM_SEED_FOOD;
            case REED -> ITEM_REED;
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
