package dev.longyu.legacyforgebridge.session;

import dev.longyu.legacyforgebridge.LegacyForgeBridge;
import dev.longyu.legacyforgebridge.protocol.LegacyProtocolVersions;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Owns the session-scoped legacy/modern state.
 *
 * <p>This class deliberately stores only translator-neutral protocol information so Forge/FML,
 * item mapping, diagnostics and future packet guards can depend on it without importing
 * ViaFabricPlus classes.</p>
 */
public final class LegacySessionController {
    public enum State {
        MODERN,
        LEGACY_1_7_10
    }

    private static final AtomicReference<State> STATE = new AtomicReference<>(State.MODERN);
    private static volatile int targetProtocolId = -1;
    private static volatile String targetProtocolName = "unknown";

    private LegacySessionController() {
    }

    public static State state() {
        return STATE.get();
    }

    public static boolean isLegacy1710() {
        return state() == State.LEGACY_1_7_10;
    }

    public static int targetProtocolId() {
        return targetProtocolId;
    }

    public static String targetProtocolName() {
        return targetProtocolName;
    }

    public static void onTargetProtocolChanged(int protocolId, String protocolName) {
        targetProtocolId = protocolId;
        targetProtocolName = Objects.requireNonNullElse(protocolName, "unknown");

        State next = LegacyProtocolVersions.isMinecraft1710(protocolId)
                ? State.LEGACY_1_7_10
                : State.MODERN;
        State previous = STATE.getAndSet(next);

        if (previous != next) {
            LegacyForgeBridge.LOGGER.info(
                    "Legacy session state changed: {} -> {} (target={} / protocol={})",
                    previous,
                    next,
                    targetProtocolName,
                    protocolId
            );
        }
    }

    /** Test/support hook. Runtime disconnect restoration normally comes from ViaFabricPlus. */
    public static void reset() {
        targetProtocolId = -1;
        targetProtocolName = "unknown";
        STATE.set(State.MODERN);
    }
}
