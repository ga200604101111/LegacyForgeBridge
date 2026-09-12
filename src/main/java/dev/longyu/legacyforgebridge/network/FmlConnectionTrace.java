package dev.longyu.legacyforgebridge.network;

import dev.longyu.legacyforgebridge.BuildInfo;
import dev.longyu.legacyforgebridge.LegacyForgeBridge;
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

/** Writes a focused Forge/FML trace for one legacy connection attempt. */
public final class FmlConnectionTrace {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss_SSS");
    private static final DateTimeFormatter LINE_TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final int RAW_HEX_LIMIT = 512;

    public static final FmlConnectionTrace INSTANCE = new FmlConnectionTrace();

    private BufferedWriter writer;
    private Path activePath;

    private FmlConnectionTrace() {
    }

    public synchronized Path startIfNeeded(String reason) {
        if (writer != null) {
            return activePath;
        }

        try {
            Path directory = FabricLoader.getInstance().getGameDir()
                    .resolve("logs")
                    .resolve("legacyforgebridge");
            Files.createDirectories(directory);

            activePath = directory.resolve("connection-" + FILE_TIME.format(LocalDateTime.now()) + ".log");
            writer = Files.newBufferedWriter(
                    activePath,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            );

            writeLine("=== LegacyForgeBridge Forge/FML connection trace ===");
            writeLine("version=" + BuildInfo.VERSION);
            writeLine("startedBecause=" + reason);
            writeLine("scope=Forge/FML custom payloads only; chat/auth/chunk payloads are intentionally not logged");
            writeLine("");
            flushQuietly();

            LegacyForgeBridge.LOGGER.info("Forge/FML connection trace: {}", activePath.toAbsolutePath());
            return activePath;
        } catch (IOException exception) {
            LegacyForgeBridge.LOGGER.error("Unable to open Forge/FML connection trace", exception);
            writer = null;
            activePath = null;
            return null;
        }
    }

    public synchronized Path activePath() {
        return activePath;
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
        String discriminatorText = discriminator < 0
                ? "<none>"
                : "0x%02X %s".formatted(discriminator, FmlWireCodec.discriminatorName(discriminator));

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

    public synchronized void close(String reason) {
        if (writer == null) {
            return;
        }
        try {
            writeLine("[" + LINE_TIME.format(LocalDateTime.now()) + "] [END] " + reason);
            writer.flush();
            writer.close();
        } catch (IOException exception) {
            LegacyForgeBridge.LOGGER.warn("Failed to close Forge/FML connection trace cleanly", exception);
        } finally {
            writer = null;
            activePath = null;
        }
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
            LegacyForgeBridge.LOGGER.error("Failed writing Forge/FML connection trace", exception);
            try {
                writer.close();
            } catch (IOException ignored) {
            }
            writer = null;
            activePath = null;
        }
    }

    private void flushQuietly() {
        if (writer == null) {
            return;
        }
        try {
            writer.flush();
        } catch (IOException exception) {
            LegacyForgeBridge.LOGGER.warn("Failed flushing Forge/FML connection trace", exception);
        }
    }
}
