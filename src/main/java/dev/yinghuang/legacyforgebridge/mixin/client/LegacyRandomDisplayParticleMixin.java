package dev.yinghuang.legacyforgebridge.mixin.client;

import dev.yinghuang.legacyforgebridge.compat.LegacyParticle1710;
import dev.yinghuang.legacyforgebridge.compat.LegacyRandomDisplayParticleRegistry;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyBlock;
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

/** Replays source-proven client randomDisplayTick particle presentation. */
@Mixin(ConvertedLegacyBlock.class)
public abstract class LegacyRandomDisplayParticleMixin {
    @Inject(method="animateTick",at=@At("TAIL"))
    private void lfb$randomDisplayParticles(BlockState state,Level level,BlockPos pos,RandomSource random,CallbackInfo ci){
        Block self=(Block)(Object)this;
        var rule=LegacyRandomDisplayParticleRegistry.rule(BuiltInRegistries.BLOCK.getKey(self));if(rule==null)return;
        double x=pos.getX()+rule.centerX()+(random.nextFloat()*2F-1F)*rule.spreadX();
        double y=pos.getY()+rule.centerY();
        double z=pos.getZ()+rule.centerZ()+(random.nextFloat()*2F-1F)*rule.spreadZ();
        for(String name:rule.particles()){
            var particle=LegacyParticle1710.modern(name);if(particle!=null)level.addParticle(particle,x,y,z,0D,0D,0D);
        }
    }
}
