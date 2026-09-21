package dev.yinghuang.legacyforgebridge.compat;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;

import java.util.Set;

/** Exact modern presentation mappings for a bounded subset of vanilla 1.7 particle names. */
public final class LegacyParticle1710 {
    private static final Set<String> SUPPORTED=Set.of("smoke","largesmoke","flame","cloud","heart","portal","crit");
    private LegacyParticle1710(){}

    public static boolean supported(String legacyName){return legacyName!=null&&SUPPORTED.contains(legacyName);}
    public static ParticleOptions modern(String legacyName){
        return switch(legacyName){
            case "smoke"->ParticleTypes.SMOKE;
            case "largesmoke"->ParticleTypes.LARGE_SMOKE;
            case "flame"->ParticleTypes.FLAME;
            case "cloud"->ParticleTypes.CLOUD;
            case "heart"->ParticleTypes.HEART;
            case "portal"->ParticleTypes.PORTAL;
            case "crit"->ParticleTypes.CRIT;
            default->null;
        };
    }
}
