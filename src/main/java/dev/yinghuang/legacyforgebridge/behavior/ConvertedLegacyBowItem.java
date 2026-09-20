package dev.yinghuang.legacyforgebridge.behavior;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/** Native sustained-use host for source-proven legacy bow items. */
public final class ConvertedLegacyBowItem extends ConvertedBehaviorItem {
    public ConvertedLegacyBowItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        InteractionResult source = super.use(level, player, hand);
        if (source != InteractionResult.PASS) return source;
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override public ItemUseAnimation getUseAnimation(ItemStack stack) { return ItemUseAnimation.BOW; }
    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return 72_000; }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        super.releaseUsing(stack, level, entity, remaining);
        return true;
    }
}
