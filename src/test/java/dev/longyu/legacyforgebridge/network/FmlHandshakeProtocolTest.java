package dev.longyu.legacyforgebridge.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FmlHandshakeProtocolTest {
    @Test
    void completesExpectedClientSequence() {
        FmlHandshakeProtocol.ClientSession session = new FmlHandshakeProtocol.ClientSession();

        session.receiveServerHello(2);
        assertEquals(FmlHandshakeProtocol.ClientState.SERVER_HELLO_RECEIVED, session.state());
        assertEquals(2, session.serverProtocolVersion());

        session.markClientHelloSent();
        session.markModListSent();
        session.markRegistryDataReceived();
        session.markWaitingForServerAck();
        session.markComplete();

        assertEquals(FmlHandshakeProtocol.ClientState.COMPLETE, session.state());
        assertNull(session.failureReason());
    }

    @Test
    void rejectsInvalidTransition() {
        FmlHandshakeProtocol.ClientSession session = new FmlHandshakeProtocol.ClientSession();

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                session::markModListSent
        );

        assertTrue(error.getMessage().contains("NEW"));
        assertEquals(FmlHandshakeProtocol.ClientState.NEW, session.state());
    }

    @Test
    void negativeProtocolVersionFailsSession() {
        FmlHandshakeProtocol.ClientSession session = new FmlHandshakeProtocol.ClientSession();
        session.receiveServerHello(-1);

        assertEquals(FmlHandshakeProtocol.ClientState.FAILED, session.state());
        assertNotNull(session.failureReason());
    }
}
