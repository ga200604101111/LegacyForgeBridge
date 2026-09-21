package dev.yinghuang.legacyforgebridge.convert.runtime;

import dev.yinghuang.legacyforgebridge.compat.LegacyRadialModelBlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Stateless client presentation host for a source-proven radial TESR base. */
public final class ConvertedLegacyRadialModelBlockEntity extends BlockEntity {
    public ConvertedLegacyRadialModelBlockEntity(BlockPos pos,BlockState state){
        super(LegacyRadialModelBlockRegistry.requireType(state.getBlock()),pos,state);
    }
}
