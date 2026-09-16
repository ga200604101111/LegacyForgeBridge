package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Composes independently proven 1.7.x block-drop facts into complete normal-drop result plans.
 *
 * <p>Missing source overrides are filled only with the verified vanilla {@code Block} defaults:
 * self BlockItem, quantity 1, damage 0, and {@code quantityDroppedWithBonus} delegating to
 * {@code quantityDropped}. Those defaults are admitted only when the source-owned hierarchy ends
 * directly at {@code net.minecraft.block.Block}. If the first external superclass is a more
 * specialized vanilla/Forge block class, inherited drop behavior is unknown here and the block is
 * kept incomplete rather than guessed.</p>
 *
 * <p>A plan is still not a complete modern harvest runtime. Source-owned overrides around the
 * surrounding Forge harvest/drop path are rejected here, but harvest eligibility, silk-touch
 * stacked-item semantics and explosion drop chance still require separate proof before a plan may
 * drive modern gameplay drops.</p>
 */
public final class LegacyBlockDropPlanCompiler {
    private static final String VANILLA_BLOCK = "net/minecraft/block/Block";
    private static final List<Integer> ZERO_DAMAGE = Collections.nCopies(16, 0);
    private static final List<DropPathSpec> DROP_PATH_SPECS = List.of(
            new DropPathSpec(
                    "getDrops(World,...)",
                    Set.of("getDrops"),
                    "(Lnet/minecraft/world/World;IIIII)Ljava/util/ArrayList;"
            ),
            new DropPathSpec(
                    "dropBlockAsItem",
                    Set.of("dropBlockAsItem", "func_149697_b"),
                    "(Lnet/minecraft/world/World;IIIII)V"
            ),
            new DropPathSpec(
                    "dropBlockAsItemWithChance",
                    Set.of("dropBlockAsItemWithChance", "func_149690_a"),
                    "(Lnet/minecraft/world/World;IIIIFI)V"
            ),
            new DropPathSpec(
                    "harvestBlock",
                    Set.of("harvestBlock", "func_149636_a"),
                    "(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;IIII)V"
            ),
            new DropPathSpec(
                    "canSilkHarvest",
                    Set.of("canSilkHarvest", "func_149700_E"),
                    "()Z"
            ),
            new DropPathSpec(
                    "canSilkHarvest(World,...)",
                    Set.of("canSilkHarvest"),
                    "(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;IIII)Z"
            ),
            new DropPathSpec(
                    "createStackedBlock",
                    Set.of("createStackedBlock", "func_149644_j"),
                    "(I)Lnet/minecraft/item/ItemStack;"
            )
    );

    public enum ItemKind { SELF_BLOCK_ITEM, REGISTERED_ITEM, REGISTERED_BLOCK_ITEM, NONE }

    public record ItemTarget(ItemKind kind, String registryName, String legacyNamespace) {
        public ItemTarget {
            if (kind == ItemKind.NONE) {
                registryName = null;
                legacyNamespace = null;
            } else if (registryName == null || registryName.isBlank()) {
                throw new IllegalArgumentException("Drop item target requires a registry name");
            }
        }

        public static ItemTarget none() { return new ItemTarget(ItemKind.NONE, null, null); }
    }

    public record Plan(
            String registryName,
            String legacyNamespace,
            String implementationClass,
            ItemTarget item,
            int quantity,
            List<Integer> itemDamageByBlockMeta,
            Set<String> platformDefaults
    ) {
        public Plan {
            itemDamageByBlockMeta = List.copyOf(itemDamageByBlockMeta);
            platformDefaults = Collections.unmodifiableSet(new LinkedHashSet<>(platformDefaults));
            if (quantity < 0) throw new IllegalArgumentException("Negative drop quantity");
            if (itemDamageByBlockMeta.size() != 16) throw new IllegalArgumentException("Expected 16 legacy metadata states");
        }
    }

    public record Incomplete(
            String registryName,
            String legacyNamespace,
            String implementationClass,
            List<String> reasons
    ) {
        public Incomplete { reasons = List.copyOf(reasons); }
    }

    public record Analysis(List<Plan> plans, List<Incomplete> incomplete, List<String> diagnostics) {
        public Analysis {
            plans = List.copyOf(plans);
            incomplete = List.copyOf(incomplete);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    public Analysis compile(Path jarPath) throws IOException {
        LegacyRegistryAnalyzer.Analysis registry = new LegacyRegistryAnalyzer().analyze(jarPath);
        LegacyBlockBehaviorAnalyzer.Analysis behavior = new LegacyBlockBehaviorAnalyzer().analyze(jarPath);
        LegacyBlockDropItemCompiler.Analysis itemAnalysis = new LegacyBlockDropItemCompiler().compile(jarPath);
        LegacyBlockDropQuantityCompiler.Analysis quantityAnalysis = new LegacyBlockDropQuantityCompiler().compile(jarPath);
        LegacyBlockDropMetadataCompiler.Analysis damageAnalysis = new LegacyBlockDropMetadataCompiler().compile(jarPath);
        Map<String, ClassNode> classes = loadClasses(jarPath);

        Map<BlockKey, LegacyBlockBehaviorAnalyzer.BlockBehavior> behaviors = new LinkedHashMap<>();
        for (var value : behavior.blocks()) behaviors.put(key(value.legacyNamespace(), value.registryName()), value);
        Map<BlockKey, LegacyBlockDropItemCompiler.Rule> items = new LinkedHashMap<>();
        for (var value : itemAnalysis.rules()) items.put(key(value.legacyNamespace(), value.registryName()), value);
        Map<BlockKey, LegacyBlockDropQuantityCompiler.Rule> quantities = new LinkedHashMap<>();
        for (var value : quantityAnalysis.rules()) quantities.put(key(value.legacyNamespace(), value.registryName()), value);
        Map<BlockKey, LegacyBlockDropMetadataCompiler.Rule> damages = new LinkedHashMap<>();
        for (var value : damageAnalysis.rules()) damages.put(key(value.legacyNamespace(), value.registryName()), value);

        List<Plan> plans = new ArrayList<>();
        List<Incomplete> incomplete = new ArrayList<>();
        for (LegacyRegistryAnalyzer.Registration registration : registry.blocks()) {
            BlockKey key = key(registration.legacyNamespace(), registration.registryName());
            LegacyBlockBehaviorAnalyzer.BlockBehavior blockBehavior = behaviors.get(key);
            EnumSet<LegacyBlockBehaviorAnalyzer.CallbackKind> callbacks = EnumSet.noneOf(LegacyBlockBehaviorAnalyzer.CallbackKind.class);
            if (blockBehavior != null) {
                for (var callback : blockBehavior.callbacks()) callbacks.add(callback.kind());
            }

            List<String> reasons = new ArrayList<>();
            String externalBase = firstExternalSuperclass(classes, registration.implementationClass());
            boolean vanillaBlockDefaults = VANILLA_BLOCK.equals(externalBase);
            if (externalBase == null) {
                reasons.add("source hierarchy could not prove its first external superclass");
            }
            reasons.addAll(sourceOwnedDropPathOverrides(classes, registration.implementationClass()));

            if (callbacks.contains(LegacyBlockBehaviorAnalyzer.CallbackKind.QUANTITY_DROPPED_WITH_BONUS)) {
                reasons.add("source overrides quantityDroppedWithBonus; fortune-dependent quantity is not compiled");
            } else if (!vanillaBlockDefaults) {
                reasons.add("external superclass " + String.valueOf(externalBase)
                        + " may override quantityDroppedWithBonus");
            }

            Set<String> defaults = new LinkedHashSet<>();
            ItemTarget item = null;
            if (callbacks.contains(LegacyBlockBehaviorAnalyzer.CallbackKind.ITEM_DROPPED)) {
                LegacyBlockDropItemCompiler.Rule rule = items.get(key);
                if (rule == null) {
                    reasons.add("source getItemDropped override is outside the admitted direct-return subset");
                } else {
                    item = switch (rule.target().kind()) {
                        case ITEM -> new ItemTarget(ItemKind.REGISTERED_ITEM,
                                rule.target().registryName(), rule.target().legacyNamespace());
                        case BLOCK_ITEM -> new ItemTarget(ItemKind.REGISTERED_BLOCK_ITEM,
                                rule.target().registryName(), rule.target().legacyNamespace());
                        case NONE -> ItemTarget.none();
                    };
                }
            } else if (vanillaBlockDefaults) {
                item = new ItemTarget(ItemKind.SELF_BLOCK_ITEM,
                        registration.registryName(), registration.legacyNamespace());
                defaults.add("item");
            } else {
                reasons.add("inherited getItemDropped semantics are not proven for external superclass "
                        + String.valueOf(externalBase));
            }

            Integer quantity = null;
            if (callbacks.contains(LegacyBlockBehaviorAnalyzer.CallbackKind.QUANTITY_DROPPED)) {
                LegacyBlockDropQuantityCompiler.Rule rule = quantities.get(key);
                if (rule == null) {
                    reasons.add("source quantityDropped override is not a proven non-negative constant");
                } else {
                    quantity = rule.quantity();
                }
            } else if (vanillaBlockDefaults) {
                quantity = 1;
                defaults.add("quantity");
            } else {
                reasons.add("inherited quantityDropped semantics are not proven for external superclass "
                        + String.valueOf(externalBase));
            }

            List<Integer> damage = null;
            if (callbacks.contains(LegacyBlockBehaviorAnalyzer.CallbackKind.DAMAGE_DROPPED)) {
                LegacyBlockDropMetadataCompiler.Rule rule = damages.get(key);
                if (rule == null) {
                    reasons.add("source damageDropped override is outside the admitted pure int subset");
                } else {
                    damage = rule.itemDamageByBlockMeta();
                }
            } else if (vanillaBlockDefaults) {
                damage = ZERO_DAMAGE;
                defaults.add("damage");
            } else {
                reasons.add("inherited damageDropped semantics are not proven for external superclass "
                        + String.valueOf(externalBase));
            }

            if (reasons.isEmpty() && item != null && quantity != null && damage != null) {
                plans.add(new Plan(registration.registryName(), registration.legacyNamespace(),
                        registration.implementationClass(), item, quantity, damage, defaults));
            } else {
                incomplete.add(new Incomplete(registration.registryName(), registration.legacyNamespace(),
                        registration.implementationClass(), List.copyOf(new LinkedHashSet<>(reasons))));
            }
        }

        LinkedHashSet<String> diagnostics = new LinkedHashSet<>();
        diagnostics.addAll(registry.diagnostics());
        diagnostics.addAll(behavior.diagnostics());
        diagnostics.addAll(itemAnalysis.diagnostics());
        diagnostics.addAll(quantityAnalysis.diagnostics());
        diagnostics.addAll(damageAnalysis.diagnostics());
        return new Analysis(plans, incomplete, List.copyOf(diagnostics));
    }

    private static List<String> sourceOwnedDropPathOverrides(
            Map<String, ClassNode> classes,
            String implementationClass
    ) {
        if (implementationClass == null || implementationClass.isBlank()) return List.of();
        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        String current = implementationClass;
        Set<String> visited = new LinkedHashSet<>();
        while (current != null && visited.add(current)) {
            ClassNode node = classes.get(current);
            if (node == null) break;
            for (DropPathSpec spec : DROP_PATH_SPECS) {
                for (MethodNode method : node.methods) {
                    if (!spec.matches(method)) continue;
                    reasons.add("source overrides " + spec.label()
                            + "; surrounding harvest/drop path is not compiled");
                    break;
                }
            }
            current = node.superName;
        }
        return List.copyOf(reasons);
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

    private static BlockKey key(String namespace, String registryName) {
        return new BlockKey(namespace == null ? "" : namespace, registryName);
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
                    // The constituent analyzers own malformed-class diagnostics. An unreadable
                    // hierarchy cannot receive a default-backed normal-drop plan.
                }
            }
        }
        return classes;
    }

    private record DropPathSpec(String label, Set<String> names, String descriptor) {
        boolean matches(MethodNode method) {
            return method != null
                    && (method.access & Opcodes.ACC_STATIC) == 0
                    && names.contains(method.name)
                    && descriptor.equals(method.desc);
        }
    }

    private record BlockKey(String legacyNamespace, String registryName) { }
}
