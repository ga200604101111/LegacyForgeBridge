package dev.longyu.legacyforgebridge.convert;

import dev.longyu.legacyforgebridge.BuildInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/** Stable cache contract for deterministic legacy conversion results. */
public final class ConversionCacheIdentity {
    private static final Set<String> CANDIDATE_STATUSES = Set.of("PARTIAL", "CONVERTED");

    private ConversionCacheIdentity() { }

    public static String current(String sourceSha256) {
        return fingerprint(
                BuildInfo.VERSION,
                BuildInfo.CONVERSION_SCHEMA,
                BuildInfo.CONVERTER_REVISION,
                sourceSha256
        );
    }

    static String fingerprint(String version, int schema, String revision, String sourceSha256) {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("converter version is blank");
        }
        if (schema <= 0) {
            throw new IllegalArgumentException("conversion schema must be positive");
        }
        if (revision == null || revision.isBlank()) {
            throw new IllegalArgumentException("converter revision is blank");
        }
        String source = normalizedSha256(sourceSha256);
        return version + ":schema=" + schema + ":revision=" + revision + ":source=" + source;
    }

    /**
     * Reuse is permitted only when the previous deterministic result still has the artifact shape
     * that status promises. PARTIAL/CONVERTED candidates are re-hashed before reuse; FAILED and
     * unknown states always rebuild. BLOCKED is reusable only when it never claimed a candidate.
     */
    public static boolean reusableResult(String status, Path candidate, String expectedCandidateSha256)
            throws IOException {
        String normalizedStatus = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (normalizedStatus.equals("FAILED") || normalizedStatus.isBlank()) {
            return false;
        }
        if (normalizedStatus.equals("BLOCKED")) {
            return candidate == null
                    && (expectedCandidateSha256 == null || expectedCandidateSha256.isBlank());
        }
        if (!CANDIDATE_STATUSES.contains(normalizedStatus)) {
            return false;
        }
        return matches(candidate, expectedCandidateSha256);
    }

    static boolean matches(Path candidate, String expectedCandidateSha256) throws IOException {
        if (candidate == null || !Files.isRegularFile(candidate)) {
            return false;
        }
        if (expectedCandidateSha256 == null || expectedCandidateSha256.isBlank()) {
            return false;
        }
        String expected;
        try {
            expected = normalizedSha256(expectedCandidateSha256);
        } catch (IllegalArgumentException invalidStoredHash) {
            return false;
        }
        return expected.equals(Hashing.sha256(candidate));
    }

    private static String normalizedSha256(String value) {
        if (value == null) {
            throw new IllegalArgumentException("SHA-256 is null");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid SHA-256: " + value);
        }
        return normalized;
    }
}
