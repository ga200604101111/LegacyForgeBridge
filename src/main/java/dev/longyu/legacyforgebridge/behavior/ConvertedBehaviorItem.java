package dev.longyu.legacyforgebridge.behavior;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/** Ordinary modern Item, with source-compiled callbacks supplied by the converted mod itself. */
public final class ConvertedBehaviorItem extends Item {
    public ConvertedBehaviorItem(Properties properties){super(properties);}
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand){
        ItemStack stack=player.getItemInHand(hand);var d=LegacyBehaviorRuntime.definition(stack);
        if(d==null||!d.hooks().contains("use"))return super.use(level,player,hand);
        LegacyBehaviorRuntime.synchronizeBlocking(stack);
        if(LegacyBehaviorRuntime.startUse(stack,player)){
            player.startUsingItem(hand);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }
    @Override public ItemUseAnimation getUseAnimation(ItemStack stack){
        var d=LegacyBehaviorRuntime.definition(stack);
        return d!=null&&d.hooks().contains("action")?LegacyBehaviorRuntime.animation(stack):super.getUseAnimation(stack);
    }
    @Override public int getUseDuration(ItemStack stack,LivingEntity entity){
        var d=LegacyBehaviorRuntime.definition(stack);
        return d!=null&&d.hooks().contains("duration")?LegacyBehaviorRuntime.duration(stack):super.getUseDuration(stack,entity);
    }
    @Override public boolean releaseUsing(ItemStack stack,Level level,LivingEntity entity,int remaining){
        LegacyBehaviorRuntime.release(stack,entity,remaining);
        return super.releaseUsing(stack,level,entity,remaining);
    }
}
