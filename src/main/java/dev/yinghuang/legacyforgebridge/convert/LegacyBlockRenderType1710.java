package dev.yinghuang.legacyforgebridge.convert;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

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
            "net/minecraft/block/BlockLiquid", 4,
            "net/minecraft/block/BlockBush", 1,
            "net/minecraft/block/BlockCactus", 13,
            "net/minecraft/block/BlockDoublePlant", 40
    );
    // Minecraft 1.7.10 RenderBlocks.renderItemIn3d. Custom Forge render IDs are deliberately
    // excluded here and are resolved from ISimpleBlockRenderingHandler source bytecode instead.
    private static final Set<Integer> INVENTORY_3D = Set.of(
            0, 10, 11, 13, 16, 21, 22, 26, 27, 31, 32, 34, 35, 39
    );

    private LegacyBlockRenderType1710() { }

    public static OptionalInt effectiveRenderType(String internalName) {
        if (internalName == null || internalName.isBlank()) return OptionalInt.empty();
        Integer value = EFFECTIVE.get(internalName);
        return value == null ? OptionalInt.empty() : OptionalInt.of(value);
    }

    public static Optional<Boolean> renderItemIn3d(int renderType) {
        if (renderType < -1 || renderType > 40) return Optional.empty();
        return Optional.of(INVENTORY_3D.contains(renderType));
    }
}
