package dev.yinghuang.legacyforgebridge.behavior;

import dev.yinghuang.legacyforgebridge.compat.LegacyDurabilityPresentationRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/** Ordinary modern Item, with source-compiled callbacks supplied by the converted mod itself. */
public class ConvertedBehaviorItem extends Item {
    public ConvertedBehaviorItem(Properties properties){super(properties);}
    private LegacyDurabilityPresentationRegistry.Rule durabilityRule(){
        return LegacyDurabilityPresentationRegistry.rule(BuiltInRegistries.ITEM.getKey(this));
    }
    @Override public boolean isBarVisible(ItemStack stack){
        var rule=durabilityRule();
        return rule!=null&&rule.alwaysShowBar()||super.isBarVisible(stack);
    }
    @Override public int getBarWidth(ItemStack stack){
        var rule=durabilityRule();
        if(rule!=null&&rule.inverseProgressBar()&&stack.getMaxDamage()>0){
            int width=Math.round(13.0F*stack.getDamageValue()/stack.getMaxDamage());
            return Math.max(0,Math.min(13,width));
        }
        return super.getBarWidth(stack);
    }
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand){
        ItemStack stack=player.getItemInHand(hand);var d=LegacyBehaviorRuntime.definition(stack);
        if(d==null||!d.hooks().contains("use"))return super.use(level,player,hand);
        LegacyBehaviorRuntime.synchronizeBlocking(stack);
        var result = LegacyBehaviorRuntime.use(stack,player,hand);
        if(result.sustained()){
            player.startUsingItem(hand);
            return InteractionResult.CONSUME;
        }
        return result.handled() ? InteractionResult.CONSUME : InteractionResult.PASS;
    }
    @Override public void hurtEnemy(ItemStack stack,LivingEntity target,LivingEntity attacker) {
        LegacyBehaviorRuntime.hit(stack,target,attacker);
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
