package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

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
 * Non-executing proof for bounded legacy melee/bow constants and their native item presentation.
 *
 * <p>Only source-proven values or exact vanilla 1.7 tool-family semantics are exported. 1.7 did
 * not expose attack speed; modern cadence remains a presentation/runtime adaptation in the pass.</p>
 */
public final class LegacyCombatItemAnalyzer {
    private static final Set<String> MAX_DAMAGE = Set.of("setMaxDamage", "func_77656_e");
    private static final Set<String> ATTRIBUTE_METHODS = Set.of("getItemAttributeModifiers", "func_111205_h", "getAttributeModifiers");
    private static final String ATTRIBUTE_MODIFIER = "net/minecraft/entity/ai/attributes/AttributeModifier";

    public enum Kind { SWORD, TOOL, BOW }

    public record Rule(String registryName, String sourceClass, Kind kind, int durability,
                       float attackDamage, int useDuration, String pullTexturePrefix, int pullStages,
                       List<Integer> pullStageMinTicks) {
        public Rule {
            pullStageMinTicks = pullStageMinTicks == null ? List.of() : List.copyOf(pullStageMinTicks);
            if (registryName == null || registryName.isBlank() || sourceClass == null || sourceClass.isBlank()
                    || kind == null || durability < 0 || !Float.isFinite(attackDamage) || attackDamage < 0F
                    || useDuration < 0 || pullStages < 0) {
                throw new IllegalArgumentException("Invalid combat-item proof");
            }
            if ((kind == Kind.SWORD || kind == Kind.TOOL) && attackDamage <= 0F)
                throw new IllegalArgumentException("Melee damage was not proven");
            if (kind == Kind.BOW && (useDuration <= 0 || pullTexturePrefix == null || pullTexturePrefix.isBlank()
                    || pullStages <= 0 || pullStageMinTicks.size() != pullStages))
                throw new IllegalArgumentException("Bow use/pull presentation was not proven");
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
            Float attributeDamage = uniqueAttackDamage(item);
            Float directDamage = uniqueDirectEntityDamage(source);
            Float inheritedToolDamage = inheritedVanillaToolDamage(item, classified);
            boolean toolFamily = Set.of("pickaxe","axe","shovel","tool").contains(classified);
            Kind kind = "bow".equals(classified) ? Kind.BOW
                    : toolFamily && (attributeDamage != null || directDamage != null || inheritedToolDamage != null) ? Kind.TOOL
                    : ("sword".equals(classified) || attributeDamage != null || directDamage != null ? Kind.SWORD : null);
            if (kind == null) continue;
            int durability = uniqueDurability(item);
            if (kind == Kind.SWORD || kind == Kind.TOOL) {
                Float damage = attributeDamage != null ? attributeDamage
                        : directDamage != null ? directDamage : inheritedToolDamage;
                if (damage == null || damage <= 0F) {
                    skipped.add(new Skipped(item.registryName(), source,
                            "No unique source or exact vanilla-family attack-damage proof was recovered."));
                    continue;
                }
                rules.add(new Rule(item.registryName(), source, kind, Math.max(0, durability), damage,
                        0, null, 0, List.of()));
            } else {
                String prefix = uniquePullTexturePrefix(source);
                int stages = uniquePullStageCount(source);
                List<Integer> stageMinTicks = uniquePullStageMinTicks(source, stages);
                if (prefix == null || stages <= 0 || stageMinTicks.size() != stages) {
                    skipped.add(new Skipped(item.registryName(), source,
                            "No unique source bow pull texture/threshold sequence was proven."));
                    continue;
                }
                rules.add(new Rule(item.registryName(), source, kind, Math.max(0, durability), 0F,
                        72_000, prefix, stages, stageMinTicks));
            }
        }
        return new Analysis(rules, skipped, List.copyOf(new LinkedHashSet<>(diagnostics)));
    }

    /**
     * Recover the inherited 1.7 ItemTool attack modifier only when the source constructor uniquely
     * proves a vanilla tool family and ToolMaterial. These are Minecraft 1.7.10 constants, not
     * guesses from registry names or textures.
     */
    private Float inheritedVanillaToolDamage(LegacyRegistryAnalyzer.Registration registration,String classified) {
        String vanillaBase=switch(classified){
            case "pickaxe"->"net/minecraft/item/ItemPickaxe";
            case "axe"->"net/minecraft/item/ItemAxe";
            case "shovel"->"net/minecraft/item/ItemSpade";
            default->null;
        };
        float base=switch(classified){case "pickaxe"->2F;case "axe"->3F;case "shovel"->1F;default->Float.NaN;};
        if(vanillaBase==null||!Float.isFinite(base))return null;
        String material=uniqueVanillaToolMaterial(registration,vanillaBase);
        if(material==null)return null;
        Float bonus=switch(material){case "WOOD","GOLD"->0F;case "STONE"->1F;case "IRON"->2F;case "EMERALD"->3F;default->null;};
        return bonus==null?null:base+bonus;
    }

    private String uniqueVanillaToolMaterial(LegacyRegistryAnalyzer.Registration registration,String vanillaBase) {
        LinkedHashSet<String> materials=new LinkedHashSet<>();
        for(ClassNode node:sourceLineage(registration.implementationClass()))for(MethodNode method:node.methods){
            if(!"<init>".equals(method.name))continue;
            for(AbstractInsnNode instruction:method.instructions){
                if(!(instruction instanceof MethodInsnNode call)||call.getOpcode()!=Opcodes.INVOKESPECIAL
                        ||!"<init>".equals(call.name)||!vanillaBase.equals(call.owner)
                        ||!"(Lnet/minecraft/item/Item$ToolMaterial;)V".equals(call.desc))continue;
                String material=toolMaterialSource(registration,node,method,previousReal(instruction.getPrevious()));
                if(material!=null)materials.add(material);
            }
        }
        return materials.size()==1?materials.getFirst():null;
    }

    private String toolMaterialSource(LegacyRegistryAnalyzer.Registration registration,ClassNode owner,
                                      MethodNode constructor,AbstractInsnNode source) {
        if(source instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETSTATIC
                &&"net/minecraft/item/Item$ToolMaterial".equals(field.owner)
                &&"Lnet/minecraft/item/Item$ToolMaterial;".equals(field.desc))return field.name;
        if(!(source instanceof VarInsnNode load)||load.getOpcode()!=Opcodes.ALOAD
                ||!owner.name.equals(registration.implementationClass())
                ||registration.constructorDescriptor()==null
                ||!registration.constructorDescriptor().equals(constructor.desc))return null;
        int argument=constructorArgumentIndex(constructor.desc,load.var);
        if(argument<0||argument>=registration.constructorArguments().size())return null;
        Object value=registration.constructorArguments().get(argument).value();
        if(value instanceof LegacyRegistryAnalyzer.StaticFieldReference field
                &&"net/minecraft/item/Item$ToolMaterial".equals(field.owner())
                &&"Lnet/minecraft/item/Item$ToolMaterial;".equals(field.descriptor()))return field.name();
        return null;
    }

    private int uniqueDurability(LegacyRegistryAnalyzer.Registration registration) {
        Set<Integer> values = new LinkedHashSet<>();
        String sourceClass = registration.implementationClass();
        for (ClassNode node : sourceLineage(sourceClass)) for (MethodNode method : node.methods) {
            if (!"<init>".equals(method.name)) continue;
            for (AbstractInsnNode instruction : method.instructions) {
                if (!(instruction instanceof MethodInsnNode call)
                        || !MAX_DAMAGE.contains(call.name)
                        || !"(I)Lnet/minecraft/item/Item;".equals(call.desc)) continue;
                AbstractInsnNode valueSource = previousReal(instruction.getPrevious());
                Integer value = intConstant(valueSource);
                if (value == null) value = registrationIntValue(registration, node, method, valueSource);
                if (value != null && value >= 0) values.add(value);
            }
        }
        return values.size() == 1 ? values.iterator().next() : 0;
    }

    private Float uniqueAttackDamage(LegacyRegistryAnalyzer.Registration registration) {
        Set<Float> values = new LinkedHashSet<>();
        String sourceClass = registration.implementationClass();
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
                Float amount = provenNumericValue(registration, cursor);
                if (amount != null && Float.isFinite(amount) && amount > 0F) values.add(amount);
            }
        }
        return values.size() == 1 ? values.iterator().next() : null;
    }

    /**
     * Resolve an AttributeModifier numeric operand without executing legacy code. Besides direct
     * constants, this admits the common source shape {@code this.field -> numeric conversion} only
     * when the exact registered constructor proves that field came from one concrete numeric
     * constructor argument. Field names are deliberately irrelevant.
     */
    private Float provenNumericValue(LegacyRegistryAnalyzer.Registration registration, AbstractInsnNode source) {
        AbstractInsnNode cursor = source;
        while (cursor != null && Set.of(
                Opcodes.I2D, Opcodes.F2D, Opcodes.L2D, Opcodes.I2F, Opcodes.L2F,
                Opcodes.D2F, Opcodes.I2L, Opcodes.F2L, Opcodes.D2L
        ).contains(cursor.getOpcode())) cursor = previousReal(cursor.getPrevious());
        Number direct = numberConstant(cursor);
        if (direct != null) return direct.floatValue();
        if (!(cursor instanceof FieldInsnNode field) || field.getOpcode() != Opcodes.GETFIELD) return null;
        AbstractInsnNode receiver = previousReal(cursor.getPrevious());
        if (!(receiver instanceof VarInsnNode load) || load.getOpcode() != Opcodes.ALOAD || load.var != 0) return null;
        return constructorFieldValue(registration, field);
    }

    private Float constructorFieldValue(LegacyRegistryAnalyzer.Registration registration, FieldInsnNode field) {
        if (!field.owner.equals(registration.implementationClass())) return null;
        ClassNode owner = classes.get(field.owner);
        if (owner == null || registration.constructorDescriptor() == null) return null;
        MethodNode constructor = owner.methods.stream()
                .filter(method -> "<init>".equals(method.name) && registration.constructorDescriptor().equals(method.desc))
                .findFirst().orElse(null);
        if (constructor == null) return null;
        Set<Float> values = new LinkedHashSet<>();
        for (AbstractInsnNode instruction : constructor.instructions) {
            if (!(instruction instanceof FieldInsnNode write) || write.getOpcode() != Opcodes.PUTFIELD
                    || !write.owner.equals(field.owner) || !write.name.equals(field.name)
                    || !write.desc.equals(field.desc)) continue;
            AbstractInsnNode valueSource = previousReal(instruction.getPrevious());
            Float value = numericConstructorValue(registration, constructor, valueSource);
            if (value != null && Float.isFinite(value)) values.add(value);
        }
        return values.size() == 1 ? values.iterator().next() : null;
    }

    private Integer registrationIntValue(LegacyRegistryAnalyzer.Registration registration, ClassNode owner,
                                         MethodNode constructor, AbstractInsnNode source) {
        if (!owner.name.equals(registration.implementationClass())
                || registration.constructorDescriptor() == null
                || !registration.constructorDescriptor().equals(constructor.desc)) return null;
        Float value = numericConstructorValue(registration, constructor, source);
        if (value == null || value != Math.rint(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) return null;
        return value.intValue();
    }

    private Float numericConstructorValue(LegacyRegistryAnalyzer.Registration registration, MethodNode constructor,
                                          AbstractInsnNode source) {
        Number direct = numberConstant(source);
        if (direct != null) return direct.floatValue();
        AbstractInsnNode cursor = source;
        while (cursor != null && Set.of(
                Opcodes.I2D, Opcodes.F2D, Opcodes.L2D, Opcodes.I2F, Opcodes.L2F,
                Opcodes.D2F, Opcodes.I2L, Opcodes.F2L, Opcodes.D2L
        ).contains(cursor.getOpcode())) cursor = previousReal(cursor.getPrevious());
        if (!(cursor instanceof VarInsnNode load)) return null;
        int argument = constructorArgumentIndex(constructor.desc, load.var);
        if (argument < 0 || argument >= registration.constructorArguments().size()) return null;
        Object value = registration.constructorArguments().get(argument).value();
        return value instanceof Number number ? number.floatValue() : null;
    }

    private static int constructorArgumentIndex(String descriptor, int localIndex) {
        Type[] arguments = Type.getArgumentTypes(descriptor);
        int local = 1;
        for (int i = 0; i < arguments.length; i++) {
            if (local == localIndex) return i;
            local += arguments[i].getSize();
        }
        return -1;
    }

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
                        || opcode == Opcodes.IFNULL || opcode == Opcodes.IFNONNULL
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

    private List<Integer> uniquePullStageMinTicks(String sourceClass, int stages) {
        if (stages <= 0) return List.of();
        List<List<Integer>> candidates = new ArrayList<>();
        for (ClassNode node : sourceLineage(sourceClass)) for (MethodNode method : node.methods) {
            if (!method.desc.endsWith(")Lnet/minecraft/util/IIcon;")) continue;
            List<AbstractInsnNode> code = new ArrayList<>();
            for (AbstractInsnNode instruction : method.instructions) if (instruction.getOpcode() >= 0) code.add(instruction);
            Map<Integer,Integer> minimumByStage = new LinkedHashMap<>();
            Integer elapsedLocal = null;
            boolean invalid = false;
            for (int i = 0; i < code.size(); i++) {
                if (!(code.get(i) instanceof MethodInsnNode call)
                        || !call.owner.equals(node.name)
                        || !call.desc.equals("(I)Lnet/minecraft/util/IIcon;")) continue;
                Integer stage = i > 0 ? intConstant(code.get(i - 1)) : null;
                if (stage == null || stage < 0 || stage >= stages) { invalid = true; break; }
                Threshold threshold = precedingFallthroughThreshold(code, i);
                if (threshold == null || threshold.minimumTicks() <= 0) { invalid = true; break; }
                if (elapsedLocal == null) elapsedLocal = threshold.elapsedLocal();
                else if (!elapsedLocal.equals(threshold.elapsedLocal())) { invalid = true; break; }
                Integer prior = minimumByStage.putIfAbsent(stage, threshold.minimumTicks());
                if (prior != null && !prior.equals(threshold.minimumTicks())) { invalid = true; break; }
            }
            if (invalid || minimumByStage.size() != stages) continue;
            List<Integer> ordered = new ArrayList<>(stages);
            int previous = 0;
            for (int stage = 0; stage < stages; stage++) {
                Integer minimum = minimumByStage.get(stage);
                if (minimum == null || minimum <= previous) { invalid = true; break; }
                ordered.add(minimum); previous = minimum;
            }
            if (!invalid) candidates.add(List.copyOf(ordered));
        }
        if (candidates.isEmpty()) return List.of();
        List<Integer> first = candidates.getFirst();
        for (List<Integer> candidate : candidates) if (!candidate.equals(first)) return List.of();
        return first;
    }

    private record Threshold(int elapsedLocal, int minimumTicks) { }

    private static Threshold precedingFallthroughThreshold(List<AbstractInsnNode> code, int callIndex) {
        int start = Math.max(0, callIndex - 12);
        for (int i = callIndex - 1; i >= start; i--) {
            AbstractInsnNode instruction = code.get(i);
            if (!(instruction instanceof JumpInsnNode jump)) continue;
            int opcode = jump.getOpcode();
            if (opcode == Opcodes.IFLE || opcode == Opcodes.IFLT) {
                if (i < 1 || !(code.get(i - 1) instanceof VarInsnNode load) || load.getOpcode() != Opcodes.ILOAD) continue;
                return new Threshold(load.var, opcode == Opcodes.IFLE ? 1 : 0);
            }
            if (opcode != Opcodes.IF_ICMPLT && opcode != Opcodes.IF_ICMPLE) continue;
            if (i < 2 || !(code.get(i - 2) instanceof VarInsnNode load) || load.getOpcode() != Opcodes.ILOAD) continue;
            Integer bound = intConstant(code.get(i - 1));
            if (bound == null || bound < 0) continue;
            int minimum = opcode == Opcodes.IF_ICMPLT ? bound : bound + 1;
            return new Threshold(load.var, minimum);
        }
        return null;
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
