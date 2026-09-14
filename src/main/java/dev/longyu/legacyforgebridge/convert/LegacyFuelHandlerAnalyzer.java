package dev.longyu.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
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
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Non-executing extractor for ordinary Forge 1.7.x {@code IFuelHandler#getBurnTime(ItemStack)}.
 *
 * <p>The admitted language is deliberately narrow: a registered handler may return a positive
 * constant when {@code stack.getItem() == item}, optionally conjoined with
 * {@code stack.getItemDamage() == constant}. Block items expressed through
 * {@code Item.getItemFromBlock(block)} are also accepted. The method must end in a zero default.
 * Unknown calls, branch shapes, arithmetic, mutable state or non-constant burn values are rejected
 * rather than interpreted.</p>
 */
public final class LegacyFuelHandlerAnalyzer {
    private static final String ITEM_STACK = "net/minecraft/item/ItemStack";
    private static final String ITEM = "net/minecraft/item/Item";
    private static final String GET_ITEM_DESC = "()Lnet/minecraft/item/Item;";
    private static final String GET_DAMAGE_DESC = "()I";
    private static final String ITEM_FROM_BLOCK_DESC = "(Lnet/minecraft/block/Block;)Lnet/minecraft/item/Item;";
    private static final String BURN_DESC = "(Lnet/minecraft/item/ItemStack;)I";

    public record FuelRule(
            LegacyRecipeAnalyzer.RegistryValue registry,
            Integer metadata,
            int burnTicks,
            String sourceOwner,
            String sourceMethod
    ) {
        public FuelRule {
            if (registry == null) throw new IllegalArgumentException("registry");
            if (metadata != null && metadata < 0) throw new IllegalArgumentException("metadata");
            if (burnTicks <= 0) throw new IllegalArgumentException("burnTicks");
        }

        public boolean anyMetadata() {
            return metadata == null;
        }
    }

    public record Analysis(List<FuelRule> rules, List<String> diagnostics) {
        public Analysis {
            rules = List.copyOf(rules);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    public Analysis analyze(Path jarPath) throws IOException {
        LegacyRecipeAnalyzer.Analysis recipes = new LegacyRecipeAnalyzer().analyze(jarPath);
        LegacyRegistryAnalyzer.Analysis registry = new LegacyRegistryAnalyzer().analyze(jarPath);
        Map<String, LegacyRecipeAnalyzer.RegistryValue> fields = fieldBindings(registry);
        Map<String, ClassNode> classes = load(jarPath);
        LegacyRecipeValueResolver resolver = new LegacyRecipeValueResolver();

        LinkedHashSet<FuelRule> rules = new LinkedHashSet<>();
        List<String> diagnostics = new ArrayList<>();
        for (LegacyRecipeAnalyzer.Registration source : recipes.of(LegacyRecipeAnalyzer.Kind.FUEL_HANDLER)) {
            LegacyRecipeAnalyzer.Registration registration = resolver.resolve(source);
            if (registration.arguments().size() != 1
                    || !(registration.arguments().getFirst() instanceof LegacyRecipeAnalyzer.ObjectValue handler)) {
                diagnostics.add("Fuel handler registration is not a concrete constructed object at "
                        + source.sourceOwner() + "." + source.sourceMethod() + ".");
                continue;
            }
            ClassNode owner = classes.get(handler.internalName());
            if (owner == null) {
                diagnostics.add("Registered fuel handler class is missing from source JAR: " + handler.internalName());
                continue;
            }
            List<MethodNode> candidates = owner.methods.stream()
                    .filter(method -> BURN_DESC.equals(method.desc) && (method.access & Opcodes.ACC_STATIC) == 0)
                    .toList();
            if (candidates.size() != 1) {
                diagnostics.add("Expected exactly one ItemStack->int fuel method in " + owner.name
                        + " but found " + candidates.size() + ".");
                continue;
            }
            MethodNode method = candidates.getFirst();
            ParseResult parsed = parse(owner.name, method, fields);
            if (parsed.error() != null) diagnostics.add(parsed.error());
            else rules.addAll(parsed.rules());
        }
        return new Analysis(List.copyOf(rules), List.copyOf(new LinkedHashSet<>(diagnostics)));
    }

    private static ParseResult parse(
            String owner,
            MethodNode method,
            Map<String, LegacyRecipeAnalyzer.RegistryValue> fields
    ) {
        List<AbstractInsnNode> real = realInstructions(method);
        if (real.size() < 2 || real.getLast().getOpcode() != Opcodes.IRETURN
                || integerConstant(real.get(real.size() - 2)).orElse(Integer.MIN_VALUE) != 0) {
            return ParseResult.error("Fuel method " + owner + "." + method.name + method.desc
                    + " has no terminal constant-zero default; refusing partial interpretation.");
        }

        List<FuelRule> output = new ArrayList<>();
        int previousReturnRaw = -1;
        for (int realIndex = 0; realIndex < real.size(); realIndex++) {
            AbstractInsnNode instruction = real.get(realIndex);
            if (instruction.getOpcode() != Opcodes.IRETURN) continue;
            int rawReturn = method.instructions.indexOf(instruction);
            Optional<Integer> returned = realIndex == 0 ? Optional.empty() : integerConstant(real.get(realIndex - 1));
            if (returned.isEmpty()) {
                return ParseResult.error("Fuel method " + owner + "." + method.name
                        + " contains a non-constant int return.");
            }
            int burnTicks = returned.get();
            if (burnTicks < 0) {
                return ParseResult.error("Fuel method " + owner + "." + method.name
                        + " contains a negative burn-time return.");
            }
            if (burnTicks == 0) {
                previousReturnRaw = rawReturn;
                continue;
            }

            LegacyRecipeAnalyzer.RegistryValue itemGuard = null;
            Integer metadataGuard = null;
            for (AbstractInsnNode candidate : real) {
                int raw = method.instructions.indexOf(candidate);
                if (raw <= previousReturnRaw || raw >= rawReturn) continue;
                if (candidate instanceof JumpInsnNode jump) {
                    int target = method.instructions.indexOf(jump.label);
                    if (target <= rawReturn) {
                        return ParseResult.error("Fuel method " + owner + "." + method.name
                                + " contains a branch that does not guard a single constant return.");
                    }
                    if (jump.getOpcode() == Opcodes.IF_ACMPNE) {
                        Optional<LegacyRecipeAnalyzer.RegistryValue> registry = itemEqualityBefore(jump, fields);
                        if (registry.isEmpty() || itemGuard != null) {
                            return ParseResult.error("Fuel method " + owner + "." + method.name
                                    + " contains an unsupported item guard.");
                        }
                        itemGuard = registry.get();
                    } else if (jump.getOpcode() == Opcodes.IF_ICMPNE) {
                        Optional<Integer> metadata = metadataEqualityBefore(jump);
                        if (metadata.isEmpty() || metadataGuard != null) {
                            return ParseResult.error("Fuel method " + owner + "." + method.name
                                    + " contains an unsupported metadata guard.");
                        }
                        metadataGuard = metadata.get();
                    } else {
                        return ParseResult.error("Fuel method " + owner + "." + method.name
                                + " contains unsupported conditional opcode " + jump.getOpcode() + ".");
                    }
                } else if (!allowedNonBranch(candidate)) {
                    return ParseResult.error("Fuel method " + owner + "." + method.name
                            + " contains unsupported instruction opcode " + candidate.getOpcode() + ".");
                }
            }
            if (itemGuard == null) {
                return ParseResult.error("Fuel method " + owner + "." + method.name
                        + " returns positive burn time without a proven item equality guard.");
            }
            output.add(new FuelRule(itemGuard, metadataGuard, burnTicks, owner, method.name));
            previousReturnRaw = rawReturn;
        }
        return new ParseResult(List.copyOf(output), null);
    }

    private static boolean allowedNonBranch(AbstractInsnNode instruction) {
        int opcode = instruction.getOpcode();
        if (opcode == Opcodes.IRETURN || opcode == Opcodes.ALOAD || opcode == Opcodes.GETSTATIC) return true;
        if (integerConstant(instruction).isPresent()) return true;
        if (instruction instanceof MethodInsnNode call) {
            return isStackGetItem(call) || isStackGetDamage(call) || isItemFromBlock(call);
        }
        return false;
    }

    private static Optional<LegacyRecipeAnalyzer.RegistryValue> itemEqualityBefore(
            JumpInsnNode jump,
            Map<String, LegacyRecipeAnalyzer.RegistryValue> fields
    ) {
        AbstractInsnNode one = previousReal(jump);
        AbstractInsnNode two = previousReal(one);
        AbstractInsnNode three = previousReal(two);
        AbstractInsnNode four = previousReal(three);

        if (one instanceof FieldInsnNode field
                && field.getOpcode() == Opcodes.GETSTATIC
                && two instanceof MethodInsnNode getItem
                && isStackGetItem(getItem)
                && isStackLocal(three)) {
            return resolveField(field, fields).filter(value -> value.kind() == LegacyRegistryAnalyzer.Kind.ITEM);
        }

        if (one instanceof MethodInsnNode fromBlock && isItemFromBlock(fromBlock)
                && two instanceof FieldInsnNode field
                && field.getOpcode() == Opcodes.GETSTATIC
                && three instanceof MethodInsnNode getItem
                && isStackGetItem(getItem)
                && isStackLocal(four)) {
            return resolveField(field, fields).filter(value -> value.kind() == LegacyRegistryAnalyzer.Kind.BLOCK);
        }
        return Optional.empty();
    }

    private static Optional<Integer> metadataEqualityBefore(JumpInsnNode jump) {
        AbstractInsnNode one = previousReal(jump);
        AbstractInsnNode two = previousReal(one);
        AbstractInsnNode three = previousReal(two);
        Optional<Integer> constant = integerConstant(one);
        if (constant.isPresent()
                && two instanceof MethodInsnNode damage
                && isStackGetDamage(damage)
                && isStackLocal(three)) {
            return constant;
        }
        return Optional.empty();
    }

    private static Optional<LegacyRecipeAnalyzer.RegistryValue> resolveField(
            FieldInsnNode field,
            Map<String, LegacyRecipeAnalyzer.RegistryValue> fields
    ) {
        LegacyRecipeAnalyzer.RegistryValue mod = fields.get(field.owner + "." + field.name + field.desc);
        if (mod != null) return Optional.of(mod);
        return LegacyVanillaRegistry1710.resolve(field.owner, field.name)
                .map(entry -> new LegacyRecipeAnalyzer.RegistryValue(
                        entry.kind(), entry.registryName(), "minecraft", field.owner, field.name));
    }

    private static Map<String, LegacyRecipeAnalyzer.RegistryValue> fieldBindings(LegacyRegistryAnalyzer.Analysis analysis) {
        Map<String, LegacyRecipeAnalyzer.RegistryValue> output = new LinkedHashMap<>();
        for (LegacyRegistryAnalyzer.FieldBinding binding : analysis.fieldBindings()) {
            output.put(binding.owner() + "." + binding.name() + binding.descriptor(),
                    new LegacyRecipeAnalyzer.RegistryValue(
                            binding.kind(), binding.registryName(), binding.legacyNamespace(),
                            binding.owner(), binding.name()));
        }
        return output;
    }

    private static Map<String, ClassNode> load(Path jarPath) throws IOException {
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
                }
            }
        }
        return output;
    }

    private static List<AbstractInsnNode> realInstructions(MethodNode method) {
        List<AbstractInsnNode> output = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction.getOpcode() >= 0) output.add(instruction);
        }
        return output;
    }

    private static boolean isStackLocal(AbstractInsnNode instruction) {
        return instruction instanceof VarInsnNode variable
                && variable.getOpcode() == Opcodes.ALOAD
                && variable.var == 1;
    }

    private static boolean isStackGetItem(MethodInsnNode call) {
        return call.getOpcode() == Opcodes.INVOKEVIRTUAL
                && ITEM_STACK.equals(call.owner)
                && GET_ITEM_DESC.equals(call.desc);
    }

    private static boolean isStackGetDamage(MethodInsnNode call) {
        return call.getOpcode() == Opcodes.INVOKEVIRTUAL
                && ITEM_STACK.equals(call.owner)
                && GET_DAMAGE_DESC.equals(call.desc);
    }

    private static boolean isItemFromBlock(MethodInsnNode call) {
        return call.getOpcode() == Opcodes.INVOKESTATIC
                && ITEM.equals(call.owner)
                && ITEM_FROM_BLOCK_DESC.equals(call.desc);
    }

    private static AbstractInsnNode previousReal(AbstractInsnNode instruction) {
        if (instruction == null) return null;
        AbstractInsnNode previous = instruction.getPrevious();
        while (previous != null && previous.getOpcode() < 0) previous = previous.getPrevious();
        return previous;
    }

    private static Optional<Integer> integerConstant(AbstractInsnNode instruction) {
        if (instruction == null) return Optional.empty();
        return switch (instruction.getOpcode()) {
            case Opcodes.ICONST_M1 -> Optional.of(-1);
            case Opcodes.ICONST_0 -> Optional.of(0);
            case Opcodes.ICONST_1 -> Optional.of(1);
            case Opcodes.ICONST_2 -> Optional.of(2);
            case Opcodes.ICONST_3 -> Optional.of(3);
            case Opcodes.ICONST_4 -> Optional.of(4);
            case Opcodes.ICONST_5 -> Optional.of(5);
            case Opcodes.BIPUSH, Opcodes.SIPUSH -> Optional.of(((IntInsnNode) instruction).operand);
            case Opcodes.LDC -> ((LdcInsnNode) instruction).cst instanceof Integer value
                    ? Optional.of(value) : Optional.empty();
            default -> Optional.empty();
        };
    }

    private record ParseResult(List<FuelRule> rules, String error) {
        static ParseResult error(String message) {
            return new ParseResult(List.of(), message);
        }
    }
}
