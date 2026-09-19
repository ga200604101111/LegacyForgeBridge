package dev.yinghuang.legacyforgebridge.compat;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyModBlockStateBridgeTest {
    @AfterEach
    void clearSessionState() {
        LegacyModBlockStateBridge.reset();
        LegacyModBlockRegistryMap.clear();
        LegacyModItemRegistryMap.clear();
    }

    @Test
    void conversionAliasesAndDenseTokensSurviveProtocolBoundaries() {
        Map<String, Integer> ids = new LinkedHashMap<>();
        ids.put("\u0001example:beta_block", 3500);
        ids.put("\u0001example:alpha_block", 3000);
        ids.put("\u0002example:alpha_item", 5000);
        ids.put("\u0001minecraft:stone", 1);

        Map<String, Identifier> aliases = Map.of(
                "example:alpha_block", Identifier.parse("minecraft:stone"),
                "example:beta_block", Identifier.parse("minecraft:dirt")
        );

        assertEquals(2, LegacyModBlockRegistryMap.install(ids, aliases));
        assertEquals(2, LegacyModBlockRegistryMap.stateTokenCount());
        assertEquals(0, LegacyModBlockRegistryMap.stateToken((3000 << 4) | 7));
        assertEquals(0, LegacyModBlockRegistryMap.stateToken((3000 << 4) | 15));
        assertEquals(1, LegacyModBlockRegistryMap.stateToken(3500 << 4));
        assertEquals(-1, LegacyModBlockRegistryMap.stateToken(1 << 4));

        int flattened = LegacyModBlockStateBridge.enterLegacyState(
                (3000 << 4) | 7,
                "1.13",
                9000
        );
        assertEquals(9000, flattened);

        int carried = LegacyModBlockStateBridge.carryAcrossMapping(
                flattened,
                "1.13",
                9000,
                "1.14",
                12000
        );
        assertEquals(12000, carried);

        int nativeState = LegacyModBlockStateBridge.carryAcrossMapping(
                carried,
                "1.14",
                12000,
                "1.21.11",
                30000
        );
        assertEquals(
                Block.BLOCK_STATE_REGISTRY.getId(Blocks.STONE.defaultBlockState()),
                nativeState
        );
    }

    @Test
    void nonModStatesAndUnsafeCarrierRangesAreLeftToViaVersion() {
        Map<String, Integer> ids = Map.of(
                "\u0001example:first", 3000,
                "\u0001example:second", 3001
        );
        assertEquals(2, LegacyModBlockRegistryMap.install(ids));

        assertEquals(
                LegacyModBlockStateBridge.NO_MAPPING,
                LegacyModBlockStateBridge.enterLegacyState(1 << 4, "1.13", 9000)
        );
        assertEquals(
                LegacyModBlockStateBridge.NO_MAPPING,
                LegacyModBlockStateBridge.enterLegacyState(3000 << 4, "test-exact-power", 16)
        );
        assertEquals(-1, LegacyModBlockStateBridge.decodeToken(9002, 9000, 2));
    }

    @Test
    void itemRegistryAliasesUseTheSameCanonicalIdentityRules() {
        Map<String, Integer> ids = Map.of(
                "\u0002Example Mod:Crystal Item", 5000,
                "\u0002minecraft:stone", 1
        );
        Map<String, Identifier> aliases = Map.of(
                "example_mod:crystal_item",
                Identifier.parse("converted_mod:crystal_item")
        );

        assertEquals(1, LegacyModItemRegistryMap.install(ids, aliases));
        assertEquals(
                Identifier.parse("converted_mod:crystal_item"),
                LegacyModItemRegistryMap.legacyIdentity(5000)
        );
    }
}
