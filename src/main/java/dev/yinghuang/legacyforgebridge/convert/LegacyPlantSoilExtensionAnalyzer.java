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
 * Finds source-owned Forge 1.7 soil extension hooks on registered blocks.
 *
 * <p>Both hooks are virtual methods added by Forge to Block. Any source override may extend or
 * replace the default soil domain/fertility behavior and therefore must be proven before a legacy
 * plant adapter can claim exact survival or growth semantics.</p>
 */
public final class LegacyPlantSoilExtensionAnalyzer {
    private static final String SUSTAIN_NAME = "canSustainPlant";
    private static final String SUSTAIN_DESC = "(Lnet/minecraft/world/IBlockAccess;IIILnet/minecraftforge/common/util/ForgeDirection;Lnet/minecraftforge/common/IPlantable;)Z";
    private static final String FERTILE_NAME = "isFertile";
    private static final String FERTILE_DESC = "(Lnet/minecraft/world/World;III)Z";

    public record Rule(String registryName, String sourceClass,
                       List<String> canSustainPlantHooks, List<String> fertilityHooks) {
        public Rule {
            canSustainPlantHooks = List.copyOf(canSustainPlantHooks);
            fertilityHooks = List.copyOf(fertilityHooks);
        }
        public boolean inheritsForgeDefaultSustain() { return canSustainPlantHooks.isEmpty(); }
        public boolean inheritsForgeDefaultFertility() { return fertilityHooks.isEmpty(); }
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
            List<String> sustain = new ArrayList<>();
            List<String> fertile = new ArrayList<>();
            inspectSourceLineage(block.implementationClass(), sustain, fertile);
            rules.add(new Rule(block.registryName(), block.implementationClass(), sustain, fertile));
        }
        return new Analysis(rules, registry.diagnostics());
    }

    private void inspectSourceLineage(String sourceClass, List<String> sustain, List<String> fertile) {
        String current = sourceClass;
        Set<String> seen = new HashSet<>();
        while (current != null && seen.add(current)) {
            ClassNode node = classes.get(current);
            if (node == null) break;
            for (MethodNode method : node.methods) {
                if (SUSTAIN_NAME.equals(method.name) && SUSTAIN_DESC.equals(method.desc))
                    sustain.add(current + "#" + method.name + method.desc);
                if (FERTILE_NAME.equals(method.name) && FERTILE_DESC.equals(method.desc))
                    fertile.add(current + "#" + method.name + method.desc);
            }
            current = node.superName;
        }
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
