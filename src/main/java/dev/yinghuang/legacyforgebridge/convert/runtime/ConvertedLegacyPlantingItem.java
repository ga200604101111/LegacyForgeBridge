package dev.yinghuang.legacyforgebridge.convert.runtime;

import dev.yinghuang.legacyforgebridge.compat.LegacyPlantPlacementRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Exact inherited Minecraft 1.7.10 ItemSeeds / ItemSeedFood / ItemReed planting path. */
public final class ConvertedLegacyPlantingItem extends Item {
    private final Identifier convertedId;

    public ConvertedLegacyPlantingItem(Identifier convertedId, Properties properties) {
        super(properties);
        this.convertedId = convertedId;
        if (!LegacyPlantPlacementRegistry.hasRuntimeRule(convertedId)) {
            throw new IllegalStateException("Missing proven plant placement runtime rule for " + convertedId);
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        LegacyPlantPlacementRegistry.Rule rule = LegacyPlantPlacementRegistry.rule(convertedId);
        if (rule == null) return super.useOn(context);

        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        ItemStack stack = context.getItemInHand();
        BlockPos clicked = context.getClickedPos();
        Direction face = context.getClickedFace();

        return switch (rule.adapter()) {
            case SEEDS, SEED_FOOD -> useSeed(rule, level, player, stack, clicked, face);
            case REED -> useReed(rule, level, player, stack, clicked, face);
        };
    }

    private static InteractionResult useSeed(LegacyPlantPlacementRegistry.Rule rule, Level level, Player player,
                                             ItemStack stack, BlockPos clicked, Direction face) {
        BlockPos above = clicked.above();
        boolean canEditClicked = player.mayUseItemAt(clicked, face, stack);
        boolean canEditAbove = player.mayUseItemAt(above, face, stack);
        if (!canEditClicked || !canEditAbove) return InteractionResult.FAIL;

        Identifier clickedBlockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(clicked).getBlock());
        if (!LegacyPlantPlacementRegistry.canPlantSeed(
                rule, face, clickedBlockId, level.isEmptyBlock(above), true, true)) {
            return InteractionResult.PASS;
        }
        Block target = requiredTarget(rule);
        if (target == null) return InteractionResult.FAIL;

        // 1.7 ItemSeeds/ItemSeedFood place raw metadata zero and consume after the set call.
        if (!level.isClientSide()) {
            level.setBlock(above, ConvertedLegacyBlock.withLegacyMeta(target.defaultBlockState(), 0), 3);
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult useReed(LegacyPlantPlacementRegistry.Rule rule, Level level, Player player,
                                             ItemStack stack, BlockPos clicked, Direction clickedFace) {
        BlockState clickedState = level.getBlockState(clicked);
        boolean thinSnow = clickedState.is(Blocks.SNOW)
                && clickedState.hasProperty(SnowLayerBlock.LAYERS)
                && clickedState.getValue(SnowLayerBlock.LAYERS) == 1;
        boolean replaceClicked = thinSnow || clickedState.is(Blocks.VINE)
                || clickedState.is(Blocks.SHORT_GRASS) || clickedState.is(Blocks.DEAD_BUSH);
        Direction placementFace = LegacyPlantPlacementRegistry.reedPlacementFace(clickedFace, thinSnow);
        BlockPos placementPos = LegacyPlantPlacementRegistry.reedPlacementPos(clicked, clickedFace, replaceClicked);

        // ItemReed performs these checks after resolving the final target coordinate/side.
        if (stack.isEmpty() || !player.mayUseItemAt(placementPos, placementFace, stack)) {
            return InteractionResult.FAIL;
        }
        Block target = requiredTarget(rule);
        if (target == null) return InteractionResult.FAIL;

        BlockState current = level.getBlockState(placementPos);
        boolean replaceable = replaceClicked || current.canBeReplaced();
        BlockState placed = ConvertedLegacyBlock.withLegacyMeta(target.defaultBlockState(), 0);
        boolean canPlace = replaceable && placed.canSurvive(level, placementPos);

        // 1.7 ItemReed returns true after the edit/non-empty checks even when canPlaceEntityOnSide rejects placement.
        // Only a successful set consumes the stack.
        if (canPlace && !level.isClientSide() && level.setBlock(placementPos, placed, 3)) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    private static Block requiredTarget(LegacyPlantPlacementRegistry.Rule rule) {
        if (!BuiltInRegistries.BLOCK.containsKey(rule.targetBlockId())) return null;
        Block target = BuiltInRegistries.BLOCK.getValue(rule.targetBlockId());
        return target instanceof ConvertedLegacyPlantBlock ? target : null;
    }
}
