package dev.yinghuang.legacyforgebridge;

import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

/**
 * Dedicated LegacyForgeBridge launch logger.
 *
 * <p>Normal LFB diagnostics intentionally bypass the Minecraft/SLF4J log so conversion progress,
 * proof/runtime loading and Forge/FML tracing stay in {@code logs/legacyforgebridge.log}. The file
 * is truncated exactly once per process launch and all later users share the same writer.</p>
 */
public final class LegacyFileLogger {
    private static final DateTimeFormatter LINE_TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final Path fixedPath;
    private BufferedWriter writer;
    private Path logPath;
    private Throwable initializationFailure;
    private boolean initializationAttempted;

    public LegacyFileLogger() {
        this(null);
    }

    LegacyFileLogger(Path fixedPath) {
        this.fixedPath = fixedPath;
    }

    public synchronized Path initializeForLaunch() {
        if (writer != null) return logPath;
        if (initializationAttempted) return null;
        initializationAttempted = true;
        try {
            logPath = fixedPath != null
                    ? fixedPath
                    : FabricLoader.getInstance().getGameDir().resolve("logs").resolve("legacyforgebridge.log");
            Path parent = logPath.getParent();
            if (parent != null) Files.createDirectories(parent);
            writer = Files.newBufferedWriter(
                    logPath,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
            rawInternal("=== LegacyForgeBridge launch log ===");
            rawInternal("version=" + BuildInfo.VERSION);
            rawInternal("conversionSchema=" + BuildInfo.CONVERSION_SCHEMA);
            rawInternal("converterRevision=" + BuildInfo.CONVERTER_REVISION);
            rawInternal("launchTime=" + LocalDateTime.now());
            rawInternal("policy=LFB internal diagnostics and Forge/FML trace are kept here and are not mirrored to the Minecraft game log");
            rawInternal("");
            flushInternal();
            return logPath;
        } catch (IOException | RuntimeException exception) {
            initializationFailure = exception;
            closeWriterQuietly();
            logPath = null;
            return null;
        }
    }

    public synchronized boolean isAvailable() {
        return writer != null || initializeForLaunch() != null;
    }

    public synchronized Path path() {
        return writer != null ? logPath : initializeForLaunch();
    }

    public synchronized Throwable initializationFailure() {
        return initializationFailure;
    }

    public void trace(String pattern, Object... arguments) { log("TRACE", pattern, arguments); }
    public void debug(String pattern, Object... arguments) { log("DEBUG", pattern, arguments); }
    public void info(String pattern, Object... arguments) { log("INFO", pattern, arguments); }
    public void warn(String pattern, Object... arguments) { log("WARN", pattern, arguments); }
    public void error(String pattern, Object... arguments) { log("ERROR", pattern, arguments); }

    public boolean isTraceEnabled() { return true; }
    public boolean isDebugEnabled() { return true; }
    public boolean isInfoEnabled() { return true; }
    public boolean isWarnEnabled() { return true; }
    public boolean isErrorEnabled() { return true; }

    /** Appends an already formatted trace line without adding a second LFB level prefix. */
    public synchronized void raw(String line) {
        if (writer == null && initializeForLaunch() == null) return;
        try {
            rawInternal(line == null ? "null" : line);
            flushInternal();
        } catch (IOException exception) {
            failWriter(exception);
        }
    }

    private synchronized void log(String level, String pattern, Object... arguments) {
        if (writer == null && initializeForLaunch() == null) return;
        Object[] args = arguments == null ? new Object[0] : arguments;
        int placeholders = placeholderCount(pattern);
        Throwable throwable = args.length > 0 && args[args.length - 1] instanceof Throwable candidate
                && placeholders < args.length ? candidate : null;
        int valueCount = throwable == null ? args.length : args.length - 1;
        String message = format(pattern, args, valueCount);
        try {
            rawInternal("[" + LINE_TIME.format(LocalDateTime.now()) + "] [" + level + "] " + message);
            if (throwable != null) {
                StringWriter stack = new StringWriter();
                throwable.printStackTrace(new PrintWriter(stack));
                for (String line : stack.toString().split("\\R")) rawInternal("    " + line);
            }
            flushInternal();
        } catch (IOException exception) {
            failWriter(exception);
        }
    }

    private static String format(String pattern, Object[] arguments, int valueCount) {
        String text = String.valueOf(pattern);
        if (valueCount == 0) return text;
        StringBuilder result = new StringBuilder(text.length() + valueCount * 12);
        int cursor = 0;
        int argument = 0;
        while (argument < valueCount) {
            int marker = text.indexOf("{}", cursor);
            if (marker < 0) break;
            result.append(text, cursor, marker).append(render(arguments[argument++]));
            cursor = marker + 2;
        }
        result.append(text, cursor, text.length());
        if (argument < valueCount) {
            result.append(" [extraArgs=");
            for (int index = argument; index < valueCount; index++) {
                if (index > argument) result.append(", ");
                result.append(render(arguments[index]));
            }
            result.append(']');
        }
        return result.toString();
    }

    private static int placeholderCount(String pattern) {
        if (pattern == null || pattern.isEmpty()) return 0;
        int count = 0;
        int cursor = 0;
        while ((cursor = pattern.indexOf("{}", cursor)) >= 0) {
            count++;
            cursor += 2;
        }
        return count;
    }

    private static String render(Object value) {
        if (value == null) return "null";
        Class<?> type = value.getClass();
        if (!type.isArray()) return String.valueOf(value);
        if (value instanceof Object[] objects) return Arrays.deepToString(objects);
        int length = Array.getLength(value);
        StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < length; index++) {
            if (index > 0) result.append(", ");
            result.append(Array.get(value, index));
        }
        return result.append(']').toString();
    }

    private void rawInternal(String line) throws IOException {
        writer.write(line);
        writer.newLine();
    }

    private void flushInternal() throws IOException {
        writer.flush();
    }

    private void failWriter(Throwable failure) {
        if (initializationFailure == null) initializationFailure = failure;
        closeWriterQuietly();
        logPath = null;
    }

    private void closeWriterQuietly() {
        BufferedWriter current = writer;
        writer = null;
        if (current == null) return;
        try {
            current.close();
        } catch (IOException ignored) {
        }
    }

    synchronized void closeForTests() {
        closeWriterQuietly();
    }
}
