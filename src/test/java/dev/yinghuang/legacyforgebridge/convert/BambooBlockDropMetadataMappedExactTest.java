package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("exact-corpus")
class BambooBlockDropMetadataMappedExactTest {
    private static final String BAMBOO_SHA256 =
            "bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";
    private static final Set<String> TARGETS = Set.of(
            "crossLamp",
            "bambooLiangThick", "bambooLiangVLogThick", "bambooLiangVLog2Thick", "bambooLiangVWoodThick",
            "bambooLiangThin", "bambooLiangVLogThin", "bambooLiangVLog2Thin", "bambooLiangVWoodThin"
    );
    private static final List<Integer> IDENTITY = IntStream.range(0, 16).boxed().toList();

    @Test
    void exactBambooMetadataSelfDropsHaveIdentityDamageAndSilkIsProvenDisabled() throws Exception {
        String input = System.getProperty("lfb.exactCorpus.jar");
        assertNotNull(input, "exact Bamboo corpus is required");
        Path source = Path.of(input);
        assertTrue(Files.isRegularFile(source));
        assertEquals(BAMBOO_SHA256, Hashing.sha256(source));

        var plans = new LegacyBlockDropPlanCompiler().compile(source).plans().stream()
                .collect(Collectors.toMap(LegacyBlockDropPlanCompiler.Plan::registryName, value -> value));
        var silk = new LegacyBlockSilkTouchAnalyzer().analyze(source).proofs().stream()
                .collect(Collectors.toMap(LegacyBlockSilkTouchAnalyzer.Proof::registryName, value -> value));
        var material = new LegacyBlockMaterialProvenanceAnalyzer().analyze(source).proofs().stream()
                .collect(Collectors.toMap(LegacyBlockMaterialProvenanceAnalyzer.Proof::registryName, value -> value));
        var harvest = new LegacyBlockHarvestEligibilityAnalyzer().analyze(source).proofs().stream()
                .collect(Collectors.toMap(LegacyBlockHarvestEligibilityAnalyzer.Proof::registryName, value -> value));
        var explosion = new LegacyBlockExplosionAnalyzer().analyze(source).proofs().stream()
                .collect(Collectors.toMap(LegacyBlockExplosionAnalyzer.Proof::registryName, value -> value));

        for (String name : TARGETS) {
            var plan = plans.get(name);
            assertNotNull(plan, name);
            assertEquals(LegacyBlockDropPlanCompiler.ItemKind.SELF_BLOCK_ITEM, plan.item().kind(), name);
            assertEquals(1, plan.quantity(), name);
            assertEquals(IDENTITY, plan.itemDamageByBlockMeta(), name);

            var silkProof = silk.get(name);
            assertNotNull(silkProof, name);
            assertTrue(silkProof.eligibilityProofComplete(), () -> name + ": " + silkProof.eligibilityReasons());
            assertEquals(Boolean.FALSE, silkProof.silkEligible(), name);

            var materialProof = material.get(name);
            assertNotNull(materialProof, name);
            assertTrue(materialProof.complete(), () -> name + ": " + materialProof.reasons());
            var materialRule = LegacyMaterialHarvestRules1710.lookup(
                    materialProof.material().owner(), materialProof.material().fieldName(), materialProof.material().descriptor())
                    .orElseThrow();
            assertTrue(materialRule.toolNotRequired(), name + " material=" + materialRule.namedMaterial());

            var harvestProof = harvest.get(name);
            assertNotNull(harvestProof, name);
            assertTrue(harvestProof.materialFastPathSourceSafe(), () -> name + ": " + harvestProof.materialFastPathReasons());

            var explosionProof = explosion.get(name);
            assertNotNull(explosionProof, name);
            assertTrue(explosionProof.dropEligibilityProofComplete(), () -> name + ": " + explosionProof.dropEligibilityReasons());
            assertTrue(explosionProof.sourceDestructionOverrideFree(), () -> name + ": " + explosionProof.destructionReasons());
        }
    }
}
