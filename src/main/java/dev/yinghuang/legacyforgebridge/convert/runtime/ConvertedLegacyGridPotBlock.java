package dev.yinghuang.legacyforgebridge.convert.runtime;

import dev.yinghuang.legacyforgebridge.compat.LegacyGridPotBlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/** Executable core of a source-proven 3x3 legacy grid-pot block. */
public final class ConvertedLegacyGridPotBlock extends ConvertedLegacyBlock implements EntityBlock {
    private final LegacyGridPotBlockRegistry.Rule rule;

    public ConvertedLegacyGridPotBlock(Identifier convertedId, BlockBehaviour.Properties properties) {
        super(convertedId, properties);
        this.rule = LegacyGridPotBlockRegistry.rule(convertedId);
        if (rule == null) throw new IllegalArgumentException("Missing grid-pot rule for " + convertedId);
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ConvertedLegacyGridPotBlockEntity(pos, state); }
    @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) { return List.of(); }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof ConvertedLegacyGridPotBlockEntity grid)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        int slot = slotForHit((float)(hitResult.getLocation().x-pos.getX()),
                (float)(hitResult.getLocation().z-pos.getZ()), hitResult.getDirection().getOpposite());
        if (!grid.isEnabled(slot)) return InteractionResult.SUCCESS;
        return removeStoredOrCell(level,pos,player,grid,slot);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof ConvertedLegacyGridPotBlockEntity grid)) return InteractionResult.PASS;

        // Source uses the non-opposite face only for adding another copy of the GridPot itself.
        if (stack.is(asItem())) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            int slot = slotForHit((float)(hitResult.getLocation().x-pos.getX()),
                    (float)(hitResult.getLocation().z-pos.getZ()), hitResult.getDirection());
            if (!grid.isEnabled(slot)) {
                grid.enable(slot);
                if (!player.hasInfiniteMaterials()) stack.shrink(1);
            }
            return InteractionResult.SUCCESS;
        }

        int slot = slotForHit((float)(hitResult.getLocation().x-pos.getX()),
                (float)(hitResult.getLocation().z-pos.getZ()), hitResult.getDirection().getOpposite());
        if (!grid.isEnabled(slot)) return InteractionResult.SUCCESS;

        boolean positive = false;
        boolean negative = false;
        if (stack.getItem() instanceof BlockItem blockItem) {
            Identifier blockId = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock());
            positive = rule.insertionEligible(blockId);
            negative = rule.negativeInsertionEligible(blockId);
            if (!positive && !negative) return InteractionResult.SUCCESS; // unresolved BlockItem stays fail-closed
        } else {
            negative = rule.negativeNonBlockItemRuntimeWired();
            if (!negative) return InteractionResult.SUCCESS;
        }

        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (positive) {
            ItemStack old = grid.removeItem(slot);
            if (!player.hasInfiniteMaterials() && !old.isEmpty())
                Containers.dropItemStack(level,pos.getX(),pos.getY(),pos.getZ(),old);
            grid.setItem(slot,stack);
            if (!player.hasInfiniteMaterials()) stack.shrink(1);
            return InteractionResult.SUCCESS;
        }
        return removeStoredOrCell(level,pos,player,grid,slot);
    }

    private InteractionResult removeStoredOrCell(Level level, BlockPos pos, Player player,
                                                  ConvertedLegacyGridPotBlockEntity grid, int slot) {
        ItemStack stored = grid.item(slot);
        if (!stored.isEmpty()) {
            ItemStack removed = grid.removeItem(slot);
            if (!player.hasInfiniteMaterials() && !removed.isEmpty())
                Containers.dropItemStack(level,pos.getX(),pos.getY(),pos.getZ(),removed);
            return InteractionResult.SUCCESS;
        }
        ItemStack removed = grid.removeCell(slot);
        if (!player.hasInfiniteMaterials()) {
            if (!removed.isEmpty()) Containers.dropItemStack(level,pos.getX(),pos.getY(),pos.getZ(),removed);
            Containers.dropItemStack(level,pos.getX(),pos.getY(),pos.getZ(),new ItemStack(this));
        }
        if (grid.isGridEmpty()) level.removeBlock(pos,false);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof ConvertedLegacyGridPotBlockEntity grid && !grid.isGridEmpty())
            grid.dropAll(level,pos,new ItemStack(this));
        super.affectNeighborsAfterRemoval(state,level,pos,movedByPiston);
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return gridShape(level,pos); }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return gridShape(level,pos); }

    private VoxelShape gridShape(BlockGetter level, BlockPos pos) {
        VoxelShape shape=box(0D,0D,0D,16D,rule.baseHeight()*16D,16D);
        if (!(level.getBlockEntity(pos) instanceof ConvertedLegacyGridPotBlockEntity grid)) return shape;
        double width=16D/rule.gridWidth(),height=rule.cellHeight()*16D;
        for(int slot=0;slot<rule.cells();slot++)if(grid.isEnabled(slot)){
            int x=slot%rule.gridWidth(),z=slot/rule.gridWidth();
            shape=Shapes.or(shape,box(x*width,0D,z*width,(x+1)*width,height,(z+1)*width));
        }
        return shape;
    }

    /** Exact 1.7 source formula: int(3*(x+dx/6)) + int(3*(z+dz/6))*3, upper-clamped to 8. */
    public static int slotForHit(float hitX,float hitZ,Direction direction) {
        float halfCell=1F/3F/2F;hitX+=direction.getStepX()*halfCell;hitZ+=direction.getStepZ()*halfCell;
        int slot=(int)(3F*hitX)+(int)(3F*hitZ)*3;return slot>=9?8:slot;
    }
}
