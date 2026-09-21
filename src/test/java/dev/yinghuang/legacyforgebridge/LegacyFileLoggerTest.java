package dev.yinghuang.legacyforgebridge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyFileLoggerTest {
    @TempDir Path tempDir;

    @Test void dedicatedLaunchLogTruncatesOnceFormatsSlf4jPlaceholdersAndKeepsRawTrace() throws Exception {
        Path log = tempDir.resolve("logs").resolve("legacyforgebridge.log");
        Files.createDirectories(log.getParent());
        Files.writeString(log, "old-launch\n", StandardCharsets.UTF_8);

        LegacyFileLogger logger = new LegacyFileLogger(log);
        assertEquals(log, logger.initializeForLaunch());
        logger.info("Legacy conversion [{}/{}] {}% {}", 1, 2, 60, "CONVERTING");
        logger.warn("Rule {} stayed fail-closed", "demo:crop");
        logger.error("Conversion failed for {}", "demo.jar", new IllegalStateException("boom"));
        logger.raw("[12:34:56.789] [EVENT] raw-fml-trace");

        String beforeSecondInit = Files.readString(log, StandardCharsets.UTF_8);
        assertEquals(log, logger.initializeForLaunch());
        logger.info("second initialize did not truncate");
        logger.closeForTests();

        String text = Files.readString(log, StandardCharsets.UTF_8);
        assertFalse(text.contains("old-launch"));
        assertTrue(text.contains("=== LegacyForgeBridge launch log ==="));
        assertTrue(text.contains("version=" + BuildInfo.VERSION));
        assertTrue(text.contains("conversionSchema=" + BuildInfo.CONVERSION_SCHEMA));
        assertTrue(text.contains("converterRevision=" + BuildInfo.CONVERTER_REVISION));
        assertTrue(text.contains("[INFO] Legacy conversion [1/2] 60% CONVERTING"));
        assertTrue(text.contains("[WARN] Rule demo:crop stayed fail-closed"));
        assertTrue(text.contains("[ERROR] Conversion failed for demo.jar"));
        assertTrue(text.contains("IllegalStateException: boom"));
        assertTrue(text.contains("[12:34:56.789] [EVENT] raw-fml-trace"));
        assertTrue(text.startsWith(beforeSecondInit));
        assertTrue(text.contains("second initialize did not truncate"));
        assertNotNull(logger.initializationFailure() == null ? log : null);
    }

    @Test void extraArgumentsRemainVisibleInsteadOfBeingSilentlyDropped() throws Exception {
        Path log = tempDir.resolve("extra.log");
        LegacyFileLogger logger = new LegacyFileLogger(log);
        assertNotNull(logger.initializeForLaunch());
        logger.info("message {}", "one", "two", 3);
        logger.closeForTests();
        String text = Files.readString(log, StandardCharsets.UTF_8);
        assertTrue(text.contains("message one [extraArgs=two, 3]"));
    }
}
