package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyMaterialHarvestRules1710Test {
    @Test
    void containsTheCompleteMcp908VanillaMaterialTableAndOnlySixRequireTools() {
        assertEquals(34, LegacyMaterialHarvestRules1710.rules().size());

        Set<String> requiresTool = LegacyMaterialHarvestRules1710.rules().stream()
                .filter(rule -> !rule.toolNotRequired())
                .map(LegacyMaterialHarvestRules1710.Rule::namedMaterial)
                .collect(Collectors.toSet());
        assertEquals(Set.of("rock", "iron", "anvil", "snow", "craftedSnow", "web"), requiresTool);

        var wood = LegacyMaterialHarvestRules1710.lookup(
                LegacyMaterialHarvestRules1710.MATERIAL_OWNER,
                "field_151575_d",
                LegacyMaterialHarvestRules1710.MATERIAL_DESCRIPTOR
        ).orElseThrow();
        assertEquals("wood", wood.namedMaterial());
        assertTrue(wood.toolNotRequired());

        var rock = LegacyMaterialHarvestRules1710.lookup(
                LegacyMaterialHarvestRules1710.MATERIAL_OWNER,
                "field_151576_e",
                LegacyMaterialHarvestRules1710.MATERIAL_DESCRIPTOR
        ).orElseThrow();
        assertEquals("rock", rock.namedMaterial());
        assertFalse(rock.toolNotRequired());

        assertTrue(LegacyMaterialHarvestRules1710.lookup(
                "foreign/Material", "field_151575_d", LegacyMaterialHarvestRules1710.MATERIAL_DESCRIPTOR).isEmpty());
        assertTrue(LegacyMaterialHarvestRules1710.lookup(
                LegacyMaterialHarvestRules1710.MATERIAL_OWNER, "unknown", LegacyMaterialHarvestRules1710.MATERIAL_DESCRIPTOR).isEmpty());
        assertTrue(LegacyMaterialHarvestRules1710.lookup(
                LegacyMaterialHarvestRules1710.MATERIAL_OWNER, "field_151575_d", "Ljava/lang/Object;").isEmpty());
    }
}
