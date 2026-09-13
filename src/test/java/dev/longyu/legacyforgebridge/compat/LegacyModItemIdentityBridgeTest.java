package dev.longyu.legacyforgebridge.compat;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LegacyModItemIdentityBridgeTest {
    @AfterEach
    void clear() {
        LegacyModItemRegistryMap.clear();
    }

    @Test
    void fmlRegistrySyncBuildsOnlyNonVanillaItemMappingsWithoutViaRuntime() {
        Map<String, Integer> ids = new LinkedHashMap<>();
        ids.put("\u0001rpgtool1:fake_block", 5000);
        ids.put("\u0002minecraft:stone", 1);
        ids.put("\u0002rpgtool1:dark_sword", 4169);
        ids.put("\u0002rpgtool1:water_sword", 4172);

        assertEquals(2, LegacyModItemRegistryMap.install(ids));
        assertEquals(2, LegacyModItemRegistryMap.mappedItemCount());
        assertEquals(
                Identifier.parse("rpgtool1:dark_sword"),
                LegacyModItemRegistryMap.legacyIdentity(4169)
        );
        assertEquals(
                4172,
                LegacyModItemRegistryMap.legacyNumericId(Identifier.parse("rpgtool1:water_sword"))
        );
        assertNull(LegacyModItemRegistryMap.legacyIdentity(1));
        assertNull(LegacyModItemRegistryMap.legacyIdentity(5000));
    }
}
