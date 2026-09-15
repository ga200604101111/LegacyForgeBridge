package dev.yinghuang.legacyforgebridge.protocol;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.ProtocolInfo;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.Protocol;
import com.viaversion.viaversion.api.protocol.packet.PacketType;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Types;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.network.FmlConnectionTrace;

import java.nio.charset.StandardCharsets;

/**
 * Sends legacy Forge plugin messages directly through ViaVersion's PLAY protocol pipeline.
 *
 * <p>Forge 1.7.10 performs FML negotiation before the modern client has entered PLAY. Receiving
 * the translated payload through Fabric's CONFIGURATION API works, but sending a modern
 * configuration custom payload is not guaranteed to become the 1.7.10 C17 CustomPayload packet
 * that Forge is waiting for. This transport therefore starts a serverbound CUSTOM_PAYLOAD at the
 * 1.13 side of ViaVersion's 1.12.2->1.13 protocol and lets ViaVersion translate it down to the
 * legacy server.</p>
 */
public final class ViaLegacyFmlTransport {
    public static final ViaLegacyFmlTransport INSTANCE = new ViaLegacyFmlTransport();

    private static final String PROTOCOL_CLASS =
            "com.viaversion.viaversion.protocols.v1_12_2to1_13.Protocol1_12_2To1_13";
    private static final String SERVERBOUND_PACKETS_CLASS =
            "com.viaversion.viaversion.protocols.v1_12_2to1_13.packet.ServerboundPackets1_13";

    private static final String MODERN_FML_HS = "legacyforgebridge:fml_hs";
    private static final String MODERN_REGISTER = "minecraft:register";
    private static final byte[] CLIENT_CHANNEL_REGISTRATION = String.join(
            "\0",
            "legacyforgebridge:fml_hs",
            "legacyforgebridge:fml",
            "legacyforgebridge:forge"
    ).getBytes(StandardCharsets.UTF_8);

    private volatile Handles handles;

    private ViaLegacyFmlTransport() {
    }

    /** Sends the Forge/FML client channel registration through the same legacy PLAY pipeline. */
    public boolean sendClientRegistration(FmlConnectionTrace trace) {
        trace.packet(
                "OUT-DIRECT",
                "REGISTER",
                CLIENT_CHANNEL_REGISTRATION,
                "Client channel registration aliases: legacyforgebridge:fml_hs, legacyforgebridge:fml, legacyforgebridge:forge"
        );
        return sendPluginMessage(MODERN_REGISTER, CLIENT_CHANNEL_REGISTRATION, trace);
    }

    /** Sends one FML|HS payload directly through ViaVersion instead of modern configuration networking. */
    public boolean sendHandshake(byte[] payload, FmlConnectionTrace trace) {
        return sendPluginMessage(MODERN_FML_HS, payload, trace);
    }

    private boolean sendPluginMessage(
            String modernChannel,
            byte[] payload,
            FmlConnectionTrace trace
    ) {
        if (!LegacyPluginChannelMappings.installed()) {
            trace.event("Direct ViaVersion transport unavailable: legacy channel aliases are not installed");
            return false;
        }

        try {
            UserConnection user = findLegacyClientConnection(trace);
            if (user == null) {
                trace.event("Direct ViaVersion transport unavailable: no active 1.7.10 client UserConnection found");
                return false;
            }

            Handles handles = handles();
            PacketWrapper wrapper = PacketWrapper.create(handles.customPayloadPacket(), user);
            wrapper.write(Types.STRING, modernChannel);
            wrapper.write(Types.SERVERBOUND_CUSTOM_PAYLOAD_DATA, payload);

            // false = include Protocol1_12_2To1_13 itself. That protocol performs the crucial
            // modern channel -> old channel rewrite (legacyforgebridge:fml_hs -> FML|HS).
            wrapper.scheduleSendToServer(handles.protocolClass(), false);
            trace.event(
                    "Direct ViaVersion serverbound CUSTOM_PAYLOAD scheduled: channel=" + modernChannel
                            + " bytes=" + payload.length
                            + " startProtocol=Protocol1_12_2To1_13 includeCurrent=true"
            );
            return true;
        } catch (Exception | LinkageError exception) {
            trace.event("Direct ViaVersion transport failed: " + exception);
            LegacyForgeBridge.LOGGER.error("Direct ViaVersion Forge/FML transport failed", exception);
            return false;
        }
    }

    private UserConnection findLegacyClientConnection(FmlConnectionTrace trace) {
        if (!Via.isLoaded()) {
            return null;
        }

        UserConnection fallback = null;
        int activeClientConnections = 0;

        for (UserConnection connection : Via.getManager().getConnectionManager().getConnections()) {
            if (!connection.isClientSide() || connection.getChannel() == null || !connection.getChannel().isActive()) {
                continue;
            }

            activeClientConnections++;
            if (fallback == null) {
                fallback = connection;
            }

            ProtocolInfo info = connection.getProtocolInfo();
            ProtocolVersion serverVersion = info != null ? info.serverProtocolVersion() : null;
            if (serverVersion != null
                    && LegacyProtocolVersions.isMinecraft1710(serverVersion.getVersion())) {
                trace.event(
                        "Direct ViaVersion transport selected active client UserConnection: serverProtocol="
                                + serverVersion.getName() + " (" + serverVersion.getVersion() + ")"
                );
                return connection;
            }
        }

        if (fallback != null) {
            ProtocolInfo info = fallback.getProtocolInfo();
            ProtocolVersion serverVersion = info != null ? info.serverProtocolVersion() : null;
            trace.event(
                    "Direct ViaVersion transport using sole/fallback active client UserConnection: activeClientConnections="
                            + activeClientConnections
                            + " serverProtocol=" + (serverVersion == null ? "unknown" : serverVersion.getName())
            );
        } else {
            trace.event("Direct ViaVersion transport found activeClientConnections=0");
        }
        return fallback;
    }

    private Handles handles() throws ReflectiveOperationException {
        Handles current = handles;
        if (current != null) {
            return current;
        }

        synchronized (this) {
            if (handles != null) {
                return handles;
            }

            Class<?> packetEnumClass = Class.forName(SERVERBOUND_PACKETS_CLASS);
            if (!packetEnumClass.isEnum()) {
                throw new IllegalStateException("ViaVersion serverbound packet class is not an enum: " + packetEnumClass);
            }

            @SuppressWarnings({"rawtypes", "unchecked"})
            Object customPayload = Enum.valueOf((Class<? extends Enum>) packetEnumClass, "CUSTOM_PAYLOAD");
            if (!(customPayload instanceof PacketType packetType)) {
                throw new IllegalStateException("ViaVersion CUSTOM_PAYLOAD does not implement PacketType");
            }

            Class<?> rawProtocolClass = Class.forName(PROTOCOL_CLASS);
            if (!Protocol.class.isAssignableFrom(rawProtocolClass)) {
                throw new IllegalStateException("Unexpected ViaVersion protocol class: " + rawProtocolClass);
            }

            @SuppressWarnings("unchecked")
            Class<? extends Protocol> protocolClass = (Class<? extends Protocol>) rawProtocolClass;
            handles = new Handles(protocolClass, packetType);
            return handles;
        }
    }

    static byte[] clientChannelRegistrationForTest() {
        return CLIENT_CHANNEL_REGISTRATION.clone();
    }

    private record Handles(Class<? extends Protocol> protocolClass, PacketType customPayloadPacket) {
    }
}
