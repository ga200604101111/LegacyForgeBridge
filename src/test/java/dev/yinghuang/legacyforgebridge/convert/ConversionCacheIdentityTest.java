package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversionCacheIdentityTest {
    private static final String SOURCE_A = "a".repeat(64);
    private static final String SOURCE_B = "b".repeat(64);

    @TempDir
    Path tempDir;

    @Test
    void converterSchemaRevisionAndSourceAllParticipateInFingerprint() {
        String baseline = ConversionCacheIdentity.fingerprint("0.2.0-alpha.27", 2, "r1", SOURCE_A);
        assertNotEquals(baseline, ConversionCacheIdentity.fingerprint("0.2.0-alpha.28", 2, "r1", SOURCE_A));
        assertNotEquals(baseline, ConversionCacheIdentity.fingerprint("0.2.0-alpha.27", 3, "r1", SOURCE_A));
        assertNotEquals(baseline, ConversionCacheIdentity.fingerprint("0.2.0-alpha.27", 2, "r2", SOURCE_A));
        assertNotEquals(baseline, ConversionCacheIdentity.fingerprint("0.2.0-alpha.27", 2, "r1", SOURCE_B));
    }

    @Test
    void currentRevisionInvalidatesPreHeldVisibilityCandidateCache() {
        String stale = ConversionCacheIdentity.fingerprint(
                "0.2.0-alpha.27",
                2,
                "2026-09-20.146-third-person-local-translation",
                SOURCE_A
        );
        assertNotEquals(stale, ConversionCacheIdentity.current(SOURCE_A));
    }

    @Test
    void malformedIdentityInputsFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> ConversionCacheIdentity.fingerprint("", 2, "r1", SOURCE_A));
        assertThrows(IllegalArgumentException.class,
                () -> ConversionCacheIdentity.fingerprint("v", 0, "r1", SOURCE_A));
        assertThrows(IllegalArgumentException.class,
                () -> ConversionCacheIdentity.fingerprint("v", 2, "", SOURCE_A));
        assertThrows(IllegalArgumentException.class,
                () -> ConversionCacheIdentity.fingerprint("v", 2, "r1", "not-a-sha"));
    }

    @Test
    void partialAndConvertedReuseRequireExactCandidateBytes() throws Exception {
        Path candidate = tempDir.resolve("candidate.jar");
        Files.writeString(candidate, "first");
        String sha = Hashing.sha256(candidate);

        assertTrue(ConversionCacheIdentity.reusableResult("PARTIAL", candidate, sha));
        assertTrue(ConversionCacheIdentity.reusableResult("CONVERTED", candidate, sha));

        Files.writeString(candidate, "changed");
        assertFalse(ConversionCacheIdentity.reusableResult("PARTIAL", candidate, sha));
        assertFalse(ConversionCacheIdentity.reusableResult("CONVERTED", candidate, sha));
        assertFalse(ConversionCacheIdentity.reusableResult("FAILED", candidate, Hashing.sha256(candidate)));
    }

    @Test
    void missingOrUnverifiableCandidateForcesRebuild() throws Exception {
        Path missing = tempDir.resolve("missing.jar");
        assertFalse(ConversionCacheIdentity.reusableResult("PARTIAL", missing, SOURCE_A));
        assertFalse(ConversionCacheIdentity.reusableResult("PARTIAL", null, ""));
        assertFalse(ConversionCacheIdentity.reusableResult("UNKNOWN", null, ""));
        assertTrue(ConversionCacheIdentity.reusableResult("BLOCKED", null, ""));
        assertFalse(ConversionCacheIdentity.reusableResult("BLOCKED", missing, SOURCE_A));
    }
}
