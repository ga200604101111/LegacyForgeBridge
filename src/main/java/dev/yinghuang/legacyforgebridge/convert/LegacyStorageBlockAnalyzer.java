package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Bounded non-executing analyzer for the first generic legacy BlockContainer/IInventory family.
 *
 * <p>Admission is deliberately strict. The source must prove a 54-slot inventory, ordinary
 * Items/Slot ItemStack NBT, same-block-entity distance validity, vanilla IInventory opening,
 * inventory drops and vanilla comparator calculation. Anything else remains skipped.</p>
 */
public final class LegacyStorageBlockAnalyzer {
    private static final String BLOCK_CONTAINER = "net/minecraft/block/BlockContainer";
    private static final String TILE_ENTITY = "net/minecraft/tileentity/TileEntity";
    private static final String IINVENTORY = "net/minecraft/inventory/IInventory";

    public record Rule(String registryName, String legacyNamespace, String sourceBlockClass,
                       String sourceTileClass, String legacyTileId, int slots, int rows,
                       int stackLimit, String title, double interactionDistanceSq,
                       boolean sneakingPass, boolean dropContents, boolean comparator) { }
    public record Skipped(String registryName, String sourceBlockClass, String reason) { }
    public record Analysis(List<Rule> rules, List<Skipped> skipped, List<String> diagnostics) {
        public Analysis {
            rules = List.copyOf(rules);
            skipped = List.copyOf(skipped);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    public Analysis analyze(Path jarPath) throws IOException {
        Map<String, ClassNode> classes = loadClasses(jarPath);
        LegacyRegistryAnalyzer.Analysis registry = new LegacyRegistryAnalyzer().analyze(jarPath);
        LegacyLifecycleAnalyzer.Analysis lifecycle = new LegacyLifecycleAnalyzer().analyze(jarPath);
        Map<String, String> tileIds = registeredTiles(lifecycle);
        List<Rule> rules = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();
        diagnostics.addAll(registry.diagnostics());
        diagnostics.addAll(lifecycle.diagnostics());

        for (LegacyRegistryAnalyzer.Registration registration : registry.blocks()) {
            String blockClass = registration.implementationClass();
            if (blockClass == null || !inherits(classes, blockClass, BLOCK_CONTAINER)) continue;

            MethodNode create = method(classes, blockClass,
                    Set.of("createNewTileEntity", "func_149915_a"),
                    "(Lnet/minecraft/world/World;I)Lnet/minecraft/tileentity/TileEntity;");
            String tileClass = uniqueCreatedType(create);
            if (tileClass == null || !tileIds.containsKey(tileClass)) {
                skipped.add(skip(registration, blockClass, "created TileEntity is not uniquely proven and lifecycle-registered"));
                continue;
            }
            if (!inherits(classes, tileClass, TILE_ENTITY) || !implementsType(classes, tileClass, IINVENTORY)) {
                skipped.add(skip(registration, blockClass, "registered TileEntity is not a source-proven IInventory"));
                continue;
            }

            Integer slots = returnedInt(method(classes, tileClass,
                    Set.of("getSizeInventory", "func_70302_i_"), "()I"));
            Integer stackLimit = returnedInt(method(classes, tileClass,
                    Set.of("getInventoryStackLimit", "func_70297_j_"), "()I"));
            String title = returnedString(method(classes, tileClass,
                    Set.of("getInventoryName", "func_145825_b"), "()Ljava/lang/String;"));
            Boolean customName = returnedBoolean(method(classes, tileClass,
                    Set.of("hasCustomInventoryName", "func_145818_k_"), "()Z"));
            if (slots == null || slots != 54 || stackLimit == null || stackLimit < 1 || stackLimit > 64
                    || title == null || title.isBlank() || !Boolean.FALSE.equals(customName)) {
                skipped.add(skip(registration, blockClass,
                        "first storage family requires 54 slots, stack limit 1..64, literal title and no custom name"));
                continue;
            }

            MethodNode read = method(classes, tileClass,
                    Set.of("readFromNBT", "func_145839_a"), "(Lnet/minecraft/nbt/NBTTagCompound;)V");
            MethodNode write = method(classes, tileClass,
                    Set.of("writeToNBT", "func_145841_b"), "(Lnet/minecraft/nbt/NBTTagCompound;)V");
            if (!canonicalNbt(read, write)) {
                skipped.add(skip(registration, blockClass, "inventory NBT is not canonical Items/Slot ItemStack encoding"));
                continue;
            }

            MethodNode usable = method(classes, tileClass,
                    Set.of("isUseableByPlayer", "func_70300_a", "canInteractWith"),
                    "(Lnet/minecraft/entity/player/EntityPlayer;)Z");
            Double distanceSq = usableDistanceSq(usable);
            if (distanceSq == null || distanceSq <= 0.0 || distanceSq > 4096.0) {
                skipped.add(skip(registration, blockClass, "inventory validity does not prove same-TileEntity distance gating"));
                continue;
            }

            MethodNode activation = method(classes, blockClass,
                    Set.of("onBlockActivated", "func_149727_a"),
                    "(Lnet/minecraft/world/World;IIILnet/minecraft/entity/player/EntityPlayer;IFFF)Z");
            if (!canonicalOpen(activation)) {
                skipped.add(skip(registration, blockClass, "activation is not admitted sneak-pass plus vanilla IInventory opening"));
                continue;
            }

            MethodNode breakBlock = method(classes, blockClass,
                    Set.of("breakBlock", "func_149749_a"),
                    "(Lnet/minecraft/world/World;IIILnet/minecraft/block/Block;I)V");
            if (!canonicalDrop(breakBlock)) {
                skipped.add(skip(registration, blockClass, "break behavior does not prove inventory content drops"));
                continue;
            }

            MethodNode comparatorFlag = method(classes, blockClass,
                    Set.of("hasComparatorInputOverride", "func_149740_M"), "()Z");
            MethodNode comparator = method(classes, blockClass,
                    Set.of("getComparatorInputOverride", "func_149736_g"),
                    "(Lnet/minecraft/world/World;IIII)I");
            if (!Boolean.TRUE.equals(returnedBoolean(comparatorFlag))
                    || !calls(comparator, "net/minecraft/inventory/Container", "func_94526_b",
                    "(Lnet/minecraft/inventory/IInventory;)I")) {
                skipped.add(skip(registration, blockClass, "comparator behavior is not vanilla IInventory calculation"));
                continue;
            }

            rules.add(new Rule(registration.registryName(), registration.legacyNamespace(), blockClass,
                    tileClass, tileIds.get(tileClass), slots, 6, stackLimit, title, distanceSq,
                    true, true, true));
        }
        return new Analysis(rules, skipped, List.copyOf(new LinkedHashSet<>(diagnostics)));
    }

    private static Skipped skip(LegacyRegistryAnalyzer.Registration registration, String owner, String reason) {
        return new Skipped(registration.registryName(), owner, reason);
    }

    private static Map<String, String> registeredTiles(LegacyLifecycleAnalyzer.Analysis lifecycle) {
        Map<String, String> result = new LinkedHashMap<>();
        for (LegacyLifecycleAnalyzer.Registration registration : lifecycle.of(LegacyLifecycleAnalyzer.Kind.TILE_ENTITY)) {
            if (registration.arguments().size() < 2) continue;
            if (registration.arguments().get(0) instanceof LegacyLifecycleAnalyzer.TypeValue type
                    && registration.arguments().get(1) instanceof LegacyLifecycleAnalyzer.TextValue text) {
                result.put(type.internalName(), text.value());
            }
        }
        return result;
    }

    private static boolean canonicalNbt(MethodNode read, MethodNode write) {
        return read != null && write != null
                && string(read, "Items") && string(read, "Slot")
                && calls(read, "net/minecraft/nbt/NBTTagCompound", "func_150295_c",
                        "(Ljava/lang/String;I)Lnet/minecraft/nbt/NBTTagList;")
                && calls(read, "net/minecraft/nbt/NBTTagCompound", "func_74771_c", "(Ljava/lang/String;)B")
                && calls(read, "net/minecraft/item/ItemStack", "func_77949_a",
                        "(Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/item/ItemStack;")
                && string(write, "Items") && string(write, "Slot")
                && calls(write, "net/minecraft/nbt/NBTTagCompound", "func_74774_a", "(Ljava/lang/String;B)V")
                && calls(write, "net/minecraft/item/ItemStack", "func_77955_b",
                        "(Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/nbt/NBTTagCompound;")
                && calls(write, "net/minecraft/nbt/NBTTagList", "func_74742_a",
                        "(Lnet/minecraft/nbt/NBTBase;)V")
                && calls(write, "net/minecraft/nbt/NBTTagCompound", "func_74782_a",
                        "(Ljava/lang/String;Lnet/minecraft/nbt/NBTBase;)V");
    }

    private static Double usableDistanceSq(MethodNode method) {
        if (method == null
                || !calls(method, "net/minecraft/world/World", "func_147438_o",
                "(III)Lnet/minecraft/tileentity/TileEntity;")
                || !calls(method, "net/minecraft/entity/player/EntityPlayer", "func_70092_e", "(DDD)D")) return null;
        boolean identity = false;
        Double threshold = null;
        for (AbstractInsnNode insn : method.instructions) {
            if (insn.getOpcode() == Opcodes.IF_ACMPEQ || insn.getOpcode() == Opcodes.IF_ACMPNE) identity = true;
            if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof Double value && value > 1.0) threshold = value;
        }
        return identity ? threshold : null;
    }

    private static boolean canonicalOpen(MethodNode method) {
        return method != null
                && calls(method, "net/minecraft/entity/player/EntityPlayer", "func_70093_af", "()Z")
                && calls(method, "net/minecraft/world/World", "func_147438_o",
                "(III)Lnet/minecraft/tileentity/TileEntity;")
                && typed(method, Opcodes.CHECKCAST, IINVENTORY)
                && calls(method, "net/minecraft/entity/player/EntityPlayer", "func_71007_a",
                "(Lnet/minecraft/inventory/IInventory;)V")
                && directBooleanReturn(method, false) && directBooleanReturn(method, true);
    }

    private static boolean canonicalDrop(MethodNode method) {
        return method != null
                && calls(method, "net/minecraft/world/World", "func_147438_o",
                "(III)Lnet/minecraft/tileentity/TileEntity;")
                && typed(method, Opcodes.NEW, "net/minecraft/entity/item/EntityItem")
                && calls(method, "net/minecraft/world/World", "func_72838_d",
                "(Lnet/minecraft/entity/Entity;)Z");
    }

    private static boolean calls(MethodNode method, String owner, String name, String descriptor) {
        if (method == null) return false;
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof MethodInsnNode call && call.owner.equals(owner)
                    && call.name.equals(name) && call.desc.equals(descriptor)) return true;
        }
        return false;
    }

    private static boolean string(MethodNode method, String value) {
        if (method == null) return false;
        for (AbstractInsnNode insn : method.instructions)
            if (insn instanceof LdcInsnNode ldc && value.equals(ldc.cst)) return true;
        return false;
    }

    private static boolean typed(MethodNode method, int opcode, String type) {
        for (AbstractInsnNode insn : method.instructions)
            if (insn instanceof TypeInsnNode typed && typed.getOpcode() == opcode && typed.desc.equals(type)) return true;
        return false;
    }

    private static boolean directBooleanReturn(MethodNode method, boolean value) {
        int opcode = value ? Opcodes.ICONST_1 : Opcodes.ICONST_0;
        for (AbstractInsnNode insn : method.instructions) {
            if (insn.getOpcode() != opcode) continue;
            AbstractInsnNode next = real(insn.getNext());
            if (next != null && next.getOpcode() == Opcodes.IRETURN) return true;
        }
        return false;
    }

    private static Integer returnedInt(MethodNode method) {
        if (method == null) return null;
        Integer result = null;
        for (AbstractInsnNode insn : method.instructions) {
            Integer value = intConstant(insn);
            if (value == null) continue;
            AbstractInsnNode next = real(insn.getNext());
            if (next != null && next.getOpcode() == Opcodes.IRETURN) {
                if (result != null && !result.equals(value)) return null;
                result = value;
            }
        }
        return result;
    }

    private static Boolean returnedBoolean(MethodNode method) {
        Integer value = returnedInt(method);
        return value == null || (value != 0 && value != 1) ? null : value == 1;
    }

    private static String returnedString(MethodNode method) {
        if (method == null) return null;
        String result = null;
        for (AbstractInsnNode insn : method.instructions) {
            if (!(insn instanceof LdcInsnNode ldc) || !(ldc.cst instanceof String value)) continue;
            AbstractInsnNode next = real(insn.getNext());
            if (next != null && next.getOpcode() == Opcodes.ARETURN) {
                if (result != null && !result.equals(value)) return null;
                result = value;
            }
        }
        return result;
    }

    private static Integer intConstant(AbstractInsnNode insn) {
        return switch (insn.getOpcode()) {
            case Opcodes.ICONST_M1 -> -1;
            case Opcodes.ICONST_0 -> 0;
            case Opcodes.ICONST_1 -> 1;
            case Opcodes.ICONST_2 -> 2;
            case Opcodes.ICONST_3 -> 3;
            case Opcodes.ICONST_4 -> 4;
            case Opcodes.ICONST_5 -> 5;
            case Opcodes.BIPUSH, Opcodes.SIPUSH -> ((IntInsnNode) insn).operand;
            case Opcodes.LDC -> ((LdcInsnNode) insn).cst instanceof Integer value ? value : null;
            default -> null;
        };
    }

    private static AbstractInsnNode real(AbstractInsnNode insn) {
        while (insn != null && (insn.getType() == AbstractInsnNode.LABEL
                || insn.getType() == AbstractInsnNode.LINE
                || insn.getType() == AbstractInsnNode.FRAME)) insn = insn.getNext();
        return insn;
    }

    private static String uniqueCreatedType(MethodNode method) {
        if (method == null) return null;
        String result = null;
        for (AbstractInsnNode insn : method.instructions) {
            if (!(insn instanceof TypeInsnNode typed) || typed.getOpcode() != Opcodes.NEW) continue;
            if (result != null && !result.equals(typed.desc)) return null;
            result = typed.desc;
        }
        return result;
    }

    private static MethodNode method(Map<String, ClassNode> classes, String owner,
                                     Set<String> names, String descriptor) {
        Set<String> visited = new HashSet<>();
        while (owner != null && visited.add(owner)) {
            ClassNode node = classes.get(owner);
            if (node == null) return null;
            for (MethodNode method : node.methods)
                if ((method.access & Opcodes.ACC_STATIC) == 0 && names.contains(method.name)
                        && descriptor.equals(method.desc)) return method;
            owner = node.superName;
        }
        return null;
    }

    private static boolean inherits(Map<String, ClassNode> classes, String owner, String target) {
        Set<String> visited = new HashSet<>();
        while (owner != null && visited.add(owner)) {
            if (owner.equals(target)) return true;
            ClassNode node = classes.get(owner);
            if (node == null) return false;
            owner = node.superName;
        }
        return false;
    }

    private static boolean implementsType(Map<String, ClassNode> classes, String owner, String target) {
        ArrayDeque<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(owner);
        while (!queue.isEmpty()) {
            String next = queue.removeFirst();
            if (!visited.add(next)) continue;
            if (next.equals(target)) return true;
            ClassNode node = classes.get(next);
            if (node == null) continue;
            if (node.superName != null) queue.add(node.superName);
            queue.addAll(node.interfaces);
        }
        return false;
    }

    private static Map<String, ClassNode> loadClasses(Path jarPath) throws IOException {
        Map<String, ClassNode> classes = new HashMap<>();
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
                    // Existing global analyzers own malformed-class diagnostics.
                }
            }
        }
        return classes;
    }
}
