package dev.yinghuang.legacyforgebridge.convert.runtime;

import dev.yinghuang.legacyforgebridge.compat.LegacyMetadataRotatingTesrRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Invisible modern block host for source-proven metadata-speed rotating TESR presentation. */
public final class ConvertedLegacyMetadataRotatingBlock extends ConvertedLegacyBlock implements EntityBlock {
    public ConvertedLegacyMetadataRotatingBlock(Identifier id,BlockBehaviour.Properties properties){
        super(id,properties.noOcclusion());
        LegacyMetadataRotatingTesrRegistry.requireRule(id);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new ConvertedLegacyMetadataRotatingBlockEntity(pos,state);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.INVISIBLE;}
}
