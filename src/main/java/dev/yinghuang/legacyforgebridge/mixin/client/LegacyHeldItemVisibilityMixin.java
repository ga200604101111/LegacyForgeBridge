package dev.yinghuang.legacyforgebridge.mixin.client;

import dev.yinghuang.legacyforgebridge.compat.LegacyHeldItemVisibilityRegistry;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replays source-proven client randomDisplayTick held-item visibility metadata toggles. */
@Mixin(ConvertedLegacyBlock.class)
public abstract class LegacyHeldItemVisibilityMixin {
    @Inject(method="animateTick",at=@At("HEAD"))
    private void lfb$heldOwnBlockVisibility(BlockState state,Level level,BlockPos pos,RandomSource random,CallbackInfo ci){
        Block self=(Block)(Object)this;var rule=LegacyHeldItemVisibilityRegistry.rule(BuiltInRegistries.BLOCK.getKey(self));if(rule==null)return;var player=Minecraft.getInstance().player;boolean visible=player!=null&&player.getMainHandItem().getItem()==self.asItem();int meta=ConvertedLegacyBlock.legacyMeta(state);int target=visible?(meta|rule.visibleOrMask()):(meta&rule.hiddenAndMask());if(target!=meta&&target>=0&&target<=15)level.setBlock(pos,ConvertedLegacyBlock.withLegacyMeta(state,target),3);
    }
}
