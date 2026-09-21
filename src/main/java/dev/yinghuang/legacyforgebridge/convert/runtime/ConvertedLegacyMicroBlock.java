package dev.yinghuang.legacyforgebridge.convert.runtime;

import dev.yinghuang.legacyforgebridge.compat.LegacyGeometry;
import dev.yinghuang.legacyforgebridge.compat.LegacyMicroBlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Native host block for source-proven N^3 legacy micro-block containers. */
public final class ConvertedLegacyMicroBlock extends ConvertedLegacyBlock implements EntityBlock {
    private final Identifier id;
    public ConvertedLegacyMicroBlock(Identifier id,BlockBehaviour.Properties properties){
        super(id,properties.dynamicShape().noOcclusion());this.id=id;
        if(LegacyMicroBlockRegistry.rule(id)==null)throw new IllegalArgumentException("Missing micro-block rule "+id);
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){
        return new ConvertedLegacyMicroBlockBlockEntity(pos,state);
    }

    @Override protected VoxelShape getShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){
        return sourceShape(state,world,pos);
    }

    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){
        return sourceShape(state,world,pos);
    }

    private VoxelShape sourceShape(BlockState state,BlockGetter world,BlockPos pos){
        BlockEntity blockEntity=world.getBlockEntity(pos);
        if(blockEntity instanceof ConvertedLegacyMicroBlockBlockEntity micro&&!micro.isEmpty())
            return union(micro.occupiedBoxes());
        return union(java.util.List.of(LegacyMicroBlockRegistry.emptyFace(legacyMeta(state))));
    }

    private static VoxelShape union(java.util.List<LegacyGeometry.Box> boxes){
        VoxelShape result=Shapes.empty();
        for(LegacyGeometry.Box box:boxes)result=Shapes.or(result,Shapes.box(box.x0(),box.y0(),box.z0(),box.x1(),box.y1(),box.z1()));
        return result.optimize();
    }
}
