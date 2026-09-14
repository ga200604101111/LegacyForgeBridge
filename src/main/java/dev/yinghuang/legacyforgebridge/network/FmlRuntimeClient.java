package dev.yinghuang.legacyforgebridge.network;

import dev.yinghuang.legacyforgebridge.runtime.LegacyRuntimeBindings;
import dev.yinghuang.legacyforgebridge.runtime.LegacyRuntimeGuards;
import dev.yinghuang.legacyforgebridge.runtime.LegacyRuntimeProvider;
import dev.yinghuang.legacyforgebridge.runtime.LegacyRuntimeProviders;
import dev.yinghuang.legacyforgebridge.runtime.LegacyRuntimeState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.entity.Entity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Client dispatcher for distinct FML and FORGE runtime channels, scoped to one connection. */
public final class FmlRuntimeClient {
    public enum Phase { CONFIGURATION, PLAY }
    private static final int MAX_QUEUED_PACKETS = 256;
    private static final int MAX_QUEUED_BYTES = 1_048_576;
    private final LegacyRuntimeState state = new LegacyRuntimeState();
    private final LegacyRuntimeBindings<LegacyRuntimeProvider.EntityAdapter, LegacyRuntimeProvider.GuiAdapter> bindings
            = new LegacyRuntimeBindings<>();
    private final ArrayDeque<byte[]> pending = new ArrayDeque<>();
    private int queuedBytes;

    public void initializeAdapters() { LegacyRuntimeProviders.load(bindings); }
    public synchronized void beginSession() { pending.clear(); queuedBytes = 0; state.begin(); }
    public synchronized void reset() { pending.clear(); queuedBytes = 0; state.reset(); }

    public void onPlay(FmlConnectionTrace trace) {
        List<byte[]> ready;
        long generation;
        synchronized (this) {
            if (!state.snapshot().active()) state.begin();
            generation = state.snapshot().generation();
            ready = new ArrayList<>(pending);
            pending.clear();
            queuedBytes = 0;
        }
        for (byte[] payload : ready) handle(payload, Phase.PLAY, trace, generation);
    }

    public void handleForge(byte[] payload, Phase phase, FmlConnectionTrace trace) {
        var snapshot = state.snapshot();
        if (!snapshot.active()) return;
        try {
            ForgeRuntimeCodec.Message message = ForgeRuntimeCodec.decode(payload);
            state.apply(snapshot.generation(), message);
            String detail = message instanceof ForgeRuntimeCodec.DimensionRegister dimension
                    ? "DimensionRegister id=" + dimension.dimensionId() + " provider=" + dimension.providerId()
                    : "FluidIdMap count=" + ((ForgeRuntimeCodec.FluidIdMap) message).ids().size()
                            + " defaults=" + ((ForgeRuntimeCodec.FluidIdMap) message).hasDefaults();
            trace.packet("IN", "FORGE", payload, detail + " phase=" + phase + " (session-local identities)");
        } catch (RuntimeException exception) {
            trace.event("LFB-FORGE-REJECTED: " + exception.getMessage());
        }
    }

    public void handle(byte[] payload, Phase phase, FmlConnectionTrace trace) {
        handle(payload, phase, trace, state.snapshot().generation());
    }
    private void handle(byte[] payload, Phase phase, FmlConnectionTrace trace, long generation) {
        if (!state.accepts(generation)) return;
        try {
            if (payload == null || payload.length > LegacyPayloadReader.MAX_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("Invalid FML payload size");
            }
            int discriminator = FmlRuntimeCodec.discriminator(payload);
            Object decoded;
            switch (discriminator) {
                case FmlRuntimeCodec.COMPLETE_HANDSHAKE -> {
                    var message = FmlRuntimeCodec.parseCompleteHandshake(payload);
                    require(message.trailingBytes() == 0 && message.target() != FmlRuntimeCodec.LegacySide.UNKNOWN,
                            "Invalid CompleteHandshake");
                    trace.packet("IN", "FML", payload, "CompleteHandshake target=" + message.target() + " phase=" + phase);
                    return;
                }
                case FmlRuntimeCodec.OPEN_GUI -> {
                    var message = FmlRuntimeCodec.parseOpenGui(payload);
                    require(message.trailingBytes() == 0, "Invalid legacy OpenGui framing");
                    LegacyRuntimeGuards.requireWindow(message.windowId());
                    decoded = message;
                }
                case FmlRuntimeCodec.ENTITY_SPAWN -> decoded = LegacyEntitySpawnCodec.decode(payload);
                case FmlRuntimeCodec.ENTITY_ADJUST -> {
                    var message = FmlRuntimeCodec.parseEntityAdjust(payload);
                    require(message.trailingBytes() == 0, "Unexpected EntityAdjust trailing bytes");
                    decoded = message;
                }
                default -> {
                    trace.packet("IN", "FML", payload, "LFB-FML-UNKNOWN: " + discriminator + " phase=" + phase);
                    return;
                }
            }
            trace.packet("IN", "FML", payload, FmlRuntimeCodec.discriminatorName(discriminator) + " decoded phase=" + phase);
            if (phase == Phase.CONFIGURATION) {
                enqueue(payload, generation);
                return;
            }
            Minecraft client = Minecraft.getInstance();
            ClientLevel level = client.level;
            var player = client.player;
            client.execute(() -> {
                if (!state.accepts(generation) || level == null || client.level != level
                        || (decoded instanceof FmlRuntimeCodec.OpenGui && client.player != player)) {
                    trace.event("LFB-RUNTIME-STALE: discarded runtime packet after session/world change");
                    return;
                }
                try {
                    if (decoded instanceof FmlRuntimeCodec.OpenGui message) openGui(client, level, message, trace);
                    else if (decoded instanceof LegacyEntitySpawnCodec.Spawn message) spawnEntity(client, level, message, trace);
                    else if (decoded instanceof FmlRuntimeCodec.EntityAdjust message) adjustEntity(level, message, trace);
                } catch (RuntimeException | LinkageError exception) {
                    trace.event("LFB-RUNTIME-ADAPTER-FAILED: " + exception);
                    if (decoded instanceof FmlRuntimeCodec.OpenGui message) closeUnsupportedGui(client, message.windowId());
                }
            });
        } catch (RuntimeException exception) {
            trace.event("LFB-FML-REJECTED: " + exception.getMessage());
        }
    }

    private synchronized void enqueue(byte[] payload, long generation) {
        if (!state.accepts(generation)) return;
        require(pending.size() < MAX_QUEUED_PACKETS && payload.length <= MAX_QUEUED_BYTES - queuedBytes,
                "LFB-RUNTIME-QUEUE-LIMIT: too many world packets before PLAY");
        pending.addLast(payload.clone());
        queuedBytes += payload.length;
    }

    private void openGui(Minecraft client, ClientLevel level, FmlRuntimeCodec.OpenGui message, FmlConnectionTrace trace) {
        if (client.player == null) return;
        var binding = bindings.gui(message.modId(), message.modGuiId()).orElse(null);
        if (binding == null) {
            trace.event("LFB-GUI-UNMAPPED: " + message.modId() + ':' + message.modGuiId() + "; window=" + message.windowId());
            closeUnsupportedGui(client, message.windowId());
            return;
        }
        var context = new LegacyRuntimeProvider.GuiContext(client, level, message, state.snapshot());
        var screen = binding.adapter().create(context);
        require(screen != null && screen.getMenu() != null, "GUI adapter returned no container screen");
        LegacyRuntimeGuards.requireMatchingWindow(message.windowId(), screen.getMenu().containerId);
        require(state.accepts(context.session().generation()) && client.level == level, "GUI adapter outlived its session");
        client.player.containerMenu = screen.getMenu();
        client.setScreen(screen);
        trace.event("LFB-GUI-OPENED: " + binding.target() + " window=" + message.windowId());
    }

    private static void closeUnsupportedGui(Minecraft client, int windowId) {
        if (client.getConnection() != null) {
            // Tell the legacy server to close the window it just opened; do not fabricate a menu.
            client.getConnection().send(new ServerboundContainerClosePacket(windowId));
        }
    }

    private void spawnEntity(Minecraft client, ClientLevel level, LegacyEntitySpawnCodec.Spawn message,
                             FmlConnectionTrace trace) {
        var header = message.header();
        var binding = bindings.entity(header.modId(), header.modEntityTypeId()).orElse(null);
        if (binding == null) {
            trace.event("LFB-ENTITY-UNMAPPED: " + header.modId() + ':' + header.modEntityTypeId()
                    + "; entity=" + header.entityId() + " watchers=" + message.watchers().size());
            return;
        }
        require(level.getEntity(header.entityId()) == null, "Refusing to overwrite existing entity ID");
        var context = new LegacyRuntimeProvider.EntityContext(client, level, message, state.snapshot());
        Entity entity = binding.adapter().create(context);
        require(entity != null && entity.level() == level, "Entity adapter returned no entity or the wrong world");
        entity.setId(header.entityId());
        entity.setPos(header.x(), header.y(), header.z());
        entity.setYRot(header.yaw());
        entity.setXRot(header.pitch());
        entity.setYHeadRot(header.headYaw());
        entity.syncPacketPositionCodec(header.x(), header.y(), header.z());
        if (message.throwerId() != 0) entity.setDeltaMovement(message.velocityX(), message.velocityY(), message.velocityZ());
        // DataWatcher slots and item IDs are legacy-specific, never modern raw IDs.
        binding.adapter().applyLegacyState(entity, context);
        require(state.accepts(context.session().generation()) && client.level == level
                && entity.getId() == header.entityId() && entity.level() == level
                && level.getEntity(header.entityId()) == null, "Entity adapter changed identity or inserted prematurely");
        level.addEntity(entity);
        trace.event("LFB-ENTITY-SPAWNED: " + binding.target() + " entity=" + header.entityId());
    }

    private static void adjustEntity(ClientLevel level, FmlRuntimeCodec.EntityAdjust message, FmlConnectionTrace trace) {
        Entity entity = level.getEntity(message.entityId());
        if (entity == null) {
            trace.event("EntityAdjustMessage target entity is not present; entity=" + message.entityId());
            return;
        }
        // Original Forge serverPosX/Y/Z semantics: update relative-packet baseline, not teleport.
        entity.syncPacketPositionCodec(message.x(), message.y(), message.z());
        trace.event("EntityAdjustMessage applied to packet-position baseline; entity=" + message.entityId());
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
