package dev.yinghuang.legacyforgebridge.convert.runtime;

import dev.yinghuang.legacyforgebridge.compat.LegacyInertModelBlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Empty modern host for source-proven legacy TileEntities whose only role was static presentation. */
public final class ConvertedLegacyInertModelBlockEntity extends BlockEntity {
    public ConvertedLegacyInertModelBlockEntity(BlockPos pos,BlockState state){
        super(LegacyInertModelBlockRegistry.requireType(state.getBlock()),pos,state);
    }
}
