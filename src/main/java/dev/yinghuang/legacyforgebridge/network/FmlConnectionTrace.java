package dev.yinghuang.legacyforgebridge.network;

import dev.yinghuang.legacyforgebridge.BuildInfo;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Writes Forge/FML trace sections into the shared LegacyForgeBridge launch log.
 *
 * <p>The central LFB logger owns {@code logs/legacyforgebridge.log} and truncates it once per
 * Minecraft launch. Multiple legacy connection attempts are separated into numbered SESSION
 * sections without opening a competing file writer.</p>
 */
public final class FmlConnectionTrace {
    private static final DateTimeFormatter LINE_TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final int RAW_HEX_LIMIT = 512;

    public static final FmlConnectionTrace INSTANCE = new FmlConnectionTrace();

    private Path logPath;
    private int sessionCounter;
    private boolean sessionActive;

    private FmlConnectionTrace() {
    }

    /** Attaches the FML trace section to the shared launch log without truncating it again. */
    public synchronized Path initializeForLaunch() {
        if (logPath != null) return logPath;
        logPath = LegacyForgeBridge.LOGGER.initializeForLaunch();
        if (logPath == null) return null;

        sessionCounter = 0;
        sessionActive = false;
        writeLine("");
        writeLine("=== LegacyForgeBridge Forge/FML trace ===");
        writeLine("version=" + BuildInfo.VERSION);
        writeLine("traceInitialized=" + LocalDateTime.now());
        writeLine("scope=Forge/FML custom payloads only; chat/auth/chunk payloads are intentionally not logged");
        writeLine("");
        LegacyForgeBridge.LOGGER.info("Forge/FML trace attached to dedicated log: {}", logPath.toAbsolutePath());
        return logPath;
    }

    /** Starts a new connection section only if one is not already active. */
    public synchronized Path startIfNeeded(String reason) {
        if (logPath == null && initializeForLaunch() == null) return null;
        if (!sessionActive) {
            sessionCounter++;
            sessionActive = true;
            writeLine("================================================================================");
            writeLine("[" + LINE_TIME.format(LocalDateTime.now()) + "] [SESSION #" + sessionCounter + " START] " + reason);
            writeLine("================================================================================");
        }
        return logPath;
    }

    public synchronized Path activePath() {
        return logPath;
    }

    public synchronized boolean sessionActive() {
        return sessionActive;
    }

    public synchronized void event(String message) {
        if (logPath == null) return;
        writeLine("[" + LINE_TIME.format(LocalDateTime.now()) + "] [EVENT] " + message);
    }

    public synchronized void state(String from, String to, String cause) {
        if (logPath == null) return;
        writeLine("[" + LINE_TIME.format(LocalDateTime.now()) + "] [STATE] " + from + " -> " + to + " cause=" + cause);
    }

    public synchronized void packet(String direction, String channel, byte[] payload, String parsed) {
        if (logPath == null) return;
        int discriminator = payload != null && payload.length > 0 ? payload[0] & 0xFF : -1;
        String discriminatorText;
        if (discriminator < 0) {
            discriminatorText = "<none>";
        } else {
            String discriminatorName = discriminatorName(channel, discriminator);
            discriminatorText = "0x%02X%s".formatted(
                    discriminator,
                    discriminatorName == null ? "" : " " + discriminatorName
            );
        }

        writeLine("[" + LINE_TIME.format(LocalDateTime.now()) + "] [" + direction + "] channel=" + channel
                + " bytes=" + (payload == null ? 0 : payload.length)
                + " discriminator=" + discriminatorText);
        if (parsed != null && !parsed.isBlank()) {
            for (String line : parsed.split("\\R")) writeLine("    " + line);
        }
        writeLine("    raw=" + FmlWireCodec.hex(payload, RAW_HEX_LIMIT));
    }

    public synchronized void registry(FmlWireCodec.ModIdData data) {
        if (logPath == null || data == null) return;
        writeLine("    registryEntries=" + data.ids().size());
        for (Map.Entry<String, Integer> entry : data.ids().entrySet()) {
            writeLine("      " + entry.getValue() + " -> " + printableRegistryName(entry.getKey()));
        }
        writeLine("    blockSubstitutions=" + data.blockSubstitutions().size());
        for (String value : data.blockSubstitutions()) writeLine("      blockSubstitution=" + printableRegistryName(value));
        writeLine("    itemSubstitutions=" + data.itemSubstitutions().size());
        for (String value : data.itemSubstitutions()) writeLine("      itemSubstitution=" + printableRegistryName(value));
        if (data.trailingBytes() != 0) writeLine("    trailingBytes=" + data.trailingBytes());
    }

    /** Ends the current connection section but deliberately keeps the launch log open. */
    public synchronized void endSession(String reason) {
        if (logPath == null || !sessionActive) return;
        writeLine("[" + LINE_TIME.format(LocalDateTime.now()) + "] [SESSION #" + sessionCounter + " END] " + reason);
        writeLine("");
        sessionActive = false;
    }

    /** Kept for source compatibility with alpha.3 callers; this no longer closes the launch file. */
    @Deprecated
    public synchronized void close(String reason) {
        endSession(reason);
    }

    private String discriminatorName(String channel, int discriminator) {
        return switch (channel) {
            case "FML|HS" -> FmlWireCodec.discriminatorName(discriminator);
            case "FML" -> FmlRuntimeCodec.discriminatorName(discriminator);
            default -> null;
        };
    }

    private String printableRegistryName(String raw) {
        if (raw == null || raw.isEmpty()) return String.valueOf(raw);
        char first = raw.charAt(0);
        if (first == '\u0001') return "[BLOCK] " + raw.substring(1);
        if (first == '\u0002') return "[ITEM] " + raw.substring(1);
        return raw;
    }

    private void writeLine(String line) {
        LegacyForgeBridge.LOGGER.raw(line);
        if (!LegacyForgeBridge.LOGGER.isAvailable()) logPath = null;
    }
}
