package dev.yinghuang.legacyforgebridge.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LegacyModBlockStateBridgeTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @AfterEach
    void reset() {
        LegacyModBlockStateBridge.reset();
        LegacyModBlockRegistryMap.clear();
    }

    @Test
    void createsStablePerConnectionTokensAndCarriesMetadataAcrossProtocolPalettes() {
        Map<String, Integer> registry = new LinkedHashMap<>();
        registry.put("\u0001example:alpha", 200);
        registry.put("\u0001example:beta", 400);
        registry.put("\u0002example:item", 600);
        registry.put("\u0001minecraft:stone", 1);

        Map<String, Identifier> aliases = Map.of(
                "example:alpha", Identifier.fromNamespaceAndPath("minecraft", "stone"),
                "example:beta", Identifier.fromNamespaceAndPath("minecraft", "dirt")
        );

        LegacyModBlockRegistryMap.install(registry, aliases);

        assertEquals(32, LegacyModBlockRegistryMap.stateTokenCount());
        assertEquals(7, LegacyModBlockRegistryMap.stateToken((200 << 4) | 7));
        assertEquals(15, LegacyModBlockRegistryMap.stateToken((200 << 4) | 15));
        assertEquals(16, LegacyModBlockRegistryMap.stateToken(400 << 4));
        assertEquals(23, LegacyModBlockRegistryMap.stateToken((400 << 4) | 7));
        assertEquals(
                Identifier.fromNamespaceAndPath("minecraft", "stone"),
                LegacyModBlockRegistryMap.modernIdentityForStateToken(7)
        );
        assertEquals(7, LegacyModBlockRegistryMap.legacyMetadataForStateToken(7));
        assertEquals(
                Identifier.fromNamespaceAndPath("minecraft", "dirt"),
                LegacyModBlockRegistryMap.modernIdentityForStateToken(16)
        );
        assertEquals(0, LegacyModBlockRegistryMap.legacyMetadataForStateToken(16));

        int flattenedCarrier = LegacyModBlockStateBridge.enterLegacyState(
                (200 << 4) | 7,
                "1.13",
                9000
        );
        assertEquals(9007, flattenedCarrier);

        int nextCarrier = LegacyModBlockStateBridge.carryAcrossMapping(
                flattenedCarrier,
                "1.13",
                9000,
                "1.14",
                12000
        );
        assertEquals(12007, nextCarrier);

        int nativeState = LegacyModBlockStateBridge.carryAcrossMapping(
                nextCarrier,
                "1.14",
                12000,
                "1.21.11",
                Block.BLOCK_STATE_REGISTRY.size()
        );
        assertEquals(
                Block.BLOCK_STATE_REGISTRY.getId(Blocks.STONE.defaultBlockState()),
                nativeState
        );
    }

    @Test
    void refusesToInterpretVanillaOrOutOfRangeStateIdsAsCarrierTokens() {
        assertFalse(LegacyModBlockStateBridge.canReserve(16, 32));
        assertEquals(-1, LegacyModBlockStateBridge.decodeToken(15, 16, 32));
        assertEquals(-1, LegacyModBlockStateBridge.decodeToken(9032, 9000, 32));
    }
}
