package dev.yinghuang.legacyforgebridge.convert;

import java.util.List;
import java.util.Optional;

/**
 * Reduces source-derived recipe values to the narrow ItemStack state needed by recipe conversion.
 *
 * <p>This deliberately does not emulate arbitrary legacy constructors. Only ordinary 1.7.10
 * ItemStack constructor shapes proven by the analyzer are accepted. Anything else stays
 * unresolved so later conversion can fail closed instead of inventing recipe semantics.</p>
 */
public final class LegacyRecipeStackResolver {
    public static final int WILDCARD_META = 32_767;
    private static final String ITEM_STACK = "net/minecraft/item/ItemStack";

    private LegacyRecipeStackResolver() { }

    public record StackSpec(LegacyRecipeAnalyzer.RegistryValue registry, int count, int meta) {
        public StackSpec {
            if (registry == null) throw new IllegalArgumentException("registry");
            if (count <= 0) throw new IllegalArgumentException("Legacy recipe stack count must be positive: " + count);
            if (meta < 0 || meta > WILDCARD_META) {
                throw new IllegalArgumentException("Legacy recipe metadata outside supported 1.7.10 range: " + meta);
            }
        }

        public boolean wildcardMeta() {
            return meta == WILDCARD_META;
        }
    }

    public static Optional<StackSpec> resolve(LegacyRecipeAnalyzer.Value value) {
        if (value instanceof LegacyRecipeAnalyzer.RegistryValue registry) {
            return Optional.of(new StackSpec(registry, 1, 0));
        }
        if (!(value instanceof LegacyRecipeAnalyzer.ObjectValue object)
                || !ITEM_STACK.equals(object.internalName())
                || object.constructorDescriptor() == null) {
            return Optional.empty();
        }

        List<LegacyRecipeAnalyzer.Value> args = object.constructorArguments();
        return switch (object.constructorDescriptor()) {
            case "(Lnet/minecraft/item/Item;)V", "(Lnet/minecraft/block/Block;)V" ->
                    stack(args, 1, 0, 1);
            case "(Lnet/minecraft/item/Item;I)V" ->
                    stack(args, number(args, 1), 0, 2);
            case "(Lnet/minecraft/item/Item;II)V", "(Lnet/minecraft/block/Block;II)V" ->
                    stack(args, number(args, 1), number(args, 2), 3);
            default -> Optional.empty();
        };
    }

    private static Optional<StackSpec> stack(
            List<LegacyRecipeAnalyzer.Value> args,
            int count,
            int meta,
            int expectedArgs
    ) {
        if (args.size() != expectedArgs || count <= 0 || meta < 0 || meta > WILDCARD_META) {
            return Optional.empty();
        }
        if (!(args.getFirst() instanceof LegacyRecipeAnalyzer.RegistryValue registry)) {
            return Optional.empty();
        }
        return Optional.of(new StackSpec(registry, count, meta));
    }

    private static int number(List<LegacyRecipeAnalyzer.Value> args, int index) {
        if (index >= args.size() || !(args.get(index) instanceof LegacyRecipeAnalyzer.NumberValue number)) {
            return -1;
        }
        Number value = number.value();
        double asDouble = value.doubleValue();
        int asInt = value.intValue();
        return Double.isFinite(asDouble) && asDouble == asInt ? asInt : -1;
    }
}
