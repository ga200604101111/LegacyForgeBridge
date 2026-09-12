package dev.longyu.legacyforgebridge.network;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Minimal client-side Forge 1.7.10 handshake driver for clean Forge servers.
 *
 * <p>Mod conversion/compatibility is intentionally out of scope here. The client advertises only
 * the built-in FML network identity required by a clean 1.7.10 Forge server.</p>
 */
public final class FmlHandshakeClient {
    public enum State {
        WAITING_SERVER_HELLO,
        WAITING_SERVER_MOD_LIST,
        WAITING_REGISTRY_DATA,
        WAITING_SERVER_ACK_AFTER_REGISTRY,
        WAITING_FINAL_SERVER_ACK,
        COMPLETE,
        FAILED
    }

    private static final Map<String, String> CLEAN_CLIENT_MOD_LIST;

    static {
        Map<String, String> mods = new LinkedHashMap<>();
        mods.put("FML", "7.10.99.99");
        CLEAN_CLIENT_MOD_LIST = Map.copyOf(mods);
    }

    private State state = State.WAITING_SERVER_HELLO;
    private FmlWireCodec.ServerHello serverHello;
    private Map<String, String> serverMods = Map.of();
    private FmlWireCodec.ModIdData registryData;

    public State state() {
        return state;
    }

    public FmlWireCodec.ServerHello serverHello() {
        return serverHello;
    }

    public Map<String, String> serverMods() {
        return serverMods;
    }

    public FmlWireCodec.ModIdData registryData() {
        return registryData;
    }

    public void reset() {
        state = State.WAITING_SERVER_HELLO;
        serverHello = null;
        serverMods = Map.of();
        registryData = null;
    }

    public void handle(byte[] payload, Consumer<byte[]> sender, FmlConnectionTrace trace) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(trace, "trace");

        try {
            int discriminator = FmlWireCodec.discriminator(payload);
            switch (discriminator) {
                case FmlWireCodec.SERVER_HELLO -> onServerHello(payload, sender, trace);
                case FmlWireCodec.MOD_LIST -> onServerModList(payload, sender, trace);
                case FmlWireCodec.MOD_ID_DATA -> onModIdData(payload, sender, trace);
                case FmlWireCodec.HANDSHAKE_ACK -> onServerAck(payload, sender, trace);
                case FmlWireCodec.HANDSHAKE_RESET -> {
                    trace.packet("IN", "FML|HS", payload, "HandshakeReset");
                    State previous = state;
                    reset();
                    trace.state(previous.name(), state.name(), "HandshakeReset");
                }
                default -> trace.packet(
                        "IN",
                        "FML|HS",
                        payload,
                        "Unknown handshake discriminator; packet left unhandled"
                );
            }
        } catch (RuntimeException exception) {
            State previous = state;
            state = State.FAILED;
            trace.event("Handshake decode/transition failure: " + exception);
            trace.state(previous.name(), state.name(), "exception");
            throw exception;
        }
    }

    private void onServerHello(byte[] payload, Consumer<byte[]> sender, FmlConnectionTrace trace) {
        require(State.WAITING_SERVER_HELLO, "ServerHello");
        serverHello = FmlWireCodec.parseServerHello(payload);

        trace.packet(
                "IN",
                "FML|HS",
                payload,
                "ServerHello\nprotocol=" + serverHello.protocolVersion()
                        + "\noverrideDimension=" + serverHello.overrideDimension()
                        + "\ntrailingBytes=" + serverHello.trailingBytes()
        );

        if (serverHello.protocolVersion() != FmlWireCodec.FML_PROTOCOL) {
            trace.event("Server FML protocol differs from expected protocol 2; continuing for diagnostics");
        }

        send(sender, trace, FmlWireCodec.encodeClientHello(), "ClientHello protocol=" + FmlWireCodec.FML_PROTOCOL);
        send(sender, trace, FmlWireCodec.encodeModList(CLEAN_CLIENT_MOD_LIST), "Client ModList " + CLEAN_CLIENT_MOD_LIST);

        transition(State.WAITING_SERVER_MOD_LIST, trace, "ServerHello handled");
    }

    private void onServerModList(byte[] payload, Consumer<byte[]> sender, FmlConnectionTrace trace) {
        require(State.WAITING_SERVER_MOD_LIST, "Server ModList");
        serverMods = FmlWireCodec.parseModList(payload);
        trace.packet(
                "IN",
                "FML|HS",
                payload,
                "Server ModList count=" + serverMods.size() + "\nmods=" + serverMods
        );

        send(sender, trace, FmlWireCodec.encodeAck(2), "HandshakeAck phase=2 WAITING_SERVER_DATA");
        transition(State.WAITING_REGISTRY_DATA, trace, "Server ModList accepted for clean-server probe");
    }

    private void onModIdData(byte[] payload, Consumer<byte[]> sender, FmlConnectionTrace trace) {
        require(State.WAITING_REGISTRY_DATA, "ModIdData");
        registryData = FmlWireCodec.parseModIdData(payload);
        trace.packet(
                "IN",
                "FML|HS",
                payload,
                "ModIdData registryEntries=" + registryData.ids().size()
                        + " blockSubstitutions=" + registryData.blockSubstitutions().size()
                        + " itemSubstitutions=" + registryData.itemSubstitutions().size()
        );
        trace.registry(registryData);

        send(sender, trace, FmlWireCodec.encodeAck(3), "HandshakeAck phase=3 WAITING_SERVER_COMPLETE");
        transition(State.WAITING_SERVER_ACK_AFTER_REGISTRY, trace, "Registry data received");
    }

    private void onServerAck(byte[] payload, Consumer<byte[]> sender, FmlConnectionTrace trace) {
        int serverPhase = FmlWireCodec.parseAck(payload);
        trace.packet("IN", "FML|HS", payload, "Server HandshakeAck phase=" + serverPhase);

        if (state == State.WAITING_SERVER_ACK_AFTER_REGISTRY) {
            send(sender, trace, FmlWireCodec.encodeAck(4), "HandshakeAck phase=4 PENDING_COMPLETE");
            transition(State.WAITING_FINAL_SERVER_ACK, trace, "first server ack after registry");
            return;
        }

        if (state == State.WAITING_FINAL_SERVER_ACK) {
            send(sender, trace, FmlWireCodec.encodeAck(5), "HandshakeAck phase=5 COMPLETE");
            transition(State.COMPLETE, trace, "final server ack");
            trace.event("Forge/FML handshake reached COMPLETE");
            return;
        }

        trace.event("Unexpected server HandshakeAck phase=" + serverPhase + " while state=" + state);
    }

    private void send(Consumer<byte[]> sender, FmlConnectionTrace trace, byte[] payload, String parsed) {
        trace.packet("OUT", "FML|HS", payload, parsed);
        sender.accept(payload);
    }

    private void transition(State next, FmlConnectionTrace trace, String cause) {
        State previous = state;
        state = next;
        trace.state(previous.name(), next.name(), cause);
    }

    private void require(State required, String operation) {
        if (state != required) {
            throw new IllegalStateException(
                    "Cannot handle " + operation + " while FML handshake state is " + state + "; expected " + required
            );
        }
    }
}
