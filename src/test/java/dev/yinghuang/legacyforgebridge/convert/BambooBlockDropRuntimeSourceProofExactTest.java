package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exact-corpus guard for the first Bamboo block whose complete static drop source proof closes. */
@Tag("exact-corpus")
class BambooBlockDropRuntimeSourceProofExactTest {
    private static final String BAMBOO_SHA256 =
            "bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";
    private static final String TARGET = "bambooMoss";

    @Test
    void exactBambooMossClosesNormalSilkHarvestExplosionAndEventProofs() throws Exception {
        String input = System.getProperty("lfb.exactCorpus.jar");
        assertNotNull(input, "exact Bamboo corpus is required");
        Path source = Path.of(input);
        assertTrue(Files.isRegularFile(source));
        assertEquals(BAMBOO_SHA256, Hashing.sha256(source));

        var dropAnalysis = new LegacyBlockDropPlanCompiler().compile(source);
        var plan = dropAnalysis.plans().stream()
                .filter(value -> TARGET.equals(value.registryName()))
                .findFirst().orElseThrow();
        assertEquals(LegacyBlockDropPlanCompiler.ItemKind.SELF_BLOCK_ITEM, plan.item().kind());
        assertEquals(TARGET, plan.item().registryName());
        assertEquals(1, plan.quantity());
        assertTrue(plan.itemDamageByBlockMeta().stream().allMatch(value -> value == 0));

        var silkAnalysis = new LegacyBlockSilkTouchAnalyzer().analyze(source);
        var silk = silkAnalysis.proofs().stream()
                .filter(value -> TARGET.equals(value.registryName()))
                .findFirst().orElseThrow();
        assertTrue(silk.eligibilityProofComplete(), silk.eligibilityReasons().toString());
        assertEquals(Boolean.TRUE, silk.silkEligible());
        assertTrue(silk.stackedItemProofComplete(), silk.stackedItemReasons().toString());
        assertEquals(0, silk.stackedLegacyDamage());

        var materialAnalysis = new LegacyBlockMaterialProvenanceAnalyzer().analyze(source);
        var materialProof = materialAnalysis.proofs().stream()
                .filter(value -> TARGET.equals(value.registryName()))
                .findFirst().orElseThrow();
        assertTrue(materialProof.complete(), materialProof.reasons().toString());
        var material = LegacyMaterialHarvestRules1710.lookup(
                materialProof.material().owner(),
                materialProof.material().fieldName(),
                materialProof.material().descriptor()
        ).orElseThrow();
        assertEquals("ground", material.namedMaterial());
        assertTrue(material.toolNotRequired());

        var harvestAnalysis = new LegacyBlockHarvestEligibilityAnalyzer().analyze(source);
        var harvest = harvestAnalysis.proofs().stream()
                .filter(value -> TARGET.equals(value.registryName()))
                .findFirst().orElseThrow();
        assertTrue(harvest.materialFastPathSourceSafe(), harvest.materialFastPathReasons().toString());

        var explosionAnalysis = new LegacyBlockExplosionAnalyzer().analyze(source);
        var explosion = explosionAnalysis.proofs().stream()
                .filter(value -> TARGET.equals(value.registryName()))
                .findFirst().orElseThrow();
        assertTrue(explosion.dropEligibilityProofComplete(), explosion.dropEligibilityReasons().toString());
        assertTrue(explosion.sourceDestructionOverrideFree(), explosion.destructionReasons().toString());

        var events = new LegacyEventAnalyzer().analyze(source);
        assertTrue(events.diagnostics().isEmpty(), events.diagnostics().toString());
        assertFalse(events.bindings().stream().anyMatch(binding -> {
            String event = binding.eventType();
            return event.contains("HarvestDropsEvent")
                    || event.contains("HarvestCheck")
                    || event.contains("ExplosionEvent");
        }), "Bamboo source event handlers must not mutate the admitted harvest/explosion drop path: "
                + events.bindings());
    }
}
