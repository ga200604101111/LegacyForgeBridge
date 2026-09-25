package dev.yinghuang.legacyforgebridge.convert.runtime;

import dev.yinghuang.legacyforgebridge.compat.LegacySeatBedRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Foot-half-only transient occupancy carrier. The legacy isOccupy field was intentionally not persisted. */
public final class ConvertedLegacySeatBedBlockEntity extends BlockEntity {
    private boolean occupied;

    public ConvertedLegacySeatBedBlockEntity(BlockPos pos, BlockState state) {
        super(LegacySeatBedRegistry.requireType(state.getBlock()), pos, state);
    }

    public boolean tryOccupy() {
        if (occupied) return false;
        occupied = true;
        setChanged();
        return true;
    }

    public void releaseSeat() {
        if (!occupied) return;
        occupied = false;
        setChanged();
    }

    public boolean occupied() { return occupied; }
}
