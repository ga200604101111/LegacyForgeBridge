package dev.yinghuang.legacyforgebridge.convert.runtime;

import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;

/** Shared LFB-owned entity type used by proof-gated converted transient-seat families. */
public final class LegacySeatEntityRuntime {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(LegacyForgeBridge.MOD_ID, "converted_legacy_seat");
    private static EntityType<ConvertedLegacySeatEntity> type;

    private LegacySeatEntityRuntime() { }

    public static synchronized void bootstrap() {
        if (type != null) return;
        if (BuiltInRegistries.ENTITY_TYPE.containsKey(ID)) {
            @SuppressWarnings("unchecked")
            EntityType<ConvertedLegacySeatEntity> existing =
                    (EntityType<ConvertedLegacySeatEntity>) BuiltInRegistries.ENTITY_TYPE.getValue(ID);
            type = existing;
            return;
        }
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ID);
        EntityType<ConvertedLegacySeatEntity> created = EntityType.Builder
                .<ConvertedLegacySeatEntity>of(ConvertedLegacySeatEntity::new, MobCategory.MISC)
                .noSummon().noLootTable().sized(0.01F, 0.01F)
                .passengerAttachments(0.0F).clientTrackingRange(10).updateInterval(1)
                .build(key);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, key, created);
        type = created;
    }

    public static EntityType<ConvertedLegacySeatEntity> type() {
        if (type == null) throw new IllegalStateException("Legacy seat entity type not bootstrapped");
        return type;
    }

    public static boolean mount(ServerLevel level, BlockPos footPos, Player player) {
        if (!(level.getBlockEntity(footPos) instanceof ConvertedLegacySeatBedBlockEntity bed) || !bed.tryOccupy()) return false;
        ConvertedLegacySeatEntity seat = new ConvertedLegacySeatEntity(type(), level);
        seat.bind(footPos);
        seat.setPos(footPos.getX() + 0.5D, footPos.getY() + 0.25D, footPos.getZ() + 0.5D);
        if (!level.addFreshEntity(seat)) {
            bed.releaseSeat();
            return false;
        }
        if (!player.startRiding(seat, true, true)) {
            seat.discard();
            bed.releaseSeat();
            return false;
        }
        return true;
    }
}
