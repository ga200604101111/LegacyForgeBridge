package dev.yinghuang.legacyforgebridge.network;

import java.util.Objects;

/**
 * Forge/FML 1.7.x handshake constants and a transport-independent client state machine.
 *
 * <p>The actual custom-payload transport is intentionally kept outside this class so the
 * handshake can later be attached to ViaFabricPlus/ViaVersion without coupling conversion
 * logic to a specific networking implementation.</p>
 */
public final class FmlHandshakeProtocol {
    public static final String HANDSHAKE_CHANNEL = "FML|HS";
    public static final String FML_CHANNEL = "FML";
    public static final String FORGE_CHANNEL = "FORGE";

    public static final int DISCRIMINATOR_SERVER_HELLO = 0;
    public static final int DISCRIMINATOR_CLIENT_HELLO = 1;
    public static final int DISCRIMINATOR_MOD_LIST = 2;
    public static final int DISCRIMINATOR_MOD_ID_DATA = 3;
    public static final int DISCRIMINATOR_HANDSHAKE_ACK = 0xFF;

    private FmlHandshakeProtocol() {
    }

    public enum ClientState {
        NEW,
        SERVER_HELLO_RECEIVED,
        CLIENT_HELLO_SENT,
        MOD_LIST_SENT,
        REGISTRY_DATA_RECEIVED,
        WAITING_FOR_SERVER_ACK,
        COMPLETE,
        FAILED
    }

    public enum AckPhase {
        WAITING_SERVER_DATA(2),
        WAITING_SERVER_COMPLETE(3),
        PENDING_COMPLETE(4),
        COMPLETE(5);

        private final int wireValue;

        AckPhase(int wireValue) {
            this.wireValue = wireValue;
        }

        public int wireValue() {
            return wireValue;
        }
    }

    public static final class ClientSession {
        private ClientState state = ClientState.NEW;
        private int serverProtocolVersion = -1;
        private String failureReason;

        public ClientState state() {
            return state;
        }

        public int serverProtocolVersion() {
            return serverProtocolVersion;
        }

        public String failureReason() {
            return failureReason;
        }

        public void receiveServerHello(int protocolVersion) {
            require(ClientState.NEW);
            if (protocolVersion < 0) {
                fail("Invalid negative FML protocol version: " + protocolVersion);
                return;
            }
            this.serverProtocolVersion = protocolVersion;
            this.state = ClientState.SERVER_HELLO_RECEIVED;
        }

        public void markClientHelloSent() {
            require(ClientState.SERVER_HELLO_RECEIVED);
            state = ClientState.CLIENT_HELLO_SENT;
        }

        public void markModListSent() {
            require(ClientState.CLIENT_HELLO_SENT);
            state = ClientState.MOD_LIST_SENT;
        }

        public void markRegistryDataReceived() {
            if (state != ClientState.MOD_LIST_SENT && state != ClientState.REGISTRY_DATA_RECEIVED) {
                throw invalidTransition("registry data");
            }
            state = ClientState.REGISTRY_DATA_RECEIVED;
        }

        public void markWaitingForServerAck() {
            if (state != ClientState.MOD_LIST_SENT && state != ClientState.REGISTRY_DATA_RECEIVED) {
                throw invalidTransition("waiting for server ack");
            }
            state = ClientState.WAITING_FOR_SERVER_ACK;
        }

        public void markComplete() {
            if (state != ClientState.WAITING_FOR_SERVER_ACK && state != ClientState.REGISTRY_DATA_RECEIVED) {
                throw invalidTransition("complete");
            }
            state = ClientState.COMPLETE;
        }

        public void fail(String reason) {
            this.failureReason = Objects.requireNonNullElse(reason, "Unknown FML handshake failure");
            this.state = ClientState.FAILED;
        }

        private void require(ClientState required) {
            if (state != required) {
                throw invalidTransition(required.name());
            }
        }

        private IllegalStateException invalidTransition(String operation) {
            return new IllegalStateException("Cannot transition FML handshake from " + state + " via " + operation);
        }
    }
}
