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
 * Non-executing ItemBlock placement semantic analysis.
 *
 * <p>Minecraft 1.7's default {@code Item#getMetadata(int)} returns zero. A source ItemBlock may
 * override that mapping before {@code Block#onBlockPlaced} runs, and may also override Forge's
 * {@code placeBlockAt} hook. The first P1 placement adapter can preserve pure metadata mappings,
 * but it must not pretend that a custom placeBlockAt implementation is equivalent to vanilla
 * BlockItem placement.</p>
 */
public final class LegacyItemBlockPlacementAnalyzer {
    private static final String ITEM_BLOCK = "net/minecraft/item/ItemBlock";
    private static final String METADATA_DESC = "(I)I";
    private static final String PLACE_BLOCK_AT_DESC =
            "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/EntityPlayer;"
                    + "Lnet/minecraft/world/World;IIIIFFFI)Z";

    public record Behavior(
            String registryName,
            String legacyNamespace,
            String blockImplementationClass,
            String itemBlockClass,
            LegacyPureIntFunctionCompiler.Program metadataProgram,
            String metadataOwner,
            String metadataMethod,
            boolean customPlaceBlockAt,
            String placeBlockAtOwner
    ) { }

    public record Analysis(List<Behavior> behaviors, List<String> diagnostics) {
        public Analysis {
            behaviors = List.copyOf(behaviors);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    public Analysis analyze(Path jarPath) throws IOException {
        LegacyRegistryAnalyzer.Analysis registry = new LegacyRegistryAnalyzer().analyze(jarPath);
        Map<String, ClassNode> classes = loadClasses(jarPath);
        LegacyPureIntFunctionCompiler intCompiler = new LegacyPureIntFunctionCompiler();
        List<Behavior> output = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>(registry.diagnostics());

        for (LegacyRegistryAnalyzer.Registration registration : registry.blocks()) {
            String itemBlock = registration.itemBlockClass();
            if (itemBlock == null || itemBlock.isBlank() || ITEM_BLOCK.equals(itemBlock)) {
                output.add(new Behavior(registration.registryName(), registration.legacyNamespace(),
                        registration.implementationClass(), ITEM_BLOCK,
                        LegacyPureIntFunctionCompiler.Program.constant(0),
                        ITEM_BLOCK, "getMetadata", false, null));
                continue;
            }

            ClassNode sourceItemBlock = classes.get(itemBlock);
            if (sourceItemBlock == null) {
                diagnostics.add("Registered block " + registration.registryName()
                        + " uses an external custom ItemBlock class " + itemBlock
                        + "; placement metadata semantics are not guessed.");
                continue;
            }

            MethodRef metadata = effectiveMethod(classes, itemBlock,
                    Set.of("getMetadata", "func_77647_b"), METADATA_DESC);
            LegacyPureIntFunctionCompiler.Program metadataProgram = LegacyPureIntFunctionCompiler.Program.constant(0);
            String metadataOwner = ITEM_BLOCK;
            String metadataMethod = "getMetadata";
            if (metadata != null) {
                var compiled = intCompiler.compile(metadata.method(), 1);
                if (!compiled.supported()) {
                    diagnostics.add("Unsupported ItemBlock#getMetadata for " + registration.registryName()
                            + " at " + metadata.owner() + "." + metadata.method().name + METADATA_DESC
                            + ": " + compiled.error());
                    continue;
                }
                metadataProgram = compiled.program();
                metadataOwner = metadata.owner();
                metadataMethod = metadata.method().name;
            }

            MethodRef placeBlockAt = effectiveMethod(classes, itemBlock,
                    Set.of("placeBlockAt"), PLACE_BLOCK_AT_DESC);
            output.add(new Behavior(registration.registryName(), registration.legacyNamespace(),
                    registration.implementationClass(), itemBlock, metadataProgram,
                    metadataOwner, metadataMethod, placeBlockAt != null,
                    placeBlockAt == null ? null : placeBlockAt.owner()));
        }

        return new Analysis(output, List.copyOf(new LinkedHashSet<>(diagnostics)));
    }

    private static MethodRef effectiveMethod(
            Map<String, ClassNode> classes,
            String start,
            Set<String> names,
            String descriptor
    ) {
        String owner = start;
        Set<String> visited = new LinkedHashSet<>();
        while (owner != null && visited.add(owner)) {
            ClassNode node = classes.get(owner);
            if (node == null) return null;
            for (MethodNode method : node.methods) {
                if ((method.access & Opcodes.ACC_STATIC) == 0
                        && names.contains(method.name)
                        && descriptor.equals(method.desc)) {
                    return new MethodRef(node.name, method);
                }
            }
            owner = node.superName;
        }
        return null;
    }

    private static Map<String, ClassNode> loadClasses(Path jarPath) throws IOException {
        Map<String, ClassNode> output = new LinkedHashMap<>();
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;
                try (InputStream input = jar.getInputStream(entry)) {
                    ClassNode node = new ClassNode(Opcodes.ASM9);
                    new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    output.put(node.name, node);
                } catch (RuntimeException malformed) {
                    // Registry analysis owns malformed-class diagnostics. Missing source evidence
                    // naturally fails closed above.
                }
            }
        }
        return output;
    }

    private record MethodRef(String owner, MethodNode method) { }
}
