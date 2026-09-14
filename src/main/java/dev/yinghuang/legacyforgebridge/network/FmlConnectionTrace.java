package dev.yinghuang.legacyforgebridge.network;

import dev.yinghuang.legacyforgebridge.BuildInfo;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Writes one focused Forge/FML trace file per Minecraft launch.
 *
 * <p>The file is truncated when LegacyForgeBridge starts. Multiple legacy connection attempts in
 * the same Minecraft process are separated into numbered SESSION sections instead of creating
 * many timestamped files.</p>
 */
public final class FmlConnectionTrace {
    private static final DateTimeFormatter LINE_TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final int RAW_HEX_LIMIT = 512;

    public static final FmlConnectionTrace INSTANCE = new FmlConnectionTrace();

    private BufferedWriter writer;
    private Path logPath;
    private int sessionCounter;
    private boolean sessionActive;

    private FmlConnectionTrace() {
    }

    /**
     * Opens the single launch log and clears any log left by the previous Minecraft launch.
     */
    public synchronized Path initializeForLaunch() {
        if (writer != null) {
            return logPath;
        }

        try {
            Path logDirectory = FabricLoader.getInstance().getGameDir().resolve("logs");
            Files.createDirectories(logDirectory);

            logPath = logDirectory.resolve("legacyforgebridge.log");
            writer = Files.newBufferedWriter(
                    logPath,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );

            sessionCounter = 0;
            sessionActive = false;

            writeLine("=== LegacyForgeBridge Forge/FML trace ===");
            writeLine("version=" + BuildInfo.VERSION);
            writeLine("launchTime=" + LocalDateTime.now());
            writeLine("policy=one file per Minecraft launch; previous launch log was cleared");
            writeLine("scope=Forge/FML custom payloads only; chat/auth/chunk payloads are intentionally not logged");
            writeLine("");
            flushQuietly();

            LegacyForgeBridge.LOGGER.info("Forge/FML trace log: {}", logPath.toAbsolutePath());
            return logPath;
        } catch (IOException exception) {
            LegacyForgeBridge.LOGGER.error("Unable to open Forge/FML trace log", exception);
            writer = null;
            logPath = null;
            return null;
        }
    }

    /** Starts a new connection section only if one is not already active. */
    public synchronized Path startIfNeeded(String reason) {
        if (writer == null && initializeForLaunch() == null) {
            return null;
        }

        if (!sessionActive) {
            sessionCounter++;
            sessionActive = true;
            writeLine("================================================================================");
            writeLine("[" + LINE_TIME.format(LocalDateTime.now()) + "] [SESSION #" + sessionCounter + " START] " + reason);
            writeLine("================================================================================");
            flushQuietly();
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
        if (writer == null) {
            return;
        }
        writeLine("[" + LINE_TIME.format(LocalDateTime.now()) + "] [EVENT] " + message);
        flushQuietly();
    }

    public synchronized void state(String from, String to, String cause) {
        if (writer == null) {
            return;
        }
        writeLine("[" + LINE_TIME.format(LocalDateTime.now()) + "] [STATE] " + from + " -> " + to + " cause=" + cause);
        flushQuietly();
    }

    public synchronized void packet(String direction, String channel, byte[] payload, String parsed) {
        if (writer == null) {
            return;
        }
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
            for (String line : parsed.split("\\R")) {
                writeLine("    " + line);
            }
        }
        writeLine("    raw=" + FmlWireCodec.hex(payload, RAW_HEX_LIMIT));
        flushQuietly();
    }

    public synchronized void registry(FmlWireCodec.ModIdData data) {
        if (writer == null || data == null) {
            return;
        }
        writeLine("    registryEntries=" + data.ids().size());
        for (Map.Entry<String, Integer> entry : data.ids().entrySet()) {
            writeLine("      " + entry.getValue() + " -> " + printableRegistryName(entry.getKey()));
        }
        writeLine("    blockSubstitutions=" + data.blockSubstitutions().size());
        for (String value : data.blockSubstitutions()) {
            writeLine("      blockSubstitution=" + printableRegistryName(value));
        }
        writeLine("    itemSubstitutions=" + data.itemSubstitutions().size());
        for (String value : data.itemSubstitutions()) {
            writeLine("      itemSubstitution=" + printableRegistryName(value));
        }
        if (data.trailingBytes() != 0) {
            writeLine("    trailingBytes=" + data.trailingBytes());
        }
        flushQuietly();
    }

    /** Ends the current connection section but deliberately keeps the launch log open. */
    public synchronized void endSession(String reason) {
        if (writer == null || !sessionActive) {
            return;
        }
        writeLine("[" + LINE_TIME.format(LocalDateTime.now()) + "] [SESSION #" + sessionCounter + " END] " + reason);
        writeLine("");
        sessionActive = false;
        flushQuietly();
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
        if (raw == null || raw.isEmpty()) {
            return String.valueOf(raw);
        }
        char first = raw.charAt(0);
        if (first == '\u0001') {
            return "[BLOCK] " + raw.substring(1);
        }
        if (first == '\u0002') {
            return "[ITEM] " + raw.substring(1);
        }
        return raw;
    }

    private void writeLine(String line) {
        try {
            writer.write(line);
            writer.newLine();
        } catch (IOException exception) {
            LegacyForgeBridge.LOGGER.error("Failed writing Forge/FML trace log", exception);
            try {
                writer.close();
            } catch (IOException ignored) {
            }
            writer = null;
        }
    }

    private void flushQuietly() {
        if (writer == null) {
            return;
        }
        try {
            writer.flush();
        } catch (IOException exception) {
            LegacyForgeBridge.LOGGER.warn("Failed flushing Forge/FML trace log", exception);
        }
    }
}
