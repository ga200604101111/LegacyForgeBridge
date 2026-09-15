package dev.yinghuang.legacyforgebridge.convert.runtime;

import dev.yinghuang.legacyforgebridge.compat.LegacyOscillatingModelBlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/** Modern host for source-proven client-only oscillating decorative BlockEntities. */
public final class ConvertedLegacyOscillatingModelBlock extends ConvertedLegacyBlock implements EntityBlock {
    private final Identifier id;
    private final LegacyOscillatingModelBlockRegistry.Rule rule;

    public ConvertedLegacyOscillatingModelBlock(Identifier id,Properties properties){
        super(id,properties);
        this.id=id;
        this.rule=LegacyOscillatingModelBlockRegistry.requireRule(id);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context){
        BlockState state=super.getStateForPlacement(context);
        if(state==null)return null;
        int meta=legacyMetaForPlayerFacing(context.getHorizontalDirection(),rule.placementMetaByYawQuadrant());
        return ConvertedLegacyBlock.withLegacyMeta(state,meta);
    }

    static int legacyMetaForPlayerFacing(Direction facing,int[] mapping){
        if(mapping==null||mapping.length!=4)throw new IllegalArgumentException("Expected four legacy yaw quadrants");
        int quadrant=switch(facing){
            case SOUTH->0;
            case WEST->1;
            case NORTH->2;
            case EAST->3;
            default->throw new IllegalArgumentException("Horizontal facing required: "+facing);
        };
        return mapping[quadrant];
    }

    @Override public RenderShape getRenderShape(BlockState state){return RenderShape.INVISIBLE;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new ConvertedLegacyOscillatingModelBlockEntity(pos,state);}

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){
        if(!level.isClientSide())return null;
        BlockEntityType<ConvertedLegacyOscillatingModelBlockEntity> expected=LegacyOscillatingModelBootstrap.type(id);
        if(type!=expected)return null;
        return (BlockEntityTicker<T>)(BlockEntityTicker<ConvertedLegacyOscillatingModelBlockEntity>)ConvertedLegacyOscillatingModelBlockEntity::clientTick;
    }
}
