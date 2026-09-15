package dev.longyu.legacyforgebridge.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Extracts the source-owned recipe registrations feeding an admitted single-input processor. */
public final class LegacySingleInputProcessorRecipeAnalyzer {
    private static final String STACK = "Lnet/minecraft/item/ItemStack;";
    private static final String ITEM = "Lnet/minecraft/item/Item;";
    private static final String BLOCK = "Lnet/minecraft/block/Block;";

    public sealed interface Input permits StackInput, OreInput { }
    public record StackInput(LegacyRecipeAnalyzer.Value stack) implements Input { }
    public record OreInput(String oreName, int count) implements Input { }

    public record Recipe(Input input, LegacyRecipeAnalyzer.Value output,
                         LegacyRecipeAnalyzer.Value bonus, float bonusChance,
                         String sourceOwner, String sourceMethod, String sourceDescriptor) { }
    public record Analysis(List<Recipe> recipes, List<String> diagnostics) {
        public Analysis { recipes = List.copyOf(recipes); diagnostics = List.copyOf(diagnostics); }
    }

    private static final Set<String> SUPPORTED_DESCRIPTORS = Set.of(
            "(" + STACK + BLOCK + ")V",
            "(" + STACK + ITEM + ")V",
            "(" + STACK + STACK + BLOCK + "F)V",
            "(" + STACK + STACK + ITEM + "F)V",
            "(" + STACK + STACK + ")V",
            "(" + STACK + STACK + STACK + "F)V",
            "(" + STACK + STACK + "Ljava/lang/String;IF)V"
    );

    public Analysis analyze(Path jarPath, LegacySingleInputProcessorAnalyzer.Rule machine) throws IOException {
        if (machine == null) throw new IllegalArgumentException("machine");
        Set<String> registrationNames = registrationMethodNames(jarPath, machine.recipeManagerOwner());
        if (registrationNames.isEmpty()) {
            return new Analysis(List.of(), List.of("No supported source-owned processor registration API methods were proven."));
        }
        LegacyReachableCallAnalyzer.Analysis calls = new LegacyReachableCallAnalyzer().analyze(
                jarPath, machine.recipeManagerOwner(), registrationNames);
        List<String> diagnostics = new ArrayList<>(calls.diagnostics());
        LinkedHashSet<Recipe> recipes = new LinkedHashSet<>();
        for (LegacyReachableCallAnalyzer.Call call : calls.calls()) {
            Recipe recipe = normalize(call, diagnostics);
            if (recipe != null) recipes.add(recipe);
        }
        return new Analysis(List.copyOf(recipes), List.copyOf(new LinkedHashSet<>(diagnostics)));
    }

    private static Recipe normalize(LegacyReachableCallAnalyzer.Call call, List<String> diagnostics) {
        List<LegacyRecipeAnalyzer.Value> args = call.arguments();
        String desc = call.targetDescriptor();
        try {
            return switch (desc) {
                case "(" + STACK + BLOCK + ")V" -> new Recipe(
                        new StackInput(stack(args.get(1), BLOCK)), args.get(0), LegacyRecipeAnalyzer.NullValue.INSTANCE, 0F,
                        call.sourceOwner(), call.sourceMethod(), call.sourceDescriptor());
                case "(" + STACK + ITEM + ")V" -> new Recipe(
                        new StackInput(stack(args.get(1), ITEM)), args.get(0), LegacyRecipeAnalyzer.NullValue.INSTANCE, 0F,
                        call.sourceOwner(), call.sourceMethod(), call.sourceDescriptor());
                case "(" + STACK + STACK + BLOCK + "F)V" -> new Recipe(
                        new StackInput(stack(args.get(2), BLOCK)), args.get(0), args.get(1), chance(args.get(3)),
                        call.sourceOwner(), call.sourceMethod(), call.sourceDescriptor());
                case "(" + STACK + STACK + ITEM + "F)V" -> new Recipe(
                        new StackInput(stack(args.get(2), ITEM)), args.get(0), args.get(1), chance(args.get(3)),
                        call.sourceOwner(), call.sourceMethod(), call.sourceDescriptor());
                case "(" + STACK + STACK + ")V" -> new Recipe(
                        new StackInput(args.get(1)), args.get(0), LegacyRecipeAnalyzer.NullValue.INSTANCE, 0F,
                        call.sourceOwner(), call.sourceMethod(), call.sourceDescriptor());
                case "(" + STACK + STACK + STACK + "F)V" -> new Recipe(
                        new StackInput(args.get(2)), args.get(0), args.get(1), chance(args.get(3)),
                        call.sourceOwner(), call.sourceMethod(), call.sourceDescriptor());
                case "(" + STACK + STACK + "Ljava/lang/String;IF)V" -> {
                    if (!(args.get(2) instanceof LegacyRecipeAnalyzer.TextValue ore)
                            || !(args.get(3) instanceof LegacyRecipeAnalyzer.NumberValue count)) yield null;
                    int amount = exactInt(count.value());
                    if (amount <= 0) yield null;
                    yield new Recipe(new OreInput(ore.value(), amount), args.get(0), args.get(1), chance(args.get(4)),
                            call.sourceOwner(), call.sourceMethod(), call.sourceDescriptor());
                }
                default -> null;
            };
        } catch (RuntimeException unresolved) {
            diagnostics.add("Processor recipe call could not be normalized without guessing: "
                    + call.sourceOwner() + "." + call.sourceMethod() + " -> " + desc);
            return null;
        }
    }

    private static LegacyRecipeAnalyzer.Value stack(LegacyRecipeAnalyzer.Value registry, String descriptor) {
        return new LegacyRecipeAnalyzer.ObjectValue("net/minecraft/item/ItemStack",
                descriptor.equals(BLOCK) ? "(Lnet/minecraft/block/Block;)V" : "(Lnet/minecraft/item/Item;)V",
                List.of(registry));
    }

    private static float chance(LegacyRecipeAnalyzer.Value value) {
        if (!(value instanceof LegacyRecipeAnalyzer.NumberValue number)) throw new IllegalArgumentException("chance");
        float chance = number.value().floatValue();
        if (!Float.isFinite(chance) || chance < 0F || chance > 1F) throw new IllegalArgumentException("chance");
        return chance;
    }

    private static int exactInt(Number value) {
        double asDouble = value.doubleValue(); int asInt = value.intValue();
        return Double.isFinite(asDouble) && asDouble == asInt ? asInt : -1;
    }

    private static Set<String> registrationMethodNames(Path jarPath, String managerOwner) throws IOException {
        ClassNode manager = null;
        try (JarFile jar = new JarFile(jarPath.toFile(), false)) {
            JarEntry entry = jar.getJarEntry(managerOwner + ".class");
            if (entry == null) return Set.of();
            try (InputStream input = jar.getInputStream(entry)) {
                manager = new ClassNode(Opcodes.ASM9);
                new ClassReader(input).accept(manager, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }
        Set<String> names = new LinkedHashSet<>();
        for (MethodNode method : manager.methods) {
            if ((method.access & Opcodes.ACC_STATIC) == 0 || (method.access & Opcodes.ACC_PUBLIC) == 0
                    || Type.getReturnType(method.desc).getSort() != Type.VOID) continue;
            if (SUPPORTED_DESCRIPTORS.contains(method.desc)) names.add(method.name);
        }
        return Collections.unmodifiableSet(names);
    }
}
