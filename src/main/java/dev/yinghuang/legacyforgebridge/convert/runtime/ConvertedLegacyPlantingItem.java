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

/** Exact inherited Minecraft 1.7.10 ItemSeeds / ItemSeedFood planting path. */
public final class ConvertedLegacyPlantingItem extends Item {
    private final Identifier convertedId;

    public ConvertedLegacyPlantingItem(Identifier convertedId, Properties properties) {
        super(properties);
        this.convertedId = convertedId;
        if (!LegacyPlantPlacementRegistry.hasSeedRuntimeRule(convertedId)) {
            throw new IllegalStateException("Missing proven seed placement runtime rule for " + convertedId);
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
        BlockPos above = clicked.above();

        boolean canEditClicked = player.mayUseItemAt(clicked, face, stack);
        boolean canEditAbove = player.mayUseItemAt(above, face, stack);
        if (!canEditClicked || !canEditAbove) return InteractionResult.FAIL;

        Identifier clickedBlockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(clicked).getBlock());
        if (!LegacyPlantPlacementRegistry.canPlantSeed(
                rule, face, clickedBlockId, level.isEmptyBlock(above), true, true)) {
            return InteractionResult.PASS;
        }
        if (!BuiltInRegistries.BLOCK.containsKey(rule.targetBlockId())) return InteractionResult.FAIL;
        Block target = BuiltInRegistries.BLOCK.getValue(rule.targetBlockId());
        if (!(target instanceof ConvertedLegacyPlantBlock)) return InteractionResult.FAIL;

        // 1.7 ItemSeeds/ItemSeedFood always place raw metadata zero and decrement after the set call.
        if (!level.isClientSide()) {
            level.setBlock(above, ConvertedLegacyBlock.withLegacyMeta(target.defaultBlockState(), 0), 3);
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }
}
