package dev.yinghuang.legacyforgebridge.convert.runtime;

import dev.yinghuang.legacyforgebridge.compat.LegacyInertModelBlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Modern host for an inert source BlockContainer whose TileEntity only supplied fixed presentation. */
public final class ConvertedLegacyInertModelBlock extends ConvertedLegacyBlock implements EntityBlock {
    private final LegacyInertModelBlockRegistry.Rule rule;
    private final VoxelShape shape;

    public ConvertedLegacyInertModelBlock(Identifier id,BlockBehaviour.Properties properties){
        super(id,configure(id,properties));
        this.rule=LegacyInertModelBlockRegistry.requireRule(id);
        var b=rule.bounds();
        this.shape=Shapes.box(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ());
    }

    private static BlockBehaviour.Properties configure(Identifier id,BlockBehaviour.Properties properties){
        var rule=LegacyInertModelBlockRegistry.requireRule(id);
        return properties.noOcclusion().lightLevel(state->rule.lightEmission());
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        BlockState state=super.getStateForPlacement(context);if(state==null)return null;
        int meta=legacyMetaForPlayerFacing(context.getHorizontalDirection());
        return withLegacyMeta(state,meta);
    }

    static int legacyMetaForPlayerFacing(Direction playerFacing){
        return switch(playerFacing){case SOUTH->0;case WEST->1;case NORTH->2;case EAST->3;default->throw new IllegalArgumentException("Expected horizontal direction: "+playerFacing);};
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new ConvertedLegacyInertModelBlockEntity(pos,state);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.INVISIBLE;}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return shape;}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return shape;}
}
