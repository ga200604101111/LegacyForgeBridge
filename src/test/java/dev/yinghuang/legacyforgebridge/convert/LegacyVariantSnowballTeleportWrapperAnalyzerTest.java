package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LegacyVariantSnowballTeleportWrapperAnalyzerTest {
    @TempDir Path tempDir;

    @Test void provesRandomDestinationWrapperAndExactTeleportDelegate()throws Exception{
        Path jar=VariantSnowballTeleportWrapperFixture.write(tempDir.resolve("teleport-wrapper.jar"));
        var analysis=new LegacyVariantSnowballTeleportWrapperAnalyzer().analyze(jar);assertEquals(1,analysis.proofs().size());var proof=analysis.proofs().getFirst();
        assertEquals("poison",proof.enumField());assertEquals(7,proof.selectorId());assertEquals("teleportRandomly",proof.wrapperMethod());assertEquals("teleportTo",proof.teleportMethod());
        assertTrue(proof.randomXProven(),proof.blockers().toString());assertTrue(proof.randomYProven(),proof.blockers().toString());assertTrue(proof.randomZProven(),proof.blockers().toString());assertTrue(proof.delegateProven(),proof.blockers().toString());assertTrue(proof.wrapperProven(),proof.blockers().toString());
    }

    @Test void changedHorizontalSpanFailsClosedWithoutErasingIndependentYAndDelegateProof()throws Exception{
        Path jar=VariantSnowballTeleportWrapperFixture.writeWrongHorizontalSpan(tempDir.resolve("wrong-span.jar"));var proof=new LegacyVariantSnowballTeleportWrapperAnalyzer().analyze(jar).proofs().getFirst();
        assertFalse(proof.randomXProven());assertTrue(proof.randomYProven());assertFalse(proof.randomZProven());assertFalse(proof.delegateProven(),"delegate is not admitted when proven coordinate locals are incomplete");assertFalse(proof.wrapperProven());assertTrue(proof.blockers().contains("random-x-plus-minus-16-wrapper-not-proven"));assertTrue(proof.blockers().contains("random-z-plus-minus-16-wrapper-not-proven"));
    }
}
