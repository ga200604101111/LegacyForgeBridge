package dev.longyu.legacyforgebridge;

import dev.longyu.legacyforgebridge.network.FmlConnectionTrace;
import dev.longyu.legacyforgebridge.network.FmlHandshakeClient;
import dev.longyu.legacyforgebridge.network.FmlMappedPayload;
import dev.longyu.legacyforgebridge.network.FmlRuntimeClient;
import dev.longyu.legacyforgebridge.network.FmlWireCodec;
import dev.longyu.legacyforgebridge.protocol.LegacyPluginChannelMappings;
import dev.longyu.legacyforgebridge.protocol.ViaFabricPlusBackend;
import dev.longyu.legacyforgebridge.protocol.ViaLegacyFmlTransport;
import dev.longyu.legacyforgebridge.convert.runtime.ConvertedLegacyProcessorScreen;
import dev.longyu.legacyforgebridge.convert.runtime.LegacyProcessorMenuSupport;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.gui.screens.MenuScreens;

/** Client networking entrypoint for the Forge/FML bridge. */
public final class LegacyForgeBridgeClient implements ClientModInitializer {
    private final FmlHandshakeClient handshake = new FmlHandshakeClient();
    private final FmlRuntimeClient runtime = new FmlRuntimeClient();
    private volatile boolean directRegistrationSent;

    @Override
    public void onInitializeClient() {
        registerPayloadTypes();
        LegacyProcessorMenuSupport.bootstrap();
        MenuScreens.register(LegacyProcessorMenuSupport.type(), ConvertedLegacyProcessorScreen::new);

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
            if (!ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target()) return;
            FmlConnectionTrace trace = FmlConnectionTrace.INSTANCE;
            trace.startIfNeeded("received mapped FML|HS during CONFIGURATION; target="
                    + ViaFabricPlusBackend.INSTANCE.currentProtocolName()
                    + " (" + ViaFabricPlusBackend.INSTANCE.currentProtocolId() + ")");
            trace.event("phase=CONFIGURATION");
            trace.event("ViaVersion Forge channel mappings installed=" + LegacyPluginChannelMappings.installed());
            byte[] data = payload.data();
            try {
                ensureDirectClientRegistrationForServerHello(data, trace);
                handshake.handle(data, bytes -> sendConfigurationHandshake(bytes, trace), trace);
            } catch (RuntimeException exception) {
                trace.event("Forge/FML CONFIGURATION handshake processing failed: " + exception);
                LegacyForgeBridge.LOGGER.error("Forge/FML CONFIGURATION handshake processing failed", exception);
            }
        });

        ClientConfigurationNetworking.registerGlobalReceiver(FmlMappedPayload.FML, (payload, context) -> {
            if (!ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target()) return;
            FmlConnectionTrace trace = FmlConnectionTrace.INSTANCE;
            trace.startIfNeeded("received mapped FML runtime payload during CONFIGURATION");
            runtime.handle(payload.data(), FmlRuntimeClient.Phase.CONFIGURATION, trace);
        });

        ClientConfigurationNetworking.registerGlobalReceiver(FmlMappedPayload.FORGE, (payload, context) -> {
            if (!ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target()) return;
            FmlConnectionTrace trace = FmlConnectionTrace.INSTANCE;
            trace.startIfNeeded("received mapped FORGE runtime payload during CONFIGURATION");
            trace.packet("IN", "FORGE", payload.data(), "FORGE runtime payload during CONFIGURATION (not handled yet)");
        });

        ClientConfigurationConnectionEvents.INIT.register((handler, client) -> {
            directRegistrationSent = false;
            if (!ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target()) return;
            FmlConnectionTrace trace = FmlConnectionTrace.INSTANCE;
            trace.startIfNeeded("client CONFIGURATION INIT; target=" + ViaFabricPlusBackend.INSTANCE.currentProtocolName());
            trace.event("ClientConfigurationConnectionEvents.INIT fired");
            trace.event("ViaVersion Forge channel mappings installed=" + LegacyPluginChannelMappings.installed());
            trace.event("configuration canSend(mapped FML|HS)=" + ClientConfigurationNetworking.canSend(FmlMappedPayload.FML_HS));
            trace.event("direct transport will resolve the active ViaVersion UserConnection lazily");
        });

        ClientConfigurationConnectionEvents.DISCONNECT.register((handler, client) -> {
            FmlConnectionTrace trace = FmlConnectionTrace.INSTANCE;
            if (trace.sessionActive()) trace.event("CONFIGURATION disconnected with handshakeState=" + handshake.state());
            handshake.reset();
            directRegistrationSent = false;
            trace.endSession("configuration disconnected");
        });
    }

    private void registerPlayNetworking() {
        ClientPlayNetworking.registerGlobalReceiver(FmlMappedPayload.FML_HS, (payload, context) -> {
            if (!ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target()) return;
            FmlConnectionTrace trace = FmlConnectionTrace.INSTANCE;
            trace.startIfNeeded("received mapped FML|HS during PLAY; target="
                    + ViaFabricPlusBackend.INSTANCE.currentProtocolName()
                    + " (" + ViaFabricPlusBackend.INSTANCE.currentProtocolId() + ")");
            trace.event("phase=PLAY");
            trace.event("ViaVersion Forge channel mappings installed=" + LegacyPluginChannelMappings.installed());
            byte[] data = payload.data();
            try {
                ensureDirectClientRegistrationForServerHello(data, trace);
                handshake.handle(data, bytes -> sendPlayHandshake(bytes, trace), trace);
            } catch (RuntimeException exception) {
                trace.event("Forge/FML PLAY handshake processing failed: " + exception);
                LegacyForgeBridge.LOGGER.error("Forge/FML PLAY handshake processing failed", exception);
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(FmlMappedPayload.FML, (payload, context) -> {
            if (!ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target()) return;
            FmlConnectionTrace trace = FmlConnectionTrace.INSTANCE;
            trace.startIfNeeded("received mapped FML runtime payload during PLAY");
            runtime.handle(payload.data(), FmlRuntimeClient.Phase.PLAY, trace);
        });

        ClientPlayNetworking.registerGlobalReceiver(FmlMappedPayload.FORGE, (payload, context) -> {
            if (!ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target()) return;
            FmlConnectionTrace trace = FmlConnectionTrace.INSTANCE;
            trace.startIfNeeded("received mapped FORGE runtime payload during PLAY");
            trace.packet("IN", "FORGE", payload.data(), "FORGE runtime payload during PLAY (not handled yet)");
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target()) {
                FmlConnectionTrace trace = FmlConnectionTrace.INSTANCE;
                trace.startIfNeeded("client PLAY JOIN; target=" + ViaFabricPlusBackend.INSTANCE.currentProtocolName());
                trace.event("ClientPlayConnectionEvents.JOIN fired");
                trace.event("ViaVersion Forge channel mappings installed=" + LegacyPluginChannelMappings.installed());
                trace.event("play canSend(mapped FML|HS)=" + ClientPlayNetworking.canSend(FmlMappedPayload.FML_HS));
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            FmlConnectionTrace trace = FmlConnectionTrace.INSTANCE;
            if (trace.sessionActive()) trace.event("PLAY disconnected with handshakeState=" + handshake.state());
            handshake.reset();
            directRegistrationSent = false;
            trace.endSession("play disconnected");
        });
    }

    private void ensureDirectClientRegistrationForServerHello(byte[] data, FmlConnectionTrace trace) {
        if (directRegistrationSent || handshake.state() != FmlHandshakeClient.State.WAITING_SERVER_HELLO) return;
        if (FmlWireCodec.discriminator(data) != FmlWireCodec.SERVER_HELLO) return;
        boolean sent = ViaLegacyFmlTransport.INSTANCE.sendClientRegistration(trace);
        directRegistrationSent = sent;
        trace.event("Direct legacy client REGISTER scheduled=" + sent);
    }

    private void registerPayloadTypes() {
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
        if (ViaLegacyFmlTransport.INSTANCE.sendHandshake(bytes, trace)) {
            trace.event("FML|HS sent using direct ViaVersion PLAY transport (source phase=CONFIGURATION)");
            return;
        }
        trace.event("Direct ViaVersion transport unavailable; falling back to Fabric CONFIGURATION sender");
        boolean canSend = ClientConfigurationNetworking.canSend(FmlMappedPayload.FML_HS);
        trace.event("send FML|HS during CONFIGURATION fallback requested; canSend=" + canSend + " bytes=" + bytes.length);
        if (!canSend) {
            trace.event("Cannot send mapped FML|HS during CONFIGURATION fallback. Legacy REGISTER/channel translation has not marked this channel sendable.");
            return;
        }
        ClientConfigurationNetworking.send(new FmlMappedPayload(FmlMappedPayload.FML_HS, bytes));
    }

    private void sendPlayHandshake(byte[] bytes, FmlConnectionTrace trace) {
        if (ViaLegacyFmlTransport.INSTANCE.sendHandshake(bytes, trace)) {
            trace.event("FML|HS sent using direct ViaVersion PLAY transport (source phase=PLAY)");
            return;
        }
        trace.event("Direct ViaVersion transport unavailable; falling back to Fabric PLAY sender");
        boolean canSend = ClientPlayNetworking.canSend(FmlMappedPayload.FML_HS);
        trace.event("send FML|HS during PLAY fallback requested; canSend=" + canSend + " bytes=" + bytes.length);
        if (!canSend) {
            trace.event("Cannot send mapped FML|HS during PLAY fallback. Legacy REGISTER/channel translation has not marked this channel sendable.");
            return;
        }
        ClientPlayNetworking.send(new FmlMappedPayload(FmlMappedPayload.FML_HS, bytes));
    }
}
