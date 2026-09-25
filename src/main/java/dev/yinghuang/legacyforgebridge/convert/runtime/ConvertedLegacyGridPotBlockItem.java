package dev.yinghuang.legacyforgebridge.convert.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** BlockItem that replays the source-proven first-cell initialization after ordinary placement. */
public final class ConvertedLegacyGridPotBlockItem extends BlockItem {
    public ConvertedLegacyGridPotBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState placementState) {
        if (!super.placeBlock(context, placementState)) return false;
        if (context.getLevel().getBlockEntity(context.getClickedPos()) instanceof ConvertedLegacyGridPotBlockEntity grid) {
            BlockPos originalHitBlock = context.replacingClickedOnBlock()
                    ? context.getClickedPos()
                    : context.getClickedPos().relative(context.getClickedFace().getOpposite());
            Vec3 location = context.getClickLocation();
            float hitX = (float) (location.x - originalHitBlock.getX());
            float hitZ = (float) (location.z - originalHitBlock.getZ());
            int slot = ConvertedLegacyGridPotBlock.slotForHit(hitX, hitZ, context.getClickedFace().getOpposite());
            grid.enable(slot);
        }
        return true;
    }
}
