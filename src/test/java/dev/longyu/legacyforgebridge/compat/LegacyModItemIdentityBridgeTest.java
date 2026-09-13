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
        LegacyModItemIdentityBridge.clear();
    }

    @Test
    void fmlRegistrySyncBuildsOnlyNonVanillaItemMappings() {
        Map<String, Integer> ids = new LinkedHashMap<>();
        ids.put("\u0001rpgtool1:fake_block", 5000);
        ids.put("\u0002minecraft:stone", 1);
        ids.put("\u0002rpgtool1:dark_sword", 4169);
        ids.put("\u0002rpgtool1:water_sword", 4172);

        assertEquals(2, LegacyModItemIdentityBridge.install(ids));
        assertEquals(2, LegacyModItemIdentityBridge.mappedItemCount());
        assertEquals(
                Identifier.parse("rpgtool1:dark_sword"),
                LegacyModItemIdentityBridge.legacyIdentity(4169)
        );
        assertEquals(
                4172,
                LegacyModItemIdentityBridge.legacyNumericId(Identifier.parse("rpgtool1:water_sword"))
        );
        assertNull(LegacyModItemIdentityBridge.legacyIdentity(1));
        assertNull(LegacyModItemIdentityBridge.legacyIdentity(5000));
    }
}
