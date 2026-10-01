package dev.yinghuang.legacyforgebridge.behavior;

import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * Projects source-proven legacy BlockLiquid WATER blocks onto vanilla 1.21.11 water FluidStates.
 *
 * <p>The mapping intentionally mirrors vanilla LiquidBlock's own stateCache:</p>
 * <ul>
 *   <li>legacy metadata 0 -> source water</li>
 *   <li>legacy metadata 1..7 -> flowing amount 8-meta</li>
 *   <li>legacy metadata 8..15 -> falling amount 8</li>
 * </ul>
 *
 * <p>This keeps the converted block identity for protocol round-trip while delegating world
 * rendering, transparency, face culling and visible liquid height to the vanilla water renderer.</p>
 */
public final class Rev239VanillaLiquidCompat {
    private Rev239VanillaLiquidCompat() { }

    public static boolean isLiquid(Object block) {
        return Rev233LiquidCompat.isLiquidBlock(block);
    }

    public static FluidState fluidState(ConvertedLegacyBlock block, BlockState state) {
        if (!isLiquid(block)) return null;
        int meta = ConvertedLegacyBlock.legacyMeta(state);
        FlowingFluid water = Fluids.WATER;
        if (meta == 0) return water.getSource(false);
        if (meta >= 8) return water.getFlowing(8, true);
        return water.getFlowing(8 - meta, false);
    }

    static int vanillaStateIndexForTest(int legacyMeta) {
        if (legacyMeta < 0 || legacyMeta > 15) throw new IllegalArgumentException("legacy metadata outside 0..15");
        return Math.min(legacyMeta, 8);
    }
}
