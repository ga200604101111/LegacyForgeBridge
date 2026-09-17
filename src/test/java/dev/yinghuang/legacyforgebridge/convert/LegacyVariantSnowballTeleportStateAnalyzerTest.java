package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LegacyVariantSnowballTeleportStateAnalyzerTest {
    @TempDir Path tempDir;

    @Test void provesSavedCandidateRollbackAndGuardedBooleanSkeleton()throws Exception{
        Path jar=VariantSnowballTeleportStateFixture.write(tempDir.resolve("state.jar"));var analysis=new LegacyVariantSnowballTeleportStateAnalyzer().analyze(jar);assertEquals(1,analysis.proofs().size());var proof=analysis.proofs().getFirst();
        assertTrue(proof.savedPositionProven(),proof.blockers().toString());assertTrue(proof.candidateAssignmentProven(),proof.blockers().toString());assertTrue(proof.guardedRollbackFalseProven(),proof.blockers().toString());assertTrue(proof.successTrueReturnProven(),proof.blockers().toString());assertTrue(proof.stateSkeletonProven(),proof.blockers().toString());
    }

    @Test void rollbackMustRestoreEverySavedAxisExactly()throws Exception{
        Path jar=VariantSnowballTeleportStateFixture.writeBrokenRollback(tempDir.resolve("broken-rollback.jar"));var proof=new LegacyVariantSnowballTeleportStateAnalyzer().analyze(jar).proofs().getFirst();
        assertTrue(proof.savedPositionProven());assertTrue(proof.candidateAssignmentProven());assertFalse(proof.guardedRollbackFalseProven());assertFalse(proof.successTrueReturnProven());assertFalse(proof.stateSkeletonProven());assertTrue(proof.blockers().contains("guarded-exact-position-rollback-false-path-not-proven"),proof.blockers().toString());
    }
}
