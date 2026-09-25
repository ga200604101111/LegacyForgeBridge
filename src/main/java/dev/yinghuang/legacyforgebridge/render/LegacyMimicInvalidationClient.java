package dev.yinghuang.legacyforgebridge.render;

import dev.yinghuang.legacyforgebridge.compat.LegacyBlockGeometryRegistry;
import dev.yinghuang.legacyforgebridge.compat.LegacyGeometrySpec;
import dev.yinghuang.legacyforgebridge.compat.LegacyMimicRefreshQueue;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

/** Client-thread rendering invalidation only: never sends packets or changes world state. */
public final class LegacyMimicInvalidationClient {
    private static final LegacyMimicRefreshQueue PENDING = new LegacyMimicRefreshQueue(8192);
    private static boolean initialized;
    private static boolean enabled;
    private static ClientLevel activeLevel;

    private LegacyMimicInvalidationClient() { }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        enabled = LegacyBlockGeometryRegistry.all().values().stream().anyMatch(LegacyGeometrySpec.Rule::mimic);
        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
            if (accept(level)) PENDING.columnChanged(chunk.getPos().x, chunk.getPos().z,
                    level.getMinSectionY(), level.getSectionsCount());
        });
        ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
            if (accept(level)) PENDING.columnChanged(chunk.getPos().x, chunk.getPos().z,
                    level.getMinSectionY(), level.getSectionsCount());
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            bind(client.level);
            if (!enabled || activeLevel == null) return;
            var batch = PENDING.drain();
            if (batch.fullRefresh()) {
                client.levelRenderer.allChanged();
            } else {
                for (var section : batch.sections()) {
                    client.levelRenderer.setSectionDirty(section.x(), section.y(), section.z());
                }
            }
        });
    }

    /** Called by both the changed-state and listener-notification paths; duplicates coalesce. */
    public static void blockChanged(ClientLevel level, BlockPos position) {
        if (accept(level)) PENDING.blockChanged(position.getX(), position.getY(), position.getZ());
    }

    private static boolean accept(ClientLevel level) {
        if (!enabled) return false;
        Minecraft client = Minecraft.getInstance();
        // Reject stale-world callbacks and never touch the renderer from a worker thread.
        if (!client.isSameThread() || level != client.level) return false;
        bind(level);
        return level != null;
    }

    private static void bind(ClientLevel level) {
        if (activeLevel != level) {
            activeLevel = level;
            PENDING.clear();
        }
    }
}
