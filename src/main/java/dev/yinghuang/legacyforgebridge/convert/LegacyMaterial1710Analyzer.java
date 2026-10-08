package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;

/** Source-only proof of 1.7.10 Forge EnumHelper static armor/tool material declarations.
 * Never initializes or executes legacy classes. No mod-name, registry-name or material-name dispatch.
 */
public final class LegacyMaterial1710Analyzer {
    private static final String ARMOR_DESC = "(Ljava/lang/String;I[II)Lnet/minecraft/item/ItemArmor$ArmorMaterial;";
    private static final String TOOL_DESC = "(Ljava/lang/String;IIFFI)Lnet/minecraft/item/Item$ToolMaterial;";
    private static final String ENUM_HELPER = "net/minecraftforge/common/util/EnumHelper";
    public record Armor(String sourceOwner, String sourceField, int factor, int[] points, int enchantability) {
        public Armor { points = points.clone(); }
        @Override public int[] points() { return points.clone(); }
    }
    public record Tool(String sourceOwner, String sourceField, int harvestLevel, int durability,
                       float efficiency, float damageBonus, int enchantability) { }
    public record Analysis(Map<String, Armor> armor, Map<String, Tool> tools, List<String> diagnostics) {
        public Analysis {
            armor = Map.copyOf(armor); tools = Map.copyOf(tools); diagnostics = List.copyOf(diagnostics);
        }
    }
    public Analysis analyze(Path jarPath, Collection<String> sourceOwners) throws IOException {
        Map<String, Armor> armors = new TreeMap<>();
        Map<String, Tool> tools = new TreeMap<>();
        List<String> issues = new ArrayList<>();
        try (JarFile jar = new JarFile(jarPath.toFile(), false)) {
            for (String owner : new TreeSet<>(sourceOwners)) {
                if (owner == null || !owner.matches("[A-Za-z0-9_$]+(?:/[A-Za-z0-9_$]+)*")) continue;
                var entry = jar.getJarEntry(owner + ".class");
                if (entry == null) continue;
                ClassNode node = new ClassNode(Opcodes.ASM9);
                try (InputStream input = jar.getInputStream(entry)) {
                    new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                } catch (RuntimeException malformed) { issues.add("Unparseable source field owner " + owner); continue; }
                if (!owner.equals(node.name)) continue;
                for (MethodNode method : node.methods) {
                    if (!method.name.equals("<clinit>") || !method.desc.equals("()V")) continue;
                    List<AbstractInsnNode> code = new ArrayList<>();
                    for (AbstractInsnNode n : method.instructions) if (n.getOpcode() >= 0) code.add(n);
                    for (int i = 0; i + 1 < code.size(); ++i) {
                        if (!(code.get(i) instanceof MethodInsnNode call)
                                || call.getOpcode() != Opcodes.INVOKESTATIC
                                || !ENUM_HELPER.equals(call.owner)) continue;
                        if (!(code.get(i + 1) instanceof FieldInsnNode put)
                                || put.getOpcode() != Opcodes.PUTSTATIC || !owner.equals(put.owner)) continue;
                        String key = owner + "#" + put.name;
                        if (call.name.equals("addArmorMaterial") && call.desc.equals(ARMOR_DESC)
                                && put.desc.equals("Lnet/minecraft/item/ItemArmor$ArmorMaterial;")) {
                            Armor material = sourceArmor(code, i, owner, put.name);
                            if (material == null || armors.putIfAbsent(key, material) != null) {
                                issues.add("Unproved/duplicate static armor material field: " + key);
                                armors.remove(key);
                            }
                        } else if (call.name.equals("addToolMaterial") && call.desc.equals(TOOL_DESC)
                                && put.desc.equals("Lnet/minecraft/item/Item$ToolMaterial;")) {
                            Tool material = sourceTool(code, i, owner, put.name);
                            if (material == null || tools.putIfAbsent(key, material) != null) {
                                issues.add("Unproved/duplicate static tool material field: " + key);
                                tools.remove(key);
                            }
                        }
                    }
                }
            }
        }
        return new Analysis(armors, tools, issues);
    }
    private static Armor sourceArmor(List<AbstractInsnNode> code, int at, String owner, String field) {
        // javac canonical: LDC(name), PUSH(factor), ICONST_4, NEWARRAY T_INT,
        // 4 * [DUP,PUSH(index),PUSH(points),IASTORE], PUSH(enchantability), INVOKESTATIC.
        if (at < 21) return null;
        Integer enchant = integer(code.get(at - 1));
        if (enchant == null || enchant < 0 || enchant > 255) return null;
        int index = at - 2;
        int[] protections = new int[4];
        boolean[] seen = new boolean[4];
        for (int part = 3; part >= 0; part--) {
            if (index - 3 < 0 || code.get(index).getOpcode() != Opcodes.IASTORE
                    || code.get(index - 3).getOpcode() != Opcodes.DUP) return null;
            Integer face = integer(code.get(index - 2)), points = integer(code.get(index - 1));
            if (face == null || points == null || face < 0 || face > 3 || seen[face]
                    || points < 0 || points > 32) return null;
            seen[face] = true; protections[face] = points; index -= 4;
        }
        if (index - 3 < 0 || !(code.get(index) instanceof IntInsnNode array)
                || array.getOpcode() != Opcodes.NEWARRAY || array.operand != Opcodes.T_INT
                || !Integer.valueOf(4).equals(integer(code.get(index - 1)))) return null;
        Integer factor = integer(code.get(index - 2));
        if (!(code.get(index - 3) instanceof LdcInsnNode name) || !(name.cst instanceof String)
                || factor == null || factor < 1 || factor > 500) return null;
        for (boolean present : seen) if (!present) return null;
        return new Armor(owner, field, factor, protections, enchant);
    }
    private static Tool sourceTool(List<AbstractInsnNode> code, int at, String owner, String field) {
        if (at < 6 || !(code.get(at - 6) instanceof LdcInsnNode name)
                || !(name.cst instanceof String)) return null;
        Integer harvest = integer(code.get(at - 5)), uses = integer(code.get(at - 4)), enchant = integer(code.get(at - 1));
        Float efficiency = decimal(code.get(at - 3)), damage = decimal(code.get(at - 2));
        if (harvest == null || harvest < 0 || harvest > 16 || uses == null || uses < 1 || uses > 131072
                || efficiency == null || efficiency < 0 || efficiency > 512 || damage == null
                || damage < 0 || damage > 128 || enchant == null || enchant < 0 || enchant > 255) return null;
        return new Tool(owner, field, harvest, uses, efficiency, damage, enchant);
    }
    public static Integer integer(AbstractInsnNode n) {
        return switch (n.getOpcode()) {
            case Opcodes.ICONST_M1 -> -1;
            case Opcodes.ICONST_0 -> 0; case Opcodes.ICONST_1 -> 1; case Opcodes.ICONST_2 -> 2;
            case Opcodes.ICONST_3 -> 3; case Opcodes.ICONST_4 -> 4; case Opcodes.ICONST_5 -> 5;
            case Opcodes.BIPUSH, Opcodes.SIPUSH -> ((IntInsnNode)n).operand;
            case Opcodes.LDC -> n instanceof LdcInsnNode ldc && ldc.cst instanceof Integer i ? i : null;
            default -> null;
        };
    }
    private static Float decimal(AbstractInsnNode n) {
        return switch (n.getOpcode()) {
            case Opcodes.FCONST_0 -> 0f; case Opcodes.FCONST_1 -> 1f; case Opcodes.FCONST_2 -> 2f;
            case Opcodes.LDC -> n instanceof LdcInsnNode ldc && ldc.cst instanceof Float f && Float.isFinite(f) ? f : null;
            default -> null;
        };
    }
}
