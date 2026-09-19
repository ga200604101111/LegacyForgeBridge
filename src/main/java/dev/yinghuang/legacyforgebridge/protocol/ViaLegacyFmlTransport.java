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

/**
 * Sends Forge 1.7.10 plugin messages to the active ViaVersion client connection.
 *
 * <p>The preferred path emits the final 1.7.10 C17 CustomPayload packet directly. FML performs
 * its negotiation before a modern client reaches PLAY, so routing a synthetic 1.13 payload through
 * the whole Via pipeline can be scheduled successfully while still never reaching the legacy
 * server in the form expected by its handshake decoder. Direct legacy packets remove that
 * ambiguity. The mapped 1.13 path remains a fail-safe for runtimes that reject raw packet writes.</p>
 */
public final class ViaLegacyFmlTransport {
    public static final ViaLegacyFmlTransport INSTANCE = new ViaLegacyFmlTransport();

    private static final String PROTOCOL_CLASS =
            "com.viaversion.viaversion.protocols.v1_12_2to1_13.Protocol1_12_2To1_13";
    private static final String SERVERBOUND_PACKETS_CLASS =
            "com.viaversion.viaversion.protocols.v1_12_2to1_13.packet.ServerboundPackets1_13";
    private static final String MODERN_FML_HS = "legacyforgebridge:fml_hs";
    private static final String MODERN_REGISTER = "minecraft:register";
    private static final byte[] MODERN_CLIENT_CHANNEL_REGISTRATION = String.join(
            "\0",
            "legacyforgebridge:fml_hs",
            "legacyforgebridge:fml",
            "legacyforgebridge:forge"
    ).getBytes(java.nio.charset.StandardCharsets.UTF_8);

    private volatile Handles handles;

    private ViaLegacyFmlTransport() { }

    /** Sends the exact channel registration emitted by a Forge 1.7.10 client. */
    public boolean sendClientRegistration(FmlConnectionTrace trace) {
        UserConnection user = findLegacyClientConnection(trace);
        if (user == null) {
            trace.event("Direct legacy transport unavailable: no active 1.7.10 client UserConnection found");
            return false;
        }

        byte[] registration = LegacyFmlWireConstants.clientChannelRegistration();
        trace.packet(
                "OUT-RAW-1.7.10",
                LegacyFmlWireConstants.REGISTER_CHANNEL,
                registration,
                "Client channel registration: FML|HS, FML, FORGE"
        );
        if (sendRawLegacyPluginMessage(
                user, LegacyFmlWireConstants.REGISTER_CHANNEL, registration, trace)) {
            return true;
        }

        trace.event("Raw 1.7.10 REGISTER failed; attempting mapped ViaVersion fallback");
        return sendMappedPluginMessage(user, MODERN_REGISTER, MODERN_CLIENT_CHANNEL_REGISTRATION, trace);
    }

    /** Sends one FML|HS payload, preferring an exact 1.7.10 C17 packet. */
    public boolean sendHandshake(byte[] payload, FmlConnectionTrace trace) {
        UserConnection user = findLegacyClientConnection(trace);
        if (user == null) {
            trace.event("Direct legacy transport unavailable: no active 1.7.10 client UserConnection found");
            return false;
        }

        if (sendRawLegacyPluginMessage(
                user, LegacyFmlWireConstants.HANDSHAKE_CHANNEL, payload, trace)) {
            return true;
        }

        trace.event("Raw 1.7.10 FML|HS send failed; attempting mapped ViaVersion fallback");
        return sendMappedPluginMessage(user, MODERN_FML_HS, payload, trace);
    }

    private boolean sendRawLegacyPluginMessage(
            UserConnection user,
            String legacyChannel,
            byte[] payload,
            FmlConnectionTrace trace
    ) {
        if (legacyChannel.length() > 20) {
            trace.event("Raw 1.7.10 custom payload rejected locally: channel exceeds 20 characters: " + legacyChannel);
            return false;
        }
        if (payload.length > Short.MAX_VALUE) {
            trace.event("Raw 1.7.10 custom payload rejected locally: payload exceeds signed-short length: " + payload.length);
            return false;
        }

        try {
            @SuppressWarnings("deprecation")
            PacketWrapper wrapper = PacketWrapper.create(
                    LegacyFmlWireConstants.CUSTOM_PAYLOAD_PACKET_ID, null, user);
            wrapper.write(Types.STRING, legacyChannel);
            wrapper.write(Types.SHORT, (short) payload.length);
            wrapper.write(Types.REMAINING_BYTES, payload);
            wrapper.scheduleSendToServerRaw();
            trace.event(
                    "Raw Minecraft 1.7.10 C17 CustomPayload scheduled: channel=" + legacyChannel
                            + " bytes=" + payload.length + " packetId=0x17"
            );
            return true;
        } catch (Exception | LinkageError exception) {
            trace.event("Raw Minecraft 1.7.10 C17 transport failed: " + exception);
            LegacyForgeBridge.LOGGER.error("Raw Minecraft 1.7.10 Forge/FML transport failed", exception);
            return false;
        }
    }

    private boolean sendMappedPluginMessage(
            UserConnection user,
            String modernChannel,
            byte[] payload,
            FmlConnectionTrace trace
    ) {
        if (!LegacyPluginChannelMappings.installed()) {
            trace.event("Mapped ViaVersion fallback unavailable: legacy channel aliases are not installed");
            return false;
        }

        try {
            Handles handles = handles();
            PacketWrapper wrapper = PacketWrapper.create(handles.customPayloadPacket(), user);
            wrapper.write(Types.STRING, modernChannel);
            wrapper.write(Types.SERVERBOUND_CUSTOM_PAYLOAD_DATA, payload);
            wrapper.scheduleSendToServer(handles.protocolClass(), false);
            trace.event(
                    "Mapped ViaVersion CUSTOM_PAYLOAD scheduled: channel=" + modernChannel
                            + " bytes=" + payload.length
                            + " startProtocol=Protocol1_12_2To1_13 skipCurrentPipeline=false"
            );
            return true;
        } catch (Exception | LinkageError exception) {
            trace.event("Mapped ViaVersion transport failed: " + exception);
            LegacyForgeBridge.LOGGER.error("Mapped ViaVersion Forge/FML transport failed", exception);
            return false;
        }
    }

    private UserConnection findLegacyClientConnection(FmlConnectionTrace trace) {
        if (!Via.isLoaded()) return null;

        UserConnection fallback = null;
        int activeClientConnections = 0;
        for (UserConnection connection : Via.getManager().getConnectionManager().getConnections()) {
            if (!connection.isClientSide() || connection.getChannel() == null || !connection.getChannel().isActive()) {
                continue;
            }
            activeClientConnections++;
            if (fallback == null) fallback = connection;

            ProtocolInfo info = connection.getProtocolInfo();
            ProtocolVersion serverVersion = info != null ? info.serverProtocolVersion() : null;
            if (serverVersion != null && LegacyProtocolVersions.isMinecraft1710(serverVersion.getVersion())) {
                trace.event(
                        "Legacy transport selected active client UserConnection: serverProtocol="
                                + serverVersion.getName() + " (" + serverVersion.getVersion() + ")"
                );
                return connection;
            }
        }

        if (fallback != null) {
            ProtocolInfo info = fallback.getProtocolInfo();
            ProtocolVersion serverVersion = info != null ? info.serverProtocolVersion() : null;
            trace.event(
                    "Legacy transport using sole/fallback active client UserConnection: activeClientConnections="
                            + activeClientConnections
                            + " serverProtocol=" + (serverVersion == null ? "unknown" : serverVersion.getName())
            );
        } else {
            trace.event("Legacy transport found activeClientConnections=0");
        }
        return fallback;
    }

    private Handles handles() throws ReflectiveOperationException {
        Handles current = handles;
        if (current != null) return current;

        synchronized (this) {
            if (handles != null) return handles;

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

    private record Handles(Class<? extends Protocol> protocolClass, PacketType customPayloadPacket) { }
}
