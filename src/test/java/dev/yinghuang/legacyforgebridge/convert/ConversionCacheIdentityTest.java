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
    void currentRevisionInvalidatesPreMetaRenderBranchProofCandidateCache() {
        String stale = ConversionCacheIdentity.fingerprint(
                "0.2.0-alpha.27",
                2,
                "2026-09-21.154-held-item-visibility-runtime",
                SOURCE_A
        );
        assertNotEquals(stale, ConversionCacheIdentity.current(SOURCE_A));
    }

    @Test
    void currentRevisionInvalidatesPreResourceDependencyCandidateCache() {
        String stale = ConversionCacheIdentity.fingerprint(
                "0.2.0-alpha.27",
                2,
                "2026-09-21.155-meta-render-branch-proof",
                SOURCE_A
        );
        assertNotEquals(stale, ConversionCacheIdentity.current(SOURCE_A));
    }

    @Test
    void currentRevisionInvalidatesPreGenericSignatureCandidateCache() {
        String stale = ConversionCacheIdentity.fingerprint(
                "0.2.0-alpha.27",
                2,
                "2026-09-21.156-resource-class-dependency-proof",
                SOURCE_A
        );
        assertNotEquals(stale, ConversionCacheIdentity.current(SOURCE_A));
    }

    @Test
    void currentRevisionInvalidatesPreGeneratedLinkageAuditCandidateCache() {
        String stale = ConversionCacheIdentity.fingerprint(
                "0.2.0-alpha.27",
                2,
                "2026-09-21.157-generic-signature-reference-proof",
                SOURCE_A
        );
        assertNotEquals(stale, ConversionCacheIdentity.current(SOURCE_A));
    }

    @Test
    void currentRevisionInvalidatesPreSrgSimpleRendererCandidateCache() {
        String stale = ConversionCacheIdentity.fingerprint(
                "0.2.0-alpha.27",
                2,
                "2026-09-21.158-generated-linkage-audit",
                SOURCE_A
        );
        assertNotEquals(stale, ConversionCacheIdentity.current(SOURCE_A));
    }

    @Test
    void currentRevisionInvalidatesPreRegistryNumericDataflowCandidateCache() {
        String stale = ConversionCacheIdentity.fingerprint(
                "0.2.0-alpha.27",
                2,
                "2026-09-21.159-srg-simple-renderer-proof",
                SOURCE_A
        );
        assertNotEquals(stale, ConversionCacheIdentity.current(SOURCE_A));
    }

    @Test
    void currentRevisionInvalidatesPreConnectedCuboidCandidateCache() {
        String stale = ConversionCacheIdentity.fingerprint(
                "0.2.0-alpha.27",
                2,
                "2026-09-21.160-registry-numeric-dataflow",
                SOURCE_A
        );
        assertNotEquals(stale, ConversionCacheIdentity.current(SOURCE_A));
    }

    @Test
    void currentRevisionInvalidatesPreVisibleEntityCandidateCache() {
        String stale = ConversionCacheIdentity.fingerprint(
                "0.2.0-alpha.27", 2, "2026-09-21.161-connected-cuboid-renderer", SOURCE_A);
        assertNotEquals(stale, ConversionCacheIdentity.current(SOURCE_A));
    }

    @Test
    void currentRevisionInvalidatesPreVisibleEntityRuntimeCandidateCache() {
        String stale = ConversionCacheIdentity.fingerprint(
                "0.2.0-alpha.27", 2, "2026-09-21.162-visible-entity-proof", SOURCE_A);
        assertNotEquals(stale, ConversionCacheIdentity.current(SOURCE_A));
    }

    @Test
    void currentRevisionInvalidatesPreMicroBlockRuntimeCandidateCache() {
        String stale = ConversionCacheIdentity.fingerprint(
                "0.2.0-alpha.27", 2, "2026-09-21.163-visible-entity-runtime", SOURCE_A);
        assertNotEquals(stale, ConversionCacheIdentity.current(SOURCE_A));
    }

    @Test
    void currentRevisionInvalidatesPreProcessorWorldOwnershipCandidateCache() {
        String stale = ConversionCacheIdentity.fingerprint(
                "0.2.0-alpha.27", 2, "2026-09-21.164-micro-block-runtime", SOURCE_A);
        assertNotEquals(stale, ConversionCacheIdentity.current(SOURCE_A));
    }

    @Test
    void currentRevisionInvalidatesPreLiveRenderOwnershipCandidateCache() {
        String stale = ConversionCacheIdentity.fingerprint(
                "0.2.0-alpha.27", 2, "2026-09-21.165-processor-world-ownership", SOURCE_A);
        assertNotEquals(stale, ConversionCacheIdentity.current(SOURCE_A));
    }

    @Test
    void currentRevisionInvalidatesPreLivePresentationOutputProofCandidateCache() {
        String stale = ConversionCacheIdentity.fingerprint(
                "0.2.0-alpha.27", 2, "2026-09-21.166-live-render-ownership", SOURCE_A);
        assertNotEquals(stale, ConversionCacheIdentity.current(SOURCE_A));
    }

    @Test
    void currentRevisionInvalidatesPreBambooPresentationRuntimeCandidateCache() {
        String stale = ConversionCacheIdentity.fingerprint(
                "0.2.0-alpha.27", 2, "2026-09-21.167-live-presentation-output-proof", SOURCE_A);
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
