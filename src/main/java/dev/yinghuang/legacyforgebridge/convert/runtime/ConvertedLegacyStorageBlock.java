package dev.longyu.legacyforgebridge.convert.runtime;

import dev.longyu.legacyforgebridge.compat.LegacyStorageBlockRegistry;
import dev.longyu.legacyforgebridge.convert.LegacyStoragePresentationAnalyzer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Modern block host for source-proven six-row legacy storage semantics. */
public final class ConvertedLegacyStorageBlock extends ConvertedLegacyBlock implements EntityBlock {
    private final LegacyStorageBlockRegistry.Rule storageRule;

    public ConvertedLegacyStorageBlock(Identifier convertedId, BlockBehaviour.Properties properties) {
        super(convertedId, properties);
        this.storageRule = LegacyStorageBlockRegistry.rule(convertedId);
        if (storageRule == null) throw new IllegalArgumentException("Missing storage rule for " + convertedId);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) return null;
        if (storageRule.presentationComplete()
                && storageRule.orientation().equals(
                LegacyStoragePresentationAnalyzer.ORIENTATION_PLAYER_YAW_OPPOSITE_QUADRANT)) {
            return withLegacyMeta(state, legacyMetaForPlayerFacing(context.getHorizontalDirection()));
        }
        return state;
    }

    /** Exact legacy yaw-quadrant result: player SOUTH/WEST/NORTH/EAST -> metadata 0/1/2/3. */
    static int legacyMetaForPlayerFacing(Direction playerFacing) {
        return switch (playerFacing) {
            case SOUTH -> 0;
            case WEST -> 1;
            case NORTH -> 2;
            case EAST -> 3;
            default -> throw new IllegalArgumentException("Expected horizontal player facing, got " + playerFacing);
        };
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConvertedLegacyStorageBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                BlockHitResult hitResult) {
        if (storageRule.sneakingPass() && player.isShiftKeyDown()) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof ConvertedLegacyStorageBlockEntity storage
                && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(storage);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (!movedByPiston && storageRule.dropContents()) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof ConvertedLegacyStorageBlockEntity storage) {
                Containers.dropContents(level, pos, storage);
            }
        }
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        if (storageRule.comparator()) {
            Containers.updateNeighboursAfterDestroy(state, level, pos);
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return storageRule.comparator();
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        if (!storageRule.comparator()) return 0;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof ConvertedLegacyStorageBlockEntity storage
                ? AbstractContainerMenu.getRedstoneSignalFromContainer(storage)
                : 0;
    }
}
