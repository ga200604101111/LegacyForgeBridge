package dev.yinghuang.legacyforgebridge.convert.pass;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyGridPotVanillaInsertionEligibilityPassTest {
    @Test
    void onlyBoundedSafeVanillaSubsetMaterializesFromProvenLegacyRenderTypes() {
        assertEquals(List.of("minecraft:brown_mushroom", "minecraft:cactus", "minecraft:red_mushroom"),
                LegacyGridPotBlockPass.eligibleVanilla1710Ids(Set.of(1, 13, 40)));
        assertEquals(List.of("minecraft:brown_mushroom", "minecraft:red_mushroom"),
                LegacyGridPotBlockPass.eligibleVanilla1710Ids(Set.of(1)));
        assertEquals(List.of("minecraft:cactus"), LegacyGridPotBlockPass.eligibleVanilla1710Ids(Set.of(13)));
        assertEquals(List.of(), LegacyGridPotBlockPass.eligibleVanilla1710Ids(Set.of(40)));
        assertEquals(List.of(), LegacyGridPotBlockPass.eligibleVanilla1710Ids(Set.of(6)));
    }
}
