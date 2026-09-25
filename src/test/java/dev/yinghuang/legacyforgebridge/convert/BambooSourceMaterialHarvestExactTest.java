package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("exact-corpus")
class BambooSourceMaterialHarvestExactTest {
    private static final String BAMBOO_SHA256 =
            "bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";
    private static final String TARGET = "kitunebi";
    private static final String MATERIAL = "ruby/bamboo/block/MaterialBamboo";

    @Test
    void exactKitunebiUsesStableInheritedNoToolSourceMaterial() throws Exception {
        String input = System.getProperty("lfb.exactCorpus.jar");
        assertNotNull(input, "exact Bamboo corpus is required");
        Path source = Path.of(input);
        assertTrue(Files.isRegularFile(source));
        assertEquals(BAMBOO_SHA256, Hashing.sha256(source));

        var material = new LegacyBlockMaterialProvenanceAnalyzer().analyze(source).proofs().stream()
                .filter(value -> TARGET.equals(value.registryName()))
                .findFirst().orElseThrow();
        assertTrue(material.complete(), material.reasons().toString());
        assertEquals(MATERIAL, material.material().owner());
        assertEquals("instance", material.material().fieldName());
        assertEquals("L" + MATERIAL + ";", material.material().descriptor());

        var sourceMaterial = new LegacySourceMaterialHarvestAnalyzer().analyze(source).proofs().stream()
                .filter(value -> material.material().equals(value.material()))
                .findFirst().orElseThrow();
        assertTrue(sourceMaterial.complete(), sourceMaterial.reasons().toString());
        assertEquals(Boolean.TRUE, sourceMaterial.toolNotRequired());

        var harvest = new LegacyBlockHarvestEligibilityAnalyzer().analyze(source).proofs().stream()
                .filter(value -> TARGET.equals(value.registryName()))
                .findFirst().orElseThrow();
        assertTrue(harvest.materialFastPathSourceSafe(), harvest.materialFastPathReasons().toString());

        var plan = new LegacyBlockDropPlanCompiler().compile(source).plans().stream()
                .filter(value -> TARGET.equals(value.registryName()))
                .findFirst().orElseThrow();
        assertEquals(LegacyBlockDropPlanCompiler.ItemKind.SELF_BLOCK_ITEM, plan.item().kind());
        assertEquals(1, plan.quantity());
        assertTrue(plan.itemDamageByBlockMeta().stream().allMatch(value -> value == 0));

        var silk = new LegacyBlockSilkTouchAnalyzer().analyze(source).proofs().stream()
                .filter(value -> TARGET.equals(value.registryName()))
                .findFirst().orElseThrow();
        assertTrue(silk.eligibilityProofComplete(), silk.eligibilityReasons().toString());
        assertEquals(Boolean.FALSE, silk.silkEligible());

        var explosion = new LegacyBlockExplosionAnalyzer().analyze(source).proofs().stream()
                .filter(value -> TARGET.equals(value.registryName()))
                .findFirst().orElseThrow();
        assertTrue(explosion.dropEligibilityProofComplete(), explosion.dropEligibilityReasons().toString());
        assertTrue(explosion.sourceDestructionOverrideFree(), explosion.destructionReasons().toString());
    }
}
