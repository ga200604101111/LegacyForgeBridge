package dev.yinghuang.legacyforgebridge.compat;

import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyProcessorBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import team.reborn.energy.api.EnergyStorage;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Registers the modern sided energy provider only for source-proven legacy receiver contracts. */
public final class LegacyProcessorEnergyCompat {
    private static final Set<BlockEntityType<?>> REGISTERED = ConcurrentHashMap.newKeySet();

    private LegacyProcessorEnergyCompat() { }

    public static synchronized void register(
            LegacySingleInputProcessorRegistry.Rule rule,
            BlockEntityType<ConvertedLegacyProcessorBlockEntity> type
    ) {
        if (rule == null || type == null || !rule.legacyEnergyApiPresent()
                || !rule.energyIngressRuntimeComplete() || !REGISTERED.add(type)) return;
        EnergyStorage.SIDED.registerForBlockEntity((blockEntity, direction) -> blockEntity.energyStorage(), type);
    }
}
