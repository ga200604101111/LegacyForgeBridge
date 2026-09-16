package dev.yinghuang.legacyforgebridge.convert.runtime;

import dev.yinghuang.legacyforgebridge.compat.LegacyGridPotBlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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

/**
 * Executable core of a source-proven 3x3 legacy grid-pot block. Content insertion remains
 * deliberately blocked until legacy render-type membership can be mapped without approximation.
 */
public final class ConvertedLegacyGridPotBlock extends ConvertedLegacyBlock implements EntityBlock {
    private final LegacyGridPotBlockRegistry.Rule rule;

    public ConvertedLegacyGridPotBlock(Identifier convertedId, BlockBehaviour.Properties properties) {
        super(convertedId, properties);
        this.rule = LegacyGridPotBlockRegistry.rule(convertedId);
        if (rule == null) throw new IllegalArgumentException("Missing grid-pot rule for " + convertedId);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConvertedLegacyGridPotBlockEntity(pos, state);
    }

    /** The source block explicitly returns null from getItemDropped; enabled cells own all drops. */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof ConvertedLegacyGridPotBlockEntity grid)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        float hitX = (float) (hitResult.getLocation().x - pos.getX());
        float hitZ = (float) (hitResult.getLocation().z - pos.getZ());
        int slot = slotForHit(hitX, hitZ, hitResult.getDirection().getOpposite());
        if (!grid.isEnabled(slot)) return InteractionResult.SUCCESS;

        ItemStack stored = grid.item(slot);
        if (!stored.isEmpty()) {
            ItemStack removed = grid.removeItem(slot);
            if (!player.hasInfiniteMaterials() && !removed.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), removed);
            }
            return InteractionResult.SUCCESS;
        }

        ItemStack removed = grid.removeCell(slot);
        if (!player.hasInfiniteMaterials()) {
            if (!removed.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), removed);
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(this));
        }
        if (grid.isGridEmpty()) level.removeBlock(pos, false);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof ConvertedLegacyGridPotBlockEntity grid)) {
            return InteractionResult.PASS;
        }

        // The source has a second slot lookup without getOpposite() specifically for adding another
        // copy of the grid-pot item to the existing block.
        if (stack.is(asItem())) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            float hitX = (float) (hitResult.getLocation().x - pos.getX());
            float hitZ = (float) (hitResult.getLocation().z - pos.getZ());
            int slot = slotForHit(hitX, hitZ, hitResult.getDirection());
            if (!grid.isEnabled(slot)) {
                grid.enable(slot);
                if (!player.hasInfiniteMaterials()) stack.shrink(1);
            }
            return InteractionResult.SUCCESS;
        }

        // The source decides whether another BlockItem can occupy a cell from legacy render type
        // {1, 13, 40, coordinateCrossUID}. Modern BlockItem-ness is not an equivalent predicate.
        // Consume the interaction without mutation so neither guessed insertion nor accidental
        // modern placement can occur until that render provenance is compiled.
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof ConvertedLegacyGridPotBlockEntity grid && !grid.isGridEmpty()) {
            grid.dropAll(level, pos, new ItemStack(this));
        }
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return gridShape(level, pos);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return gridShape(level, pos);
    }

    private VoxelShape gridShape(BlockGetter level, BlockPos pos) {
        VoxelShape shape = box(0D, 0D, 0D, 16D, rule.baseHeight() * 16D, 16D);
        if (!(level.getBlockEntity(pos) instanceof ConvertedLegacyGridPotBlockEntity grid)) return shape;
        double width = 16D / rule.gridWidth();
        double height = rule.cellHeight() * 16D;
        for (int slot = 0; slot < rule.cells(); slot++) {
            if (!grid.isEnabled(slot)) continue;
            int x = slot % rule.gridWidth();
            int z = slot / rule.gridWidth();
            shape = Shapes.or(shape, box(x * width, 0D, z * width, (x + 1) * width, height, (z + 1) * width));
        }
        return shape;
    }

    /** Exact 1.7 source formula: int(3*(x+dx/6)) + int(3*(z+dz/6))*3, upper-clamped to 8. */
    public static int slotForHit(float hitX, float hitZ, Direction direction) {
        float halfCell = 1F / 3F / 2F;
        hitX += direction.getStepX() * halfCell;
        hitZ += direction.getStepZ() * halfCell;
        int slot = (int) (3F * hitX) + (int) (3F * hitZ) * 3;
        return slot >= 9 ? 8 : slot;
    }
}
