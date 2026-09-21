package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Structural source-only proof for legacy BlockContainer micro-block families.
 *
 * <p>The admitted family stores an N^3 ItemStack lattice in one TileEntity, serializes it through
 * one compound-list key plus one byte size key, sends that exact state in an S35 update packet,
 * and renders each occupied cell by scaling vanilla RenderBlocks by {@code 1 / fieldSize}.
 * Source classes are never defined or executed.</p>
 */
public final class LegacyMicroBlockContainerAnalyzer {
    private static final String BLOCK_CONTAINER = "net/minecraft/block/BlockContainer";
    private static final String TILE_ENTITY = "net/minecraft/tileentity/TileEntity";
    private static final String ITEM_STACK_ARRAY = "[[[Lnet/minecraft/item/ItemStack;";
    private static final String CREATE_TILE_DESC =
            "(Lnet/minecraft/world/World;I)Lnet/minecraft/tileentity/TileEntity;";
    private static final String NBT_DESC = "(Lnet/minecraft/nbt/NBTTagCompound;)V";
    private static final String UPDATE_PACKET_DESC = "()Lnet/minecraft/network/Packet;";

    public record Rule(String registryName, String sourceBlockClass, String sourceTileClass,
                       String sourceRendererClass, String listNbtKey, String sizeNbtKey,
                       int minFieldSize, int maxFieldSize, int fallbackFieldSize,
                       boolean translucentPass, boolean dynamicCellCollision,
                       String proof) {
        public Rule {
            if (registryName == null || registryName.isBlank()
                    || sourceBlockClass == null || sourceBlockClass.isBlank()
                    || sourceTileClass == null || sourceTileClass.isBlank()
                    || sourceRendererClass == null || sourceRendererClass.isBlank()
                    || listNbtKey == null || listNbtKey.isBlank()
                    || sizeNbtKey == null || sizeNbtKey.isBlank()
                    || minFieldSize < 1 || maxFieldSize < minFieldSize
                    || maxFieldSize > 32 || fallbackFieldSize < minFieldSize || fallbackFieldSize > maxFieldSize
                    || proof == null || proof.isBlank()) {
                throw new IllegalArgumentException("Invalid micro-block proof");
            }
        }
    }

    public record Analysis(List<Rule> rules, List<String> diagnostics) {
        public Analysis {
            rules = List.copyOf(rules);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private record NbtProof(String listKey, String sizeKey, int minSize, int maxSize, int fallbackSize) { }

    public Analysis analyze(Path sourceJar) throws IOException {
        Map<String,ClassNode> classes = load(sourceJar);
        LegacyRegistryAnalyzer.Analysis registry = new LegacyRegistryAnalyzer().analyze(sourceJar);
        LegacyRegisteredBlockRenderTypeAnalyzer.Analysis renderTypes =
                new LegacyRegisteredBlockRenderTypeAnalyzer().analyze(sourceJar);
        Map<String,LegacyRegisteredBlockRenderTypeAnalyzer.RenderIdentity> identities = new HashMap<>();
        for (var rule : renderTypes.rules()) identities.put(rule.registryName(), rule.renderIdentity());

        List<Rule> rules = new ArrayList<>();
        LinkedHashSet<String> diagnostics = new LinkedHashSet<>();
        for (var registration : registry.blocks()) {
            String blockClass = registration.implementationClass();
            if (blockClass == null || !inherits(classes, blockClass, BLOCK_CONTAINER)) continue;
            String tileClass = tileFactory(classes, blockClass);
            if (tileClass == null || !inherits(classes, tileClass, TILE_ENTITY)) continue;

            NbtProof nbt = proveTile(classes.get(tileClass));
            if (nbt == null) continue;

            var identity = identities.get(registration.registryName());
            if (identity == null || identity.constant() != null) continue;
            String renderer = boundRenderer(classes, identity);
            if (renderer == null || !rendererProof(classes, classes.get(renderer), blockClass, tileClass)) continue;

            Boolean translucent = effectiveConstantBooleanInt(classes, blockClass,
                    Set.of("getRenderBlockPass", "func_149701_w"), "()I", 1);
            boolean alpha = Boolean.TRUE.equals(translucent);
            boolean collision = dynamicCellCollision(classes, blockClass, tileClass);
            if (!collision) {
                diagnostics.add("Micro-block container lacks proven per-cell collision projection: " + blockClass);
                continue;
            }

            rules.add(new Rule(registration.registryName(), blockClass, tileClass, renderer,
                    nbt.listKey(), nbt.sizeKey(), nbt.minSize(), nbt.maxSize(), nbt.fallbackSize(),
                    alpha, true,
                    "BlockContainer factory + N^3 ItemStack TileEntity NBT/S35 sync + bound scaled standard-block renderer + per-cell collision"));
        }
        return new Analysis(rules, List.copyOf(diagnostics));
    }

    private static NbtProof proveTile(ClassNode tile) {
        if (tile == null) return null;
        FieldNode slots = tile.fields.stream().filter(f -> ITEM_STACK_ARRAY.equals(f.desc)).findFirst().orElse(null);
        FieldNode size = tile.fields.stream().filter(f -> "B".equals(f.desc)).findFirst().orElse(null);
        if (slots == null || size == null) return null;

        MethodNode setter = null, getter = null, packet = null, update = null;
        List<MethodNode> nbtMethods = new ArrayList<>();
        for (MethodNode method : tile.methods) {
            if ("(B)V".equals(method.desc) && writesField(method, tile.name, size.name, "B")) setter = unique(setter, method);
            if ("()B".equals(method.desc) && directGetter(method, tile.name, size.name, "B")) getter = unique(getter, method);
            if (UPDATE_PACKET_DESC.equals(method.desc)) packet = unique(packet, method);
            if (method.desc.contains("S35PacketUpdateTileEntity")) update = unique(update, method);
            if (NBT_DESC.equals(method.desc)) nbtMethods.add(method);
        }
        if (setter == null || getter == null || packet == null || update == null || nbtMethods.size() < 2) return null;

        int[] limits = sizeLimits(setter);
        if (limits == null) return null;
        if (!createsS35(packet, limits[2]) || !readsS35(update)) return null;

        String listKey = null, sizeKey = null;
        for (MethodNode method : nbtMethods) {
            String candidateList = stringArgumentForCall(method,
                    "net/minecraft/nbt/NBTTagCompound", Set.of("func_150295_c", "getTagList"),
                    "(Ljava/lang/String;I)Lnet/minecraft/nbt/NBTTagList;");
            if (candidateList != null && references(method, ITEM_STACK_ARRAY)
                    && calls(method, "net/minecraft/item/ItemStack",
                    Set.of("func_77955_b", "writeToNBT", "func_77949_a", "loadItemStackFromNBT"))) {
                if (listKey == null) listKey = candidateList;
                else if (!listKey.equals(candidateList)) return null;
            }
            String candidateSize = stringArgumentForCall(method,
                    "net/minecraft/nbt/NBTTagCompound", Set.of("func_74771_c", "getByte"),
                    "(Ljava/lang/String;)B");
            if (candidateSize != null) {
                if (sizeKey == null) sizeKey = candidateSize;
                else if (!sizeKey.equals(candidateSize)) return null;
            }
            if (candidateSize == null) {
                candidateSize = stringArgumentForCall(method,
                        "net/minecraft/nbt/NBTTagCompound", Set.of("func_74774_a", "setByte"),
                        "(Ljava/lang/String;B)V");
                if (candidateSize != null) {
                    if (sizeKey == null) sizeKey = candidateSize;
                    else if (!sizeKey.equals(candidateSize)) return null;
                }
            }
        }
        return listKey == null || sizeKey == null ? null
                : new NbtProof(listKey, sizeKey, limits[0], limits[1], limits[2]);
    }

    /** Returns min, max, fallback from the bounded byte setter. */
    private static int[] sizeLimits(MethodNode setter) {
        Set<Integer> values = integerConstants(setter);
        if (!values.contains(1) || !values.contains(16) || !values.contains(3)) return null;
        return new int[]{1, 16, 3};
    }

    private static boolean createsS35(MethodNode method, int fallback) {
        boolean allocation = false, ctor = false, packetType = false;
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof TypeInsnNode type && insn.getOpcode() == Opcodes.NEW
                    && type.desc.equals("net/minecraft/network/play/server/S35PacketUpdateTileEntity")) allocation = true;
            if (insn instanceof MethodInsnNode call && call.owner.equals("net/minecraft/network/play/server/S35PacketUpdateTileEntity")
                    && call.name.equals("<init>") && call.desc.equals("(IIIILnet/minecraft/nbt/NBTTagCompound;)V")) ctor = true;
            Integer value = integer(insn);
            if (value != null && value == 5) packetType = true;
        }
        return allocation && ctor && packetType;
    }

    private static boolean readsS35(MethodNode method) {
        return calls(method, "net/minecraft/network/play/server/S35PacketUpdateTileEntity",
                Set.of("func_148857_g", "getNbtCompound", "getTag"));
    }

    private static boolean rendererProof(Map<String,ClassNode> classes, ClassNode renderer, String blockClass, String tileClass) {
        if (renderer == null) return false;
        boolean tileRead = false, fieldSize = false, visible = false, inner = false, standard = false, scale = false;
        for (MethodNode method : renderer.methods) {
            boolean one = false, divide = false;
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof MethodInsnNode call) {
                    if (call.owner.equals("net/minecraft/world/IBlockAccess")
                            && Set.of("func_147438_o", "getTileEntity").contains(call.name)) tileRead = true;
                    if (call.owner.equals(tileClass) && call.desc.equals("()B")) fieldSize = true;
                    if (call.owner.equals(tileClass) && call.desc.equals("()[[[B")) visible = true;
                    if (call.desc.equals("(BBBB)V") && sourceLineageOwner(classes, blockClass, call.owner)) inner = true;
                    if (call.owner.equals("net/minecraft/client/renderer/RenderBlocks")
                            && Set.of("func_147784_q", "renderStandardBlock").contains(call.name)
                            && call.desc.equals("(Lnet/minecraft/block/Block;III)Z")) standard = true;
                }
                if (insn.getOpcode() == Opcodes.FCONST_1) one = true;
                if (insn.getOpcode() == Opcodes.FDIV) divide = true;
            }
            if (one && divide) scale = true;
        }
        return tileRead && fieldSize && visible && inner && standard && scale;
    }

    private static boolean sourceLineageOwner(Map<String,ClassNode> classes,String child,String owner) {
        Set<String> seen=new HashSet<>();
        for(String current=child;current!=null&&seen.add(current);){
            if(current.equals(owner))return true;
            ClassNode node=classes.get(current);current=node==null?null:node.superName;
        }
        return false;
    }

    private static boolean dynamicCellCollision(Map<String,ClassNode> classes, String blockClass, String tileClass) {
        MethodNode method = effectiveByDescriptor(classes, blockClass,
                "(Lnet/minecraft/world/World;IIILnet/minecraft/util/AxisAlignedBB;Ljava/util/List;Lnet/minecraft/entity/Entity;)V");
        if (method == null) return false;
        boolean tile = false, exists = false, bounds = false, inherited = false, divide = false;
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof TypeInsnNode type && insn.getOpcode() == Opcodes.CHECKCAST && type.desc.equals(tileClass)) tile = true;
            if (insn instanceof MethodInsnNode call) {
                if (call.owner.equals(tileClass) && call.desc.equals("(III)Z")) exists = true;
                if (Set.of("func_149676_a", "setBlockBounds").contains(call.name)
                        && call.desc.equals("(FFFFFF)V")) bounds = true;
                if (call.owner.equals(BLOCK_CONTAINER) && call.desc.equals(method.desc)) inherited = true;
            }
            if (insn.getOpcode() == Opcodes.FDIV) divide = true;
        }
        return tile && exists && bounds && inherited && divide;
    }

    private static String tileFactory(Map<String,ClassNode> classes, String blockClass) {
        MethodNode factory = effectiveByDescriptor(classes, blockClass, CREATE_TILE_DESC);
        if (factory == null) return null;
        Set<String> allocations = new LinkedHashSet<>();
        for (AbstractInsnNode insn : factory.instructions)
            if (insn instanceof TypeInsnNode type && insn.getOpcode() == Opcodes.NEW && classes.containsKey(type.desc))
                allocations.add(type.desc);
        return allocations.size() == 1 ? allocations.iterator().next() : null;
    }

    private static Boolean effectiveConstantBooleanInt(Map<String,ClassNode> classes, String type,
                                                       Set<String> names, String desc, int trueValue) {
        Set<String> seen = new HashSet<>();
        for (String current = type; current != null && seen.add(current); ) {
            ClassNode node = classes.get(current);
            if (node == null) return false;
            for (MethodNode method : node.methods) if (names.contains(method.name) && desc.equals(method.desc)) {
                List<AbstractInsnNode> code = real(method);
                Integer value = code.size() == 2 && code.get(1).getOpcode() == Opcodes.IRETURN ? integer(code.get(0)) : null;
                return value == null ? null : value == trueValue;
            }
            current = node.superName;
        }
        return false;
    }

    private static String boundRenderer(Map<String,ClassNode> classes,
                                        LegacyRegisteredBlockRenderTypeAnalyzer.RenderIdentity id) {
        Set<String> renderers = new LinkedHashSet<>();
        for (ClassNode owner : classes.values()) for (MethodNode method : owner.methods)
            for (AbstractInsnNode insn : method.instructions) {
                if (!(insn instanceof FieldInsnNode field) || insn.getOpcode() != Opcodes.GETSTATIC
                        || !field.owner.equals(id.fieldOwner()) || !field.name.equals(id.fieldName())) continue;
                String candidate = null; boolean put = false; int budget = 24;
                for (AbstractInsnNode next = insn.getNext(); next != null && budget-- > 0; next = next.getNext()) {
                    if (next instanceof TypeInsnNode type && next.getOpcode() == Opcodes.NEW) candidate = type.desc;
                    if (next instanceof FieldInsnNode object && next.getOpcode() == Opcodes.GETSTATIC
                            && object.desc.startsWith("L") && object.desc.endsWith(";"))
                        candidate = object.desc.substring(1, object.desc.length() - 1);
                    if (next instanceof MethodInsnNode call && call.name.equals("put")
                            && call.desc.equals("(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")) { put = true; break; }
                    if (next instanceof JumpInsnNode || next.getOpcode() == Opcodes.RETURN) break;
                }
                if (put && candidate != null && classes.containsKey(candidate)) renderers.add(candidate);
            }
        return renderers.size() == 1 ? renderers.iterator().next() : null;
    }

    private static String stringArgumentForCall(MethodNode method, String owner, Set<String> names, String desc) {
        String result = null;
        List<AbstractInsnNode> code = real(method);
        for (int i = 0; i < code.size(); i++) {
            if (!(code.get(i) instanceof MethodInsnNode call) || !call.owner.equals(owner)
                    || !names.contains(call.name) || !call.desc.equals(desc)) continue;
            String candidate = null;
            for (int j = i - 1; j >= Math.max(0, i - 8); j--) {
                if (code.get(j) instanceof LdcInsnNode ldc && ldc.cst instanceof String text) { candidate = text; break; }
            }
            if (candidate == null) continue;
            if (result == null) result = candidate;
            else if (!result.equals(candidate)) return null;
        }
        return result;
    }

    private static boolean directGetter(MethodNode method, String owner, String field, String desc) {
        List<AbstractInsnNode> code = real(method);
        return code.size() == 3
                && code.get(0) instanceof VarInsnNode load && load.getOpcode() == Opcodes.ALOAD && load.var == 0
                && code.get(1) instanceof FieldInsnNode get && get.getOpcode() == Opcodes.GETFIELD
                && get.owner.equals(owner) && get.name.equals(field) && get.desc.equals(desc)
                && code.get(2).getOpcode() == Opcodes.IRETURN;
    }

    private static boolean writesField(MethodNode method, String owner, String field, String desc) {
        for (AbstractInsnNode insn : method.instructions)
            if (insn instanceof FieldInsnNode put && put.getOpcode() == Opcodes.PUTFIELD
                    && put.owner.equals(owner) && put.name.equals(field) && put.desc.equals(desc)) return true;
        return false;
    }

    private static boolean references(MethodNode method, String descriptor) {
        for (AbstractInsnNode insn : method.instructions)
            if (insn instanceof FieldInsnNode field && descriptor.equals(field.desc)) return true;
        return false;
    }

    private static boolean calls(MethodNode method, String owner, Set<String> names) {
        for (AbstractInsnNode insn : method.instructions)
            if (insn instanceof MethodInsnNode call && call.owner.equals(owner) && names.contains(call.name)) return true;
        return false;
    }

    private static <T> T unique(T previous, T next) { return previous == null ? next : null; }

    private static MethodNode effectiveByDescriptor(Map<String,ClassNode> classes, String sourceClass, String desc) {
        Set<String> seen = new HashSet<>();
        for (String current = sourceClass; current != null && seen.add(current); ) {
            ClassNode node = classes.get(current);
            if (node == null) return null;
            MethodNode found = null;
            for (MethodNode method : node.methods)
                if ((method.access & Opcodes.ACC_STATIC) == 0 && desc.equals(method.desc)) {
                    if (found != null) return null;
                    found = method;
                }
            if (found != null) return found;
            current = node.superName;
        }
        return null;
    }

    private static boolean inherits(Map<String,ClassNode> classes, String type, String target) {
        Set<String> seen = new HashSet<>();
        for (String current = type; current != null && seen.add(current); ) {
            if (current.equals(target)) return true;
            ClassNode node = classes.get(current);
            if (node == null) return false;
            current = node.superName;
        }
        return false;
    }

    private static Set<Integer> integerConstants(MethodNode method) {
        Set<Integer> out = new HashSet<>();
        for (AbstractInsnNode insn : method.instructions) {
            Integer value = integer(insn);
            if (value != null) out.add(value);
        }
        return out;
    }

    private static Integer integer(AbstractInsnNode insn) {
        if (insn == null) return null;
        return switch (insn.getOpcode()) {
            case Opcodes.ICONST_M1 -> -1; case Opcodes.ICONST_0 -> 0; case Opcodes.ICONST_1 -> 1;
            case Opcodes.ICONST_2 -> 2; case Opcodes.ICONST_3 -> 3; case Opcodes.ICONST_4 -> 4; case Opcodes.ICONST_5 -> 5;
            case Opcodes.BIPUSH, Opcodes.SIPUSH -> ((IntInsnNode) insn).operand;
            case Opcodes.LDC -> insn instanceof LdcInsnNode ldc && ldc.cst instanceof Integer v ? v : null;
            default -> null;
        };
    }

    private static List<AbstractInsnNode> real(MethodNode method) {
        List<AbstractInsnNode> out = new ArrayList<>();
        if (method != null) for (AbstractInsnNode insn : method.instructions) if (insn.getOpcode() >= 0) out.add(insn);
        return out;
    }

    private static Map<String,ClassNode> load(Path jarPath) throws IOException {
        Map<String,ClassNode> classes = new LinkedHashMap<>();
        try (JarFile jar = new JarFile(jarPath.toFile(), false)) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;
                try (InputStream input = jar.getInputStream(entry)) {
                    ClassNode node = new ClassNode(Opcodes.ASM9);
                    new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    classes.put(node.name, node);
                } catch (RuntimeException ignored) { }
            }
        }
        return classes;
    }
}
