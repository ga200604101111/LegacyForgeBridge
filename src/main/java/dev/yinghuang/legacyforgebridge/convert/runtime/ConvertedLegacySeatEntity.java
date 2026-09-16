package dev.yinghuang.legacyforgebridge.convert.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Invisible transient vehicle matching the source dummy-chair lifetime contract. */
public final class ConvertedLegacySeatEntity extends Entity {
    private BlockPos chairPos;
    private boolean anchorReleased;

    public ConvertedLegacySeatEntity(EntityType<? extends ConvertedLegacySeatEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public void bind(BlockPos chairPos) {
        if (chairPos == null) throw new IllegalArgumentException("Missing seat anchor");
        this.chairPos = chairPos.immutable();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        if (chairPos == null || getPassengers().isEmpty()) {
            discard();
            return;
        }
        if (!(level().getBlockEntity(chairPos) instanceof ConvertedLegacySeatBedBlockEntity bed) || !bed.occupied()) {
            discard();
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        releaseAnchor();
        ejectPassengers();
        super.remove(reason);
    }

    private void releaseAnchor() {
        if (anchorReleased || level().isClientSide() || chairPos == null) return;
        anchorReleased = true;
        if (level().getBlockEntity(chairPos) instanceof ConvertedLegacySeatBedBlockEntity bed) bed.releaseSeat();
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder entityData) { }
    @Override protected void readAdditionalSaveData(ValueInput input) { }
    @Override protected void addAdditionalSaveData(ValueOutput output) { }

    @Override
    public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) {
        return false;
    }
}
