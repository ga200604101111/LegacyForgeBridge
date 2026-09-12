package dev.longyu.legacyforgebridge;

import dev.longyu.legacyforgebridge.network.FmlConnectionTrace;
import dev.longyu.legacyforgebridge.network.FmlHandshakeClient;
import dev.longyu.legacyforgebridge.network.FmlMappedPayload;
import dev.longyu.legacyforgebridge.protocol.LegacyPluginChannelMappings;
import dev.longyu.legacyforgebridge.protocol.ViaFabricPlusBackend;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Client networking entrypoint for the Forge/FML bridge. */
public final class LegacyForgeBridgeClient implements ClientModInitializer {
    private final FmlHandshakeClient handshake = new FmlHandshakeClient();

    @Override
    public void onInitializeClient() {
        registerPayloadTypes();

        // ViaFabricPlus initializes ViaVersion asynchronously. Never force-load the 1.12->1.13
        // protocol class before ViaVersion reports that protocols are initialized. The first call
        // is opportunistic; the tick callback retries quietly until it succeeds.
        LegacyPluginChannelMappings.installIfReady();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!LegacyPluginChannelMappings.installed()) {
                LegacyPluginChannelMappings.installIfReady();
            }
        });

        registerConfigurationNetworking();
        registerPlayNetworking();

        LegacyForgeBridge.LOGGER.info("Legacy Forge client networking initialized");
    }

    private void registerConfigurationNetworking() {
        ClientConfigurationNetworking.registerGlobalReceiver(FmlMappedPayload.FML_HS, (payload, context) -> {
            if (!ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target()) {
                return;
            }

            FmlConnectionTrace trace = FmlConnectionTrace.INSTANCE;
            trace.startIfNeeded(
                    "received mapped FML|HS during CONFIGURATION; target="
                            + ViaFabricPlusBackend.INSTANCE.currentProtocolName()
                            + " (" + ViaFabricPlusBackend.INSTANCE.currentProtocolId() + ")"
            );
            trace.event("phase=CONFIGURATION");
            trace.event("ViaVersion Forge channel mappings installed=" + LegacyPluginChannelMappings.installed());

            byte[] data = payload.data();
            try {
                handshake.handle(data, bytes -> sendConfigurationHandshake(bytes, trace), trace);
            } catch (RuntimeException exception) {
                trace.event("Forge/FML CONFIGURATION handshake processing failed: " + exception);
                LegacyForgeBridge.LOGGER.error("Forge/FML CONFIGURATION handshake processing failed", exception);
            }
        });

        ClientConfigurationNetworking.registerGlobalReceiver(FmlMappedPayload.FML, (payload, context) -> {
            if (!ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target()) {
                return;
            }
            FmlConnectionTrace trace = FmlConnectionTrace.INSTANCE;
            trace.startIfNeeded("received mapped FML runtime payload during CONFIGURATION");
            trace.packet("IN", "FML", payload.data(), "FML runtime payload during CONFIGURATION (not handled yet)");
        });

        ClientConfigurationNetworking.registerGlobalReceiver(FmlMappedPayload.FORGE, (payload, context) -> {
            if (!ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target()) {
                return;
            }
            FmlConnectionTrace trace = FmlConnectionTrace.INSTANCE;
            trace.startIfNeeded("received mapped FORGE runtime payload during CONFIGURATION");
            trace.packet("IN", "FORGE", payload.data(), "FORGE runtime payload during CONFIGURATION (not handled yet)");
        });

        ClientConfigurationConnectionEvents.INIT.register((handler, client) -> {
            if (!ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target()) {
                return;
            }
            FmlConnectionTrace trace = FmlConnectionTrace.INSTANCE;
            trace.startIfNeeded(
                    "client CONFIGURATION INIT; target=" + ViaFabricPlusBackend.INSTANCE.currentProtocolName()
            );
            trace.event("ClientConfigurationConnectionEvents.INIT fired");
            trace.event("ViaVersion Forge channel mappings installed=" + LegacyPluginChannelMappings.installed());
            trace.event("configuration canSend(mapped FML|HS)="
                    + ClientConfigurationNetworking.canSend(FmlMappedPayload.FML_HS));
        });

        ClientConfigurationConnectionEvents.DISCONNECT.register((handler, client) -> {
            FmlConnectionTrace trace = FmlConnectionTrace.INSTANCE;
            if (trace.sessionActive()) {
                trace.event("CONFIGURATION disconnected with handshakeState=" + handshake.state());
            }
            handshake.reset();
            trace.endSession("configuration disconnected");
        });
    }

    private void registerPlayNetworking() {
        ClientPlayNetworking.registerGlobalReceiver(FmlMappedPayload.FML_HS, (payload, context) -> {
            if (!ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target()) {
                return;
            }

            FmlConnectionTrace trace = FmlConnectionTrace.INSTANCE;
            trace.startIfNeeded(
                    "received mapped FML|HS during PLAY; target="
                            + ViaFabricPlusBackend.INSTANCE.currentProtocolName()
                            + " (" + ViaFabricPlusBackend.INSTANCE.currentProtocolId() + ")"
            );
            trace.event("phase=PLAY");
            trace.event("ViaVersion Forge channel mappings installed=" + LegacyPluginChannelMappings.installed());

            byte[] data = payload.data();
            try {
                handshake.handle(data, bytes -> sendPlayHandshake(bytes, trace), trace);
            } catch (RuntimeException exception) {
                trace.event("Forge/FML PLAY handshake processing failed: " + exception);
                LegacyForgeBridge.LOGGER.error("Forge/FML PLAY handshake processing failed", exception);
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(FmlMappedPayload.FML, (payload, context) -> {
            if (!ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target()) {
                return;
            }
            FmlConnectionTrace trace = FmlConnectionTrace.INSTANCE;
            trace.startIfNeeded("received mapped FML runtime payload during PLAY");
            trace.packet("IN", "FML", payload.data(), "FML runtime payload during PLAY (not handled yet)");
        });

        ClientPlayNetworking.registerGlobalReceiver(FmlMappedPayload.FORGE, (payload, context) -> {
            if (!ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target()) {
                return;
            }
            FmlConnectionTrace trace = FmlConnectionTrace.INSTANCE;
            trace.startIfNeeded("received mapped FORGE runtime payload during PLAY");
            trace.packet("IN", "FORGE", payload.data(), "FORGE runtime payload during PLAY (not handled yet)");
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target()) {
                FmlConnectionTrace trace = FmlConnectionTrace.INSTANCE;
                trace.startIfNeeded(
                        "client PLAY JOIN; target=" + ViaFabricPlusBackend.INSTANCE.currentProtocolName()
                );
                trace.event("ClientPlayConnectionEvents.JOIN fired");
                trace.event("ViaVersion Forge channel mappings installed=" + LegacyPluginChannelMappings.installed());
                trace.event("play canSend(mapped FML|HS)=" + ClientPlayNetworking.canSend(FmlMappedPayload.FML_HS));
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            FmlConnectionTrace trace = FmlConnectionTrace.INSTANCE;
            if (trace.sessionActive()) {
                trace.event("PLAY disconnected with handshakeState=" + handshake.state());
            }
            handshake.reset();
            trace.endSession("play disconnected");
        });
    }

    private void registerPayloadTypes() {
        // Forge 1.7.10 begins FML negotiation before its normal JoinGame packet. On a modern
        // client this can arrive while Fabric is still in CONFIGURATION, so the aliases must be
        // registered for both CONFIGURATION and PLAY.
        PayloadTypeRegistry.configurationS2C().register(FmlMappedPayload.FML_HS, FmlMappedPayload.codec(FmlMappedPayload.FML_HS));
        PayloadTypeRegistry.configurationC2S().register(FmlMappedPayload.FML_HS, FmlMappedPayload.codec(FmlMappedPayload.FML_HS));
        PayloadTypeRegistry.configurationS2C().register(FmlMappedPayload.FML, FmlMappedPayload.codec(FmlMappedPayload.FML));
        PayloadTypeRegistry.configurationC2S().register(FmlMappedPayload.FML, FmlMappedPayload.codec(FmlMappedPayload.FML));
        PayloadTypeRegistry.configurationS2C().register(FmlMappedPayload.FORGE, FmlMappedPayload.codec(FmlMappedPayload.FORGE));
        PayloadTypeRegistry.configurationC2S().register(FmlMappedPayload.FORGE, FmlMappedPayload.codec(FmlMappedPayload.FORGE));

        PayloadTypeRegistry.playS2C().register(FmlMappedPayload.FML_HS, FmlMappedPayload.codec(FmlMappedPayload.FML_HS));
        PayloadTypeRegistry.playC2S().register(FmlMappedPayload.FML_HS, FmlMappedPayload.codec(FmlMappedPayload.FML_HS));
        PayloadTypeRegistry.playS2C().register(FmlMappedPayload.FML, FmlMappedPayload.codec(FmlMappedPayload.FML));
        PayloadTypeRegistry.playC2S().register(FmlMappedPayload.FML, FmlMappedPayload.codec(FmlMappedPayload.FML));
        PayloadTypeRegistry.playS2C().register(FmlMappedPayload.FORGE, FmlMappedPayload.codec(FmlMappedPayload.FORGE));
        PayloadTypeRegistry.playC2S().register(FmlMappedPayload.FORGE, FmlMappedPayload.codec(FmlMappedPayload.FORGE));
    }

    private void sendConfigurationHandshake(byte[] bytes, FmlConnectionTrace trace) {
        boolean canSend = ClientConfigurationNetworking.canSend(FmlMappedPayload.FML_HS);
        trace.event("send FML|HS during CONFIGURATION requested; canSend=" + canSend + " bytes=" + bytes.length);

        if (!canSend) {
            trace.event(
                    "Cannot send mapped FML|HS during CONFIGURATION. Legacy REGISTER/channel translation has not marked this channel sendable."
            );
            return;
        }

        try {
            ClientConfigurationNetworking.send(new FmlMappedPayload(FmlMappedPayload.FML_HS, bytes));
        } catch (RuntimeException exception) {
            trace.event("ClientConfigurationNetworking.send failed: " + exception);
            throw exception;
        }
    }

    private void sendPlayHandshake(byte[] bytes, FmlConnectionTrace trace) {
        boolean canSend = ClientPlayNetworking.canSend(FmlMappedPayload.FML_HS);
        trace.event("send FML|HS during PLAY requested; canSend=" + canSend + " bytes=" + bytes.length);

        if (!canSend) {
            trace.event(
                    "Cannot send mapped FML|HS during PLAY. Legacy REGISTER/channel translation has not marked this channel sendable."
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
