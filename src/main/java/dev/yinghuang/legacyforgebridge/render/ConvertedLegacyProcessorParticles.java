package dev.yinghuang.legacyforgebridge.render;

import dev.yinghuang.legacyforgebridge.compat.LegacySingleInputProcessorRegistry;
import dev.yinghuang.legacyforgebridge.convert.runtime.LegacyProcessorParticleBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class ConvertedLegacyProcessorParticles {
    private ConvertedLegacyProcessorParticles(){}
    public static void install(){LegacyProcessorParticleBridge.install(ConvertedLegacyProcessorParticles::emit);}
    private static void emit(Level level,BlockPos pos,ItemStack input,float roll,LegacySingleInputProcessorRegistry.ParticleRule rule){
        if(!(level instanceof ClientLevel client)||input.isEmpty())return;
        if(input.getItem() instanceof BlockItem blockItem){double x,z;if((((int)roll)&1)==1){x=client.random.nextFloat();z=client.random.nextInt(2);}else{x=client.random.nextInt(2);z=client.random.nextFloat();}Particle particle=Minecraft.getInstance().particleEngine.createParticle(new BlockParticleOption(ParticleTypes.BLOCK,blockItem.getBlock().defaultBlockState()),pos.getX()+x,pos.getY()+0.5D,pos.getZ()+z,0D,0D,0D);if(particle!=null)particle.setPower(rule.blockVelocityMultiplier()).scale(rule.blockScale());return;}
        float phase=client.random.nextFloat()*2F-1F;for(int i=0;i<rule.itemParticleCount();i++){Vec3 velocity=new Vec3((client.random.nextFloat()-0.5D)*0.1D,Math.random()*0.1D+0.1D,0D).yRot(-phase*(float)Math.PI);Vec3 origin=new Vec3((client.random.nextFloat()-0.5D)*0.3D,-client.random.nextFloat()*0.6D-0.3D,0.6D).yRot(-phase*(float)Math.PI).add(pos.getX()+0.5D,pos.getY()+1D,pos.getZ()+0.5D);client.addParticle(new ItemParticleOption(ParticleTypes.ITEM,input.copyWithCount(1)),origin.x,origin.y,origin.z,velocity.x,velocity.y-rule.itemVelocityYOffset(),velocity.z);}
    }
}
