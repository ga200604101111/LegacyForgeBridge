package dev.yinghuang.legacyforgebridge.compat;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyModBlockRegistryMapMetadataTest {
    @AfterEach
    void clear() {
        LegacyModBlockRegistryMap.clear();
    }

    @Test
    void allocatesOneCarrierTokenForEveryLegacyMetadataValue() {
        Map<String, Integer> registry = new LinkedHashMap<>();
        registry.put("\u0001ExampleMod:decorativeBlock", 4095);
        Map<String, Identifier> aliases = Map.of(
                "examplemod:decorativeblock",
                Identifier.fromNamespaceAndPath("examplemod", "decorative_block"));

        assertEquals(1, LegacyModBlockRegistryMap.install(registry, aliases));
        assertEquals(16, LegacyModBlockRegistryMap.stateTokenCount());
        for (int metadata = 0; metadata < 16; metadata++) {
            int token = LegacyModBlockRegistryMap.stateToken((4095 << 4) | metadata);
            assertEquals(metadata, token);
            assertEquals(metadata, LegacyModBlockRegistryMap.legacyMetadataForStateToken(token));
            assertEquals(
                    Identifier.fromNamespaceAndPath("examplemod", "decorative_block"),
                    LegacyModBlockRegistryMap.modernIdentityForStateToken(token));
        }
    }

    @Test
    void differentBlocksReceiveDisjointMetadataRanges() {
        Map<String, Integer> registry = new LinkedHashMap<>();
        registry.put("\u0001example:first", 220);
        registry.put("\u0001example:second", 410);

        LegacyModBlockRegistryMap.install(registry);
        assertEquals(32, LegacyModBlockRegistryMap.stateTokenCount());
        assertEquals(5, LegacyModBlockRegistryMap.stateToken((220 << 4) | 5));
        assertEquals(21, LegacyModBlockRegistryMap.stateToken((410 << 4) | 5));
    }
}
