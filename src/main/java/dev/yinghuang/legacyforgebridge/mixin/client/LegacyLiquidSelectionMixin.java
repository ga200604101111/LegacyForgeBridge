package dev.yinghuang.legacyforgebridge.mixin.client;

import dev.yinghuang.legacyforgebridge.behavior.Rev233LiquidCompat;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ConvertedLegacyBlock.class)
public abstract class LegacyLiquidSelectionMixin {
    @Inject(method="getShape",at=@At("HEAD"),cancellable=true)
    private void legacyforgebridge$liquidSelection(BlockState state,BlockGetter world,BlockPos pos,
                                                    CollisionContext context,CallbackInfoReturnable<VoxelShape> cir){
        if(Rev233LiquidCompat.isLiquidBlock(this))cir.setReturnValue(Shapes.empty());
    }
}
