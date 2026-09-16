package dev.yinghuang.legacyforgebridge.convert.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Dedicated separate-ID placement item for the source-proven legacy two-part bed family. */
public final class ConvertedLegacySeatBedItem extends Item {
    private final ConvertedLegacySeatBedBlock block;

    public ConvertedLegacySeatBedItem(ConvertedLegacySeatBedBlock block, Properties properties) {
        super(properties);
        this.block = block;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (context.getClickedFace() != Direction.UP) return InteractionResult.FAIL;
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.FAIL;

        ItemStack stack = context.getItemInHand();
        BlockPos foot = context.getClickedPos().above();
        Direction facing = player.getDirection();
        BlockPos head = foot.relative(facing);
        // Modern ItemStack already enforces CAN_PLACE_ON against the clicked support. The legacy
        // ItemBed additionally checked both target cells via canPlayerEdit. When modern build
        // permission is absent we fail closed instead of inventing an adventure-mode equivalence.
        if (!player.getAbilities().mayBuild) return InteractionResult.FAIL;
        if (!level.getBlockState(foot).isAir() || !level.getBlockState(head).isAir()) return InteractionResult.FAIL;
        if (!solidTop(level, foot.below()) || !solidTop(level, head.below())) return InteractionResult.FAIL;

        BlockState footState = block.stateForLegacyHalf(facing, false, false);
        if (!level.setBlock(foot, footState, 3)) return InteractionResult.FAIL;
        if (level.getBlockState(foot).is(block)) {
            level.setBlock(head, block.stateForLegacyHalf(facing, true, false), 3);
        }
        if (!player.hasInfiniteMaterials()) stack.shrink(1);
        return InteractionResult.SUCCESS;
    }

    private static boolean solidTop(Level level, BlockPos pos) {
        return level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP);
    }
}
