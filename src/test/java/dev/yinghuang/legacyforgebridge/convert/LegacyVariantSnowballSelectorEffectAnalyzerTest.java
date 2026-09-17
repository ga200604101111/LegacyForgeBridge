package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LegacyVariantSnowballSelectorEffectAnalyzerTest {
    @TempDir Path tempDir;

    @Test void provesJavacStyleSelectorDispatchPotionBranchAndDefaultNoop()throws Exception{
        Path jar=VariantSnowballSelectorEffectFixture.write(tempDir.resolve("effects.jar"));
        var analysis=new LegacyVariantSnowballSelectorEffectAnalyzer().analyze(jar);assertEquals(1,analysis.proofs().size());var proof=analysis.proofs().getFirst();
        assertTrue(proof.selectorDispatchProven(),proof.blockers().toString());assertTrue(proof.selectorEffectEdgesProven(),proof.blockers().toString());assertTrue(proof.selectorSpecificImpactSemanticsComplete(),proof.blockers().toString());assertEquals(1,proof.potionBranchesProven());
        var stone=proof.effects().stream().filter(e->"stone".equals(e.enumField())).findFirst().orElseThrow();assertEquals(0,stone.selectorId());assertEquals("NONE",stone.impactEffect());
        var poison=proof.effects().stream().filter(e->"poison".equals(e.enumField())).findFirst().orElseThrow();assertEquals(7,poison.selectorId());assertEquals("POTION",poison.impactEffect());assertEquals("poison",poison.potion());assertEquals(30,poison.duration());assertEquals(3,poison.amplifier());
    }

    @Test void missingSyntheticCaseBindingFailsClosedInsteadOfGuessingEnumEdge()throws Exception{
        Path jar=VariantSnowballSelectorEffectFixture.writeMissingCaseBinding(tempDir.resolve("missing-map.jar"));
        var proof=new LegacyVariantSnowballSelectorEffectAnalyzer().analyze(jar).proofs().getFirst();
        assertFalse(proof.selectorDispatchProven());assertFalse(proof.selectorEffectEdgesProven());assertFalse(proof.selectorSpecificImpactSemanticsComplete());assertTrue(proof.effects().isEmpty());assertTrue(proof.blockers().contains("selector-switch-case-table-not-source-bound"),proof.blockers().toString());
    }
}
