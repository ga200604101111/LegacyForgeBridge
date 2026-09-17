package dev.yinghuang.legacyforgebridge.convert;

import java.util.Map;
import java.util.OptionalInt;

/**
 * Bounded Minecraft 1.7.10 platform facts for effective {@code Block#getRenderType()} values.
 *
 * <p>This table is intentionally tiny. It is used only when every source-owned class in a
 * registered block lineage omits {@code getRenderType()/func_149645_b}; a source override always
 * wins and unknown external bases remain fail-closed.</p>
 */
public final class LegacyBlockRenderType1710 {
    private static final Map<String,Integer> EFFECTIVE = Map.of(
            "net/minecraft/block/Block", 0,
            "net/minecraft/block/BlockContainer", 0,
            "net/minecraft/block/BlockBush", 1,
            "net/minecraft/block/BlockCactus", 13,
            "net/minecraft/block/BlockDoublePlant", 40
    );

    private LegacyBlockRenderType1710() { }

    public static OptionalInt effectiveRenderType(String internalName) {
        if (internalName == null || internalName.isBlank()) return OptionalInt.empty();
        Integer value = EFFECTIVE.get(internalName);
        return value == null ? OptionalInt.empty() : OptionalInt.of(value);
    }
}
