package dev.longyu.legacyforgebridge;

import dev.longyu.legacyforgebridge.network.FmlConnectionTrace;
import dev.longyu.legacyforgebridge.network.FmlHandshakeClient;
import dev.longyu.legacyforgebridge.network.FmlMappedPayload;
import dev.longyu.legacyforgebridge.protocol.LegacyPluginChannelMappings;
import dev.longyu.legacyforgebridge.protocol.ViaFabricPlusBackend;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Client networking entrypoint for the Forge/FML bridge. */
public final class LegacyForgeBridgeClient implements ClientModInitializer {
    private final FmlHandshakeClient handshake = new FmlHandshakeClient();

    @Override
    public void onInitializeClient() {
        registerPayloadTypes();

        ClientPlayNetworking.registerGlobalReceiver(FmlMappedPayload.FML_HS, (payload, context) -> {
            if (!ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target()) {
                return;
            }

            FmlConnectionTrace trace = FmlConnectionTrace.INSTANCE;
            trace.startIfNeeded(
                    "received mapped FML|HS payload; target="
                            + ViaFabricPlusBackend.INSTANCE.currentProtocolName()
                            + " (" + ViaFabricPlusBackend.INSTANCE.currentProtocolId() + ")"
            );
            trace.event("ViaVersion Forge channel mappings installed=" + LegacyPluginChannelMappings.installed());

            byte[] data = payload.data();
            try {
                handshake.handle(data, bytes -> sendHandshake(bytes, trace), trace);
            } catch (RuntimeException exception) {
                LegacyForgeBridge.LOGGER.error("Forge/FML handshake processing failed", exception);
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(FmlMappedPayload.FML, (payload, context) -> {
            if (!ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target()) {
                return;
            }
            FmlConnectionTrace trace = FmlConnectionTrace.INSTANCE;
            trace.startIfNeeded("received mapped FML runtime payload");
            trace.packet("IN", "FML", payload.data(), "FML runtime payload (not handled in alpha.3)");
        });

        ClientPlayNetworking.registerGlobalReceiver(FmlMappedPayload.FORGE, (payload, context) -> {
            if (!ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target()) {
                return;
            }
            FmlConnectionTrace trace = FmlConnectionTrace.INSTANCE;
            trace.startIfNeeded("received mapped FORGE runtime payload");
            trace.packet("IN", "FORGE", payload.data(), "FORGE runtime payload (not handled in alpha.3)");
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target()) {
                FmlConnectionTrace trace = FmlConnectionTrace.INSTANCE;
                trace.startIfNeeded(
                        "client play JOIN; target=" + ViaFabricPlusBackend.INSTANCE.currentProtocolName()
                );
                trace.event("ClientPlayConnectionEvents.JOIN fired");
                trace.event("canSend(mapped FML|HS)=" + ClientPlayNetworking.canSend(FmlMappedPayload.FML_HS));
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            FmlConnectionTrace trace = FmlConnectionTrace.INSTANCE;
            if (trace.activePath() != null) {
                trace.event("Disconnected with handshakeState=" + handshake.state());
            }
            handshake.reset();
            trace.close("client disconnected");
        });

        LegacyForgeBridge.LOGGER.info("Legacy Forge client networking initialized");
    }

    private void registerPayloadTypes() {
        PayloadTypeRegistry.playS2C().register(FmlMappedPayload.FML_HS, FmlMappedPayload.codec(FmlMappedPayload.FML_HS));
        PayloadTypeRegistry.playC2S().register(FmlMappedPayload.FML_HS, FmlMappedPayload.codec(FmlMappedPayload.FML_HS));

        PayloadTypeRegistry.playS2C().register(FmlMappedPayload.FML, FmlMappedPayload.codec(FmlMappedPayload.FML));
        PayloadTypeRegistry.playC2S().register(FmlMappedPayload.FML, FmlMappedPayload.codec(FmlMappedPayload.FML));

        PayloadTypeRegistry.playS2C().register(FmlMappedPayload.FORGE, FmlMappedPayload.codec(FmlMappedPayload.FORGE));
        PayloadTypeRegistry.playC2S().register(FmlMappedPayload.FORGE, FmlMappedPayload.codec(FmlMappedPayload.FORGE));
    }

    private void sendHandshake(byte[] bytes, FmlConnectionTrace trace) {
        boolean canSend = ClientPlayNetworking.canSend(FmlMappedPayload.FML_HS);
        trace.event("send FML|HS requested; canSend=" + canSend + " bytes=" + bytes.length);

        if (!canSend) {
            trace.event(
                    "Cannot send mapped FML|HS. The server REGISTER packet was either not translated, not received, or the mapping was installed too late."
            );
            return;
        }

        try {
            ClientPlayNetworking.send(new FmlMappedPayload(FmlMappedPayload.FML_HS, bytes));
        } catch (RuntimeException exception) {
            trace.event("ClientPlayNetworking.send failed: " + exception);
            throw exception;
        }
    }
}
