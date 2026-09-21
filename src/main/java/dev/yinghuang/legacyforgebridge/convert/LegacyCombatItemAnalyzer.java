package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
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
 * Non-executing proof for bounded legacy sword/bow constants and their native item presentation.
 *
 * <p>Only constants assigned by source bytecode are exported. 1.7 did not expose attack speed;
 * admitted swords receive the ordinary modern sword cadence separately in the conversion pass.</p>
 */
public final class LegacyCombatItemAnalyzer {
    private static final Set<String> MAX_DAMAGE = Set.of("setMaxDamage", "func_77656_e");
    private static final Set<String> ATTRIBUTE_METHODS = Set.of("getItemAttributeModifiers", "func_111205_h");
    private static final String ATTRIBUTE_MODIFIER = "net/minecraft/entity/ai/attributes/AttributeModifier";

    public enum Kind { SWORD, BOW }

    public record Rule(String registryName, String sourceClass, Kind kind, int durability,
                       float attackDamage, int useDuration, String pullTexturePrefix, int pullStages) {
        public Rule {
            if (registryName == null || registryName.isBlank() || sourceClass == null || sourceClass.isBlank()
                    || kind == null || durability < 0 || !Float.isFinite(attackDamage) || attackDamage < 0F
                    || useDuration < 0 || pullStages < 0) {
                throw new IllegalArgumentException("Invalid combat-item proof");
            }
            if (kind == Kind.SWORD && attackDamage <= 0F) throw new IllegalArgumentException("Sword damage was not proven");
            if (kind == Kind.BOW && (useDuration <= 0 || pullTexturePrefix == null || pullTexturePrefix.isBlank()
                    || pullStages <= 0)) throw new IllegalArgumentException("Bow use/pull presentation was not proven");
        }
    }

    public record Skipped(String registryName, String sourceClass, String reason) { }
    public record Analysis(List<Rule> rules, List<Skipped> skipped, List<String> diagnostics) {
        public Analysis {
            rules = List.copyOf(rules);
            skipped = List.copyOf(skipped);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private final Map<String, ClassNode> classes = new LinkedHashMap<>();
    private final List<String> diagnostics = new ArrayList<>();

    public Analysis analyze(Path jarPath) throws IOException {
        classes.clear();
        diagnostics.clear();
        load(jarPath);
        LegacyRegistryAnalyzer registryAnalyzer = new LegacyRegistryAnalyzer();
        var registry = registryAnalyzer.analyze(jarPath);
        List<Rule> rules = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        for (var item : registry.items()) {
            String source = item.implementationClass();
            if (source == null) continue;
            String classified = registryAnalyzer.classifyItem(source);
            Float attributeDamage = uniqueAttackDamage(source);
            Float directDamage = uniqueDirectEntityDamage(source);
            Kind kind = "bow".equals(classified) ? Kind.BOW
                    : ("sword".equals(classified) || attributeDamage != null || directDamage != null ? Kind.SWORD : null);
            if (kind == null) continue;
            int durability = uniqueDurability(source);
            if (kind == Kind.SWORD) {
                Float damage = attributeDamage != null ? attributeDamage : directDamage;
                if (damage == null || damage <= 0F) {
                    skipped.add(new Skipped(item.registryName(), source,
                            "No unique source attack-damage AttributeModifier was proven."));
                    continue;
                }
                rules.add(new Rule(item.registryName(), source, kind, Math.max(0, durability), damage,
                        0, null, 0));
            } else {
                String prefix = uniquePullTexturePrefix(source);
                int stages = uniquePullStageCount(source);
                if (prefix == null || stages <= 0) {
                    skipped.add(new Skipped(item.registryName(), source,
                            "No unique source bow pull texture sequence was proven."));
                    continue;
                }
                rules.add(new Rule(item.registryName(), source, kind, Math.max(0, durability), 0F,
                        72_000, prefix, stages));
            }
        }
        return new Analysis(rules, skipped, List.copyOf(new LinkedHashSet<>(diagnostics)));
    }

    private int uniqueDurability(String sourceClass) {
        Set<Integer> values = new LinkedHashSet<>();
        for (ClassNode node : sourceLineage(sourceClass)) for (MethodNode method : node.methods) {
            if (!"<init>".equals(method.name)) continue;
            for (AbstractInsnNode instruction : method.instructions) {
                if (!(instruction instanceof MethodInsnNode call)
                        || !MAX_DAMAGE.contains(call.name)
                        || !"(I)Lnet/minecraft/item/Item;".equals(call.desc)) continue;
                Integer value = intConstant(previousReal(instruction.getPrevious()));
                if (value != null && value >= 0) values.add(value);
            }
        }
        return values.size() == 1 ? values.iterator().next() : 0;
    }

    private Float uniqueAttackDamage(String sourceClass) {
        Set<Float> values = new LinkedHashSet<>();
        for (ClassNode node : sourceLineage(sourceClass)) for (MethodNode method : node.methods) {
            if (!ATTRIBUTE_METHODS.contains(method.name)) continue;
            boolean attackAttribute = false;
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof FieldInsnNode field
                        && field.owner.equals("net/minecraft/entity/SharedMonsterAttributes")
                        && (field.name.equals("field_111264_e") || field.name.toLowerCase().contains("attack"))) {
                    attackAttribute = true;
                }
                if (!attackAttribute || !(instruction instanceof MethodInsnNode call)
                        || call.getOpcode() != Opcodes.INVOKESPECIAL
                        || !ATTRIBUTE_MODIFIER.equals(call.owner)
                        || !"<init>".equals(call.name)
                        || !"(Ljava/util/UUID;Ljava/lang/String;DI)V".equals(call.desc)) continue;
                AbstractInsnNode cursor = previousReal(instruction.getPrevious());
                Integer operation = intConstant(cursor);
                if (operation == null || operation != 0) continue;
                cursor = previousReal(cursor.getPrevious());
                Number amount = numberConstant(cursor);
                if (amount != null && Float.isFinite(amount.floatValue()) && amount.floatValue() > 0F) {
                    values.add(amount.floatValue());
                }
            }
        }
        return values.size() == 1 ? values.iterator().next() : null;
    }

    /**
     * Legacy mods frequently implement weapons as plain Item/ItemSword subclasses and expose their
     * actual attack value through a source-owned getDamageVsEntity(Entity) method instead of
     * getItemAttributeModifiers(). Admit only the conservative leading-base-value shape:
     * a positive numeric constant stored to a float local before any branch/call/field access.
     *
     * <p>This is intentionally behavior-based rather than class/name based, so custom weapons from
     * unrelated mods are discovered without adding per-item allowlists.</p>
     */
    private Float uniqueDirectEntityDamage(String sourceClass) {
        Set<Float> values = new LinkedHashSet<>();
        for (ClassNode node : sourceLineage(sourceClass)) for (MethodNode method : node.methods) {
            if (!"getDamageVsEntity".equals(method.name)
                    || !"(Lnet/minecraft/entity/Entity;)F".equals(method.desc)) continue;
            AbstractInsnNode cursor = firstReal(method.instructions.getFirst());
            int budget = 0;
            while (cursor != null && budget++ < 8) {
                Number number = numberConstant(cursor);
                if (number != null && Float.isFinite(number.floatValue()) && number.floatValue() > 0F) {
                    AbstractInsnNode next = nextReal(cursor.getNext());
                    if (next != null && next.getOpcode() == Opcodes.FSTORE) {
                        values.add(number.floatValue());
                    }
                    break;
                }
                int opcode = cursor.getOpcode();
                if (cursor instanceof MethodInsnNode || cursor instanceof FieldInsnNode
                        || (opcode >= Opcodes.IFEQ && opcode <= Opcodes.IF_ACMPNE)
                        || opcode == Opcodes.GOTO || opcode == Opcodes.TABLESWITCH || opcode == Opcodes.LOOKUPSWITCH) {
                    break;
                }
                cursor = nextReal(cursor.getNext());
            }
        }
        return values.size() == 1 ? values.iterator().next() : null;
    }

    private static AbstractInsnNode firstReal(AbstractInsnNode instruction) {
        AbstractInsnNode current = instruction;
        while (current != null && current.getOpcode() < 0) current = current.getNext();
        return current;
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode instruction) {
        AbstractInsnNode current = instruction;
        while (current != null && current.getOpcode() < 0) current = current.getNext();
        return current;
    }

    private String uniquePullTexturePrefix(String sourceClass) {
        Set<String> values = new LinkedHashSet<>();
        for (ClassNode node : sourceLineage(sourceClass)) for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof LdcInsnNode ldc && ldc.cst instanceof String text
                        && text.contains(":") && text.endsWith("_pull_")) values.add(text);
            }
        }
        return values.size() == 1 ? values.iterator().next() : null;
    }

    private int uniquePullStageCount(String sourceClass) {
        Set<Integer> values = new LinkedHashSet<>();
        for (ClassNode node : sourceLineage(sourceClass)) for (MethodNode method : node.methods) {
            if (!"<init>".equals(method.name)) continue;
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction.getOpcode() != Opcodes.ANEWARRAY) continue;
                Integer count = intConstant(previousReal(instruction.getPrevious()));
                if (count != null && count > 0 && count <= 16) values.add(count);
            }
        }
        return values.size() == 1 ? values.iterator().next() : 0;
    }

    private List<ClassNode> sourceLineage(String sourceClass) {
        List<ClassNode> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        String current = sourceClass;
        while (current != null && seen.add(current)) {
            ClassNode node = classes.get(current);
            if (node == null) break;
            result.add(node);
            current = node.superName;
        }
        return result;
    }

    private static AbstractInsnNode previousReal(AbstractInsnNode instruction) {
        AbstractInsnNode current = instruction;
        while (current != null && current.getOpcode() < 0) current = current.getPrevious();
        return current;
    }

    private static Number numberConstant(AbstractInsnNode instruction) {
        if (instruction instanceof LdcInsnNode ldc && ldc.cst instanceof Number number) return number;
        Integer integer = intConstant(instruction);
        return integer;
    }

    private static Integer intConstant(AbstractInsnNode instruction) {
        if (instruction instanceof InsnNode insn) return switch (insn.getOpcode()) {
            case Opcodes.ICONST_M1 -> -1;
            case Opcodes.ICONST_0 -> 0;
            case Opcodes.ICONST_1 -> 1;
            case Opcodes.ICONST_2 -> 2;
            case Opcodes.ICONST_3 -> 3;
            case Opcodes.ICONST_4 -> 4;
            case Opcodes.ICONST_5 -> 5;
            default -> null;
        };
        if (instruction instanceof IntInsnNode value) return value.operand;
        if (instruction instanceof LdcInsnNode ldc && ldc.cst instanceof Integer value) return value;
        return null;
    }

    private void load(Path jarPath) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")
                        || entry.getName().equals("module-info.class")) continue;
                try (InputStream input = jar.getInputStream(entry)) {
                    ClassNode node = new ClassNode(Opcodes.ASM9);
                    new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    classes.put(node.name, node);
                } catch (RuntimeException malformed) {
                    diagnostics.add("Unreadable combat-item class " + entry.getName() + ": "
                            + malformed.getClass().getSimpleName());
                }
            }
        }
    }
}
