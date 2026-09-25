package dev.yinghuang.legacyforgebridge.convert.runtime;

import dev.yinghuang.legacyforgebridge.compat.LegacySingleInputProcessorRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Objects;

public final class LegacyProcessorParticleBridge {
    @FunctionalInterface public interface Emitter { void emit(Level level,BlockPos pos,ItemStack input,float roll,LegacySingleInputProcessorRegistry.ParticleRule rule); }
    private static volatile Emitter emitter=(level,pos,input,roll,rule)->{};
    private LegacyProcessorParticleBridge(){}
    public static void install(Emitter value){emitter=Objects.requireNonNull(value,"value");}
    public static void emit(Level level,BlockPos pos,ItemStack input,float roll,LegacySingleInputProcessorRegistry.ParticleRule rule){if(level==null||pos==null||input==null||input.isEmpty()||rule==null)return;emitter.emit(level,pos,input,roll,rule);}
}
