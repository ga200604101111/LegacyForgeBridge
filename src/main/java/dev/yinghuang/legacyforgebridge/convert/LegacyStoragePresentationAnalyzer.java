package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Conservative presentation extractor for already-admitted legacy storage blocks.
 *
 * <p>The admitted presentation family is intentionally narrow: a normal opaque full cube, four
 * raw metadata facings derived from the placing entity's yaw quadrant, and exactly two registered
 * block icons (front/other). It never infers presentation from class names or texture filenames.</p>
 */
public final class LegacyStoragePresentationAnalyzer {
    public static final String ORIENTATION_PLAYER_YAW_OPPOSITE_QUADRANT = "player_yaw_opposite_quadrant_0_3";
    private static final String ICON = "net/minecraft/util/IIcon";

    public record Presentation(String sourceBlockClass, String orientation,
                               String frontTexture, String otherTexture) { }
    public record Analysis(Map<String, Presentation> presentations, List<String> diagnostics) {
        public Analysis {
            presentations = Collections.unmodifiableMap(new LinkedHashMap<>(presentations));
            diagnostics = List.copyOf(diagnostics);
        }
    }

    public Analysis analyze(Path jarPath, Collection<String> sourceBlockClasses) throws IOException {
        Map<String, ClassNode> classes = loadClasses(jarPath);
        Map<String, Presentation> output = new LinkedHashMap<>();
        List<String> diagnostics = new ArrayList<>();
        for (String owner : new TreeSet<>(sourceBlockClasses)) {
            ClassNode node = classes.get(owner);
            if (node == null) {
                diagnostics.add(owner + ": source class unavailable for storage presentation analysis");
                continue;
            }
            if (!simpleFullCube(node)) {
                diagnostics.add(owner + ": storage presentation is not a proven normal opaque full cube");
                continue;
            }
            if (!placedByYawQuadrant(classes, owner)) {
                diagnostics.add(owner + ": placement metadata is not the admitted player-yaw quadrant mapping");
                continue;
            }
            Map<String, String> texturesByField = iconAssignments(node);
            FaceFields faces = faceFields(node);
            if (faces == null) {
                diagnostics.add(owner + ": front/other icon metadata mapping is not the admitted 0=N,1=E,2=S,3=W layout");
                continue;
            }
            String front = texturesByField.get(faces.frontField());
            String other = texturesByField.get(faces.otherField());
            if (!qualifiedTexture(front) || !qualifiedTexture(other) || front.equals(other)) {
                diagnostics.add(owner + ": front/other icon registrations are missing, unqualified, or ambiguous");
                continue;
            }
            output.put(owner, new Presentation(owner, ORIENTATION_PLAYER_YAW_OPPOSITE_QUADRANT, front, other));
        }
        return new Analysis(output, List.copyOf(new LinkedHashSet<>(diagnostics)));
    }

    private static boolean simpleFullCube(ClassNode node) {
        MethodNode renderType = ownMethod(node, Set.of("getRenderType", "func_149645_b"), "()I");
        MethodNode opaque = ownMethod(node, Set.of("isOpaqueCube", "func_149662_c"), "()Z");
        MethodNode normal = ownMethod(node, Set.of("renderAsNormalBlock", "func_149686_d"), "()Z");
        return Integer.valueOf(0).equals(returnedInt(renderType))
                && Boolean.TRUE.equals(returnedBoolean(opaque))
                && Boolean.TRUE.equals(returnedBoolean(normal))
                && fullCubeBounds(ownMethod(node, Set.of("<init>"), "()V"));
    }

    private static boolean fullCubeBounds(MethodNode constructor) {
        if (constructor == null) return false;
        List<AbstractInsnNode> insns = real(constructor);
        for (int i = 0; i < insns.size(); i++) {
            if (!(insns.get(i) instanceof MethodInsnNode call)
                    || !(call.name.equals("setBlockBounds") || call.name.equals("func_149676_a"))
                    || !call.desc.equals("(FFFFFF)V")) continue;
            if (i < 6) return false;
            float[] expected = {0F, 0F, 0F, 1F, 1F, 1F};
            for (int arg = 0; arg < 6; arg++) {
                Float value = floatConstant(insns.get(i - 6 + arg));
                if (value == null || Float.compare(value, expected[arg]) != 0) return false;
            }
        }
        return true;
    }

    private static boolean placedByYawQuadrant(Map<String, ClassNode> classes, String owner) {
        MethodNode placed = method(classes, owner, Set.of("onBlockPlacedBy", "func_149689_a"),
                "(Lnet/minecraft/world/World;IIILnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;)V");
        if (placed == null) return false;
        List<AbstractInsnNode> insns = real(placed);
        for (int i = 0; i < insns.size(); i++) {
            if (!(insns.get(i) instanceof MethodInsnNode worldCall)
                    || !worldCall.owner.equals("net/minecraft/world/World")
                    || !(worldCall.name.equals("setBlockMetadataWithNotify") || worldCall.name.equals("func_72921_c"))
                    || !worldCall.desc.equals("(IIIII)Z") || i < 3) continue;
            if (!Integer.valueOf(3).equals(intConstant(insns.get(i - 1)))) continue;
            if (!(insns.get(i - 2) instanceof MethodInsnNode helper) || helper.getOpcode() != Opcodes.INVOKESTATIC) continue;
            if (!(helper.desc.equals("(Lnet/minecraft/entity/Entity;)B")
                    || helper.desc.equals("(Lnet/minecraft/entity/Entity;)I"))) continue;
            if (!(insns.get(i - 3) instanceof VarInsnNode entityLoad)
                    || entityLoad.getOpcode() != Opcodes.ALOAD || entityLoad.var != 5) continue;
            ClassNode helperOwner = classes.get(helper.owner);
            MethodNode helperMethod = helperOwner == null ? null : ownMethod(helperOwner, Set.of(helper.name), helper.desc);
            if (yawQuadrantHelper(helperMethod)) return true;
        }
        return false;
    }

    private static boolean yawQuadrantHelper(MethodNode method) {
        if (method == null || (method.access & Opcodes.ACC_STATIC) == 0) return false;
        boolean yaw = false, four = false, threeSixty = false, half = false, floor = false, mask = false, returns = false;
        List<AbstractInsnNode> insns = real(method);
        for (int i = 0; i < insns.size(); i++) {
            AbstractInsnNode insn = insns.get(i);
            if (insn instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETFIELD
                    && field.owner.equals("net/minecraft/entity/Entity") && field.desc.equals("F")
                    && (field.name.equals("rotationYaw") || field.name.equals("field_70177_z"))) yaw = true;
            Float f = floatConstant(insn);
            if (f != null && Float.compare(f, 4F) == 0) four = true;
            if (f != null && Float.compare(f, 360F) == 0) threeSixty = true;
            if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof Double d && Double.compare(d, 0.5D) == 0) half = true;
            if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC
                    && call.owner.equals("net/minecraft/util/MathHelper")
                    && (call.name.equals("floor_double") || call.name.equals("func_76128_c"))
                    && call.desc.equals("(D)I")) floor = true;
            if (insn.getOpcode() == Opcodes.IAND && i > 0 && Integer.valueOf(3).equals(intConstant(insns.get(i - 1)))) mask = true;
            if (insn.getOpcode() == Opcodes.IRETURN) returns = true;
        }
        return yaw && four && threeSixty && half && floor && mask && returns;
    }

    private static Map<String, String> iconAssignments(ClassNode node) {
        MethodNode method = ownMethod(node, Set.of("registerBlockIcons", "func_149651_a"),
                "(Lnet/minecraft/client/renderer/texture/IIconRegister;)V");
        if (method == null) return Map.of();
        List<AbstractInsnNode> insns = real(method);
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 2; i < insns.size(); i++) {
            if (!(insns.get(i) instanceof FieldInsnNode field) || field.getOpcode() != Opcodes.PUTFIELD
                    || !field.owner.equals(node.name) || !field.desc.equals("L" + ICON + ";")) continue;
            if (!(insns.get(i - 1) instanceof MethodInsnNode call)
                    || !call.owner.equals("net/minecraft/client/renderer/texture/IIconRegister")
                    || !(call.name.equals("registerIcon") || call.name.equals("func_94245_a"))
                    || !call.desc.equals("(Ljava/lang/String;)Lnet/minecraft/util/IIcon;")) continue;
            if (!(insns.get(i - 2) instanceof LdcInsnNode ldc) || !(ldc.cst instanceof String texture)) continue;
            result.put(field.name, texture);
        }
        return result;
    }

    private record FaceFields(String frontField, String otherField) { }

    private static FaceFields faceFields(ClassNode node) {
        MethodNode method = ownMethod(node, Set.of("getIcon", "func_149673_e"),
                "(Lnet/minecraft/world/IBlockAccess;IIII)Lnet/minecraft/util/IIcon;");
        if (method == null || !calls(method, "net/minecraft/world/IBlockAccess", "func_72805_g", "(III)I")) return null;
        List<AbstractInsnNode> insns = real(method);
        Map<Integer, Integer> expected = Map.of(0, 2, 1, 5, 2, 3, 3, 4);
        Map<Integer, String> mapped = new HashMap<>();
        Set<String> returnedFields = new LinkedHashSet<>();
        for (int i = 0; i < insns.size(); i++) {
            if (insns.get(i).getOpcode() != Opcodes.ARETURN || i < 7
                    || !(insns.get(i - 1) instanceof FieldInsnNode field)
                    || field.getOpcode() != Opcodes.GETFIELD || !field.owner.equals(node.name)
                    || !field.desc.equals("L" + ICON + ";")) continue;
            returnedFields.add(field.name);
            if (insns.get(i - 2).getOpcode() != Opcodes.ALOAD
                    || !(insns.get(i - 3) instanceof JumpInsnNode sideJump)
                    || sideJump.getOpcode() != Opcodes.IF_ICMPNE
                    || !(insns.get(i - 5) instanceof VarInsnNode sideLoad)
                    || sideLoad.getOpcode() != Opcodes.ILOAD || sideLoad.var != 5
                    || !(insns.get(i - 6) instanceof JumpInsnNode metaJump)) continue;
            Integer side = intConstant(insns.get(i - 4));
            Integer meta;
            if (metaJump.getOpcode() == Opcodes.IFNE) {
                meta = 0;
                if (!(insns.get(i - 7) instanceof VarInsnNode metaLoad) || metaLoad.getOpcode() != Opcodes.ILOAD) continue;
            } else if (metaJump.getOpcode() == Opcodes.IF_ICMPNE) {
                if (i < 8 || !(insns.get(i - 8) instanceof VarInsnNode metaLoad) || metaLoad.getOpcode() != Opcodes.ILOAD) continue;
                meta = intConstant(insns.get(i - 7));
            } else continue;
            if (side == null || meta == null || !expected.containsKey(meta) || expected.get(meta).intValue() != side) continue;
            mapped.put(meta, field.name);
        }
        if (mapped.size() != 4 || returnedFields.size() != 2) return null;
        String front = mapped.get(0);
        if (front == null || mapped.values().stream().anyMatch(value -> !value.equals(front))) return null;
        String other = returnedFields.stream().filter(value -> !value.equals(front)).findFirst().orElse(null);
        return other == null ? null : new FaceFields(front, other);
    }

    private static boolean qualifiedTexture(String value) {
        if (value == null) return false;
        int colon = value.indexOf(':');
        return colon > 0 && colon < value.length() - 1 && value.indexOf(':', colon + 1) < 0;
    }

    private static boolean calls(MethodNode method, String owner, String name, String descriptor) {
        for (AbstractInsnNode insn : method.instructions)
            if (insn instanceof MethodInsnNode call && call.owner.equals(owner)
                    && call.name.equals(name) && call.desc.equals(descriptor)) return true;
        return false;
    }

    private static MethodNode ownMethod(ClassNode node, Set<String> names, String descriptor) {
        for (MethodNode method : node.methods)
            if (names.contains(method.name) && descriptor.equals(method.desc)) return method;
        return null;
    }

    private static MethodNode method(Map<String, ClassNode> classes, String owner, Set<String> names, String descriptor) {
        Set<String> seen = new HashSet<>();
        while (owner != null && seen.add(owner)) {
            ClassNode node = classes.get(owner);
            if (node == null) return null;
            MethodNode method = ownMethod(node, names, descriptor);
            if (method != null) return method;
            owner = node.superName;
        }
        return null;
    }

    private static Integer returnedInt(MethodNode method) {
        if (method == null) return null;
        Integer result = null;
        List<AbstractInsnNode> insns = real(method);
        for (int i = 1; i < insns.size(); i++) {
            if (insns.get(i).getOpcode() != Opcodes.IRETURN) continue;
            Integer value = intConstant(insns.get(i - 1));
            if (value == null || result != null && !result.equals(value)) return null;
            result = value;
        }
        return result;
    }

    private static Boolean returnedBoolean(MethodNode method) {
        Integer value = returnedInt(method);
        return value == null || value < 0 || value > 1 ? null : value == 1;
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
            case Opcodes.LDC -> insn instanceof LdcInsnNode ldc && ldc.cst instanceof Integer value ? value : null;
            default -> null;
        };
    }

    private static Float floatConstant(AbstractInsnNode insn) {
        return switch (insn.getOpcode()) {
            case Opcodes.FCONST_0 -> 0F;
            case Opcodes.FCONST_1 -> 1F;
            case Opcodes.FCONST_2 -> 2F;
            case Opcodes.LDC -> insn instanceof LdcInsnNode ldc && ldc.cst instanceof Float value ? value : null;
            default -> null;
        };
    }

    private static List<AbstractInsnNode> real(MethodNode method) {
        List<AbstractInsnNode> result = new ArrayList<>();
        for (AbstractInsnNode insn : method.instructions)
            if (insn.getType() != AbstractInsnNode.LABEL && insn.getType() != AbstractInsnNode.LINE
                    && insn.getType() != AbstractInsnNode.FRAME) result.add(insn);
        return result;
    }

    private static Map<String, ClassNode> loadClasses(Path jarPath) throws IOException {
        Map<String, ClassNode> classes = new LinkedHashMap<>();
        try (JarFile jar = new JarFile(jarPath.toFile(), false)) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;
                try (InputStream input = jar.getInputStream(entry)) {
                    ClassNode node = new ClassNode(Opcodes.ASM9);
                    new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    if (classes.put(node.name, node) != null) throw new IOException("Duplicate class " + node.name);
                } catch (RuntimeException malformed) {
                    // Presentation evidence is optional. Malformed source simply cannot satisfy this family.
                }
            }
        }
        return classes;
    }
}
