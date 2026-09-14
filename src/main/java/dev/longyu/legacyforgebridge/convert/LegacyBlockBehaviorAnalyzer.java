package dev.longyu.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Non-executing inventory of source-owned Minecraft 1.7.x Block callbacks for proven
 * {@code GameRegistry.registerBlock} identities.
 *
 * <p>This stage intentionally does not compile behavior. It establishes which effective override
 * actually belongs to the source JAR, walking source-owned superclasses from the registered
 * implementation class outward. Later compilers may admit only callback families they can prove
 * equivalent; unknown methods remain ordinary source evidence and are never guessed.</p>
 */
public final class LegacyBlockBehaviorAnalyzer {
    public enum CallbackKind {
        ACTIVATE,
        PLACED_BY,
        NEIGHBOR_CHANGED,
        UPDATE_TICK,
        RANDOM_DISPLAY_TICK,
        BLOCK_ADDED,
        BREAK_BLOCK,
        ITEM_DROPPED,
        QUANTITY_DROPPED,
        DAMAGE_DROPPED
    }

    public record Callback(CallbackKind kind, String owner, String method, String descriptor) { }

    public record BlockBehavior(String registryName, String legacyNamespace, String implementationClass,
                                List<Callback> callbacks) {
        public BlockBehavior { callbacks = List.copyOf(callbacks); }
    }

    public record Analysis(List<BlockBehavior> blocks, List<String> diagnostics) {
        public Analysis {
            blocks = List.copyOf(blocks);
            diagnostics = List.copyOf(diagnostics);
        }

        public int callbackCount() {
            return blocks.stream().mapToInt(value -> value.callbacks().size()).sum();
        }
    }

    private record Spec(CallbackKind kind, Set<String> names, String descriptor) {
        boolean matches(MethodNode method) {
            return (method.access & Opcodes.ACC_STATIC) == 0
                    && names.contains(method.name)
                    && descriptor.equals(method.desc);
        }
    }

    private static final List<Spec> SPECS = List.of(
            spec(CallbackKind.ACTIVATE,
                    "(Lnet/minecraft/world/World;IIILnet/minecraft/entity/player/EntityPlayer;IFFF)Z",
                    "onBlockActivated", "func_149727_a"),
            spec(CallbackKind.PLACED_BY,
                    "(Lnet/minecraft/world/World;IIILnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;)V",
                    "onBlockPlacedBy", "func_149689_a"),
            spec(CallbackKind.NEIGHBOR_CHANGED,
                    "(Lnet/minecraft/world/World;IIILnet/minecraft/block/Block;)V",
                    "onNeighborBlockChange", "func_149695_a"),
            spec(CallbackKind.UPDATE_TICK,
                    "(Lnet/minecraft/world/World;IIILjava/util/Random;)V",
                    "updateTick", "func_149674_a"),
            spec(CallbackKind.RANDOM_DISPLAY_TICK,
                    "(Lnet/minecraft/world/World;IIILjava/util/Random;)V",
                    "randomDisplayTick", "func_149734_b"),
            spec(CallbackKind.BLOCK_ADDED,
                    "(Lnet/minecraft/world/World;III)V",
                    "onBlockAdded", "func_149726_b"),
            spec(CallbackKind.BREAK_BLOCK,
                    "(Lnet/minecraft/world/World;IIILnet/minecraft/block/Block;I)V",
                    "breakBlock", "func_149749_a"),
            spec(CallbackKind.ITEM_DROPPED,
                    "(ILjava/util/Random;I)Lnet/minecraft/item/Item;",
                    "getItemDropped", "func_149650_a"),
            spec(CallbackKind.QUANTITY_DROPPED,
                    "(Ljava/util/Random;)I",
                    "quantityDropped", "func_149745_a"),
            spec(CallbackKind.DAMAGE_DROPPED,
                    "(I)I",
                    "damageDropped", "func_149692_a")
    );

    public Analysis analyze(Path jarPath) throws IOException {
        Map<String, ClassNode> classes = loadClasses(jarPath);
        LegacyRegistryAnalyzer.Analysis registry = new LegacyRegistryAnalyzer().analyze(jarPath);
        List<String> diagnostics = new ArrayList<>(registry.diagnostics());
        List<BlockBehavior> output = new ArrayList<>();

        for (LegacyRegistryAnalyzer.Registration registration : registry.blocks()) {
            String implementation = registration.implementationClass();
            if (implementation == null || implementation.isBlank()) {
                diagnostics.add("Registered block " + registration.registryName() + " has no proven implementation class.");
                continue;
            }
            EnumMap<CallbackKind, Callback> effective = new EnumMap<>(CallbackKind.class);
            String owner = implementation;
            Set<String> visited = new LinkedHashSet<>();
            while (owner != null && visited.add(owner)) {
                ClassNode node = classes.get(owner);
                if (node == null) break;
                for (Spec spec : SPECS) {
                    if (effective.containsKey(spec.kind())) continue;
                    MethodNode method = find(node, spec);
                    if (method != null) {
                        effective.put(spec.kind(), new Callback(spec.kind(), node.name, method.name, method.desc));
                    }
                }
                owner = node.superName;
            }
            if (!effective.isEmpty()) {
                List<Callback> callbacks = new ArrayList<>();
                for (CallbackKind kind : CallbackKind.values()) {
                    Callback callback = effective.get(kind);
                    if (callback != null) callbacks.add(callback);
                }
                output.add(new BlockBehavior(registration.registryName(), registration.legacyNamespace(),
                        implementation, callbacks));
            }
        }

        return new Analysis(output, List.copyOf(new LinkedHashSet<>(diagnostics)));
    }

    private static MethodNode find(ClassNode owner, Spec spec) {
        for (MethodNode method : owner.methods) if (spec.matches(method)) return method;
        return null;
    }

    private static Spec spec(CallbackKind kind, String descriptor, String... names) {
        return new Spec(kind, Set.of(names), descriptor);
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
                    // Registry analysis owns malformed-class diagnostics. This inventory never
                    // invents callbacks for a class it could not parse.
                }
            }
        }
        return classes;
    }
}
