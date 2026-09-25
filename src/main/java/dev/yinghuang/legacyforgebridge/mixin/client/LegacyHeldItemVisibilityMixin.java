package dev.yinghuang.legacyforgebridge.mixin.client;

import dev.yinghuang.legacyforgebridge.compat.LegacyHeldItemVisibilityRegistry;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Replays source-proven client randomDisplayTick held-item visibility metadata toggles. */
@Mixin(ConvertedLegacyBlock.class)
public abstract class LegacyHeldItemVisibilityMixin {
    /** Vanilla outline queries omit the entity context. Match the same current-hand pick
     * only for the active client level, never for a server/world-shape query. */
    @Inject(method="getShape",at=@At("HEAD"),cancellable=true)
    private void lfb$currentHandSelection(BlockState state,BlockGetter level,BlockPos pos,
            CollisionContext context,CallbackInfoReturnable<VoxelShape> ci){
        if(context!=CollisionContext.empty())return;
        var client=Minecraft.getInstance();if(level!=client.level)return;
        Block self=(Block)(Object)this;
        var rule=LegacyHeldItemVisibilityRegistry.rule(BuiltInRegistries.BLOCK.getKey(self));
        if(rule==null||rule.selection()==null)return;
        boolean held=client.player!=null&&client.player.getMainHandItem().is(self.asItem());
        ci.setReturnValue(rule.selectionShape(held));
    }

    @Inject(method="animateTick",at=@At("HEAD"))
    private void lfb$heldOwnBlockVisibility(BlockState state,Level level,BlockPos pos,RandomSource random,CallbackInfo ci){
        Block self=(Block)(Object)this;var rule=LegacyHeldItemVisibilityRegistry.rule(BuiltInRegistries.BLOCK.getKey(self));if(rule==null)return;var player=Minecraft.getInstance().player;boolean visible=player!=null&&player.getMainHandItem().getItem()==self.asItem();int meta=ConvertedLegacyBlock.legacyMeta(state);int target=visible?(meta|rule.visibleOrMask()):(meta&rule.hiddenAndMask());if(target!=meta&&target>=0&&target<=15)level.setBlock(pos,ConvertedLegacyBlock.withLegacyMeta(state,target),3);
    }
}
