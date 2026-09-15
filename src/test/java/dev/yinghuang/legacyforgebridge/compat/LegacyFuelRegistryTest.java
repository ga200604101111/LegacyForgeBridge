package dev.longyu.legacyforgebridge.compat;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LegacyFuelRegistryTest {
    @AfterEach void clear() {
        LegacyFuelRegistry.clearForTests();
    }

    @Test void firstMatchingLegacyRuleWinsAndUnmatchedItemsFallThrough() {
        LegacyFuelRegistry.register("minecraft:stick", LegacyFuelRegistry.ANY, LegacyFuelRegistry.ANY, 30);
        LegacyFuelRegistry.register("minecraft:stick", 4, LegacyFuelRegistry.ANY, 270);

        assertEquals(30, LegacyFuelRegistry.burnDuration(Identifier.parse("minecraft:stick"), 4, 0));
        assertNull(LegacyFuelRegistry.burnDuration(Identifier.parse("minecraft:diamond"), 0, 0));
    }

    @Test void exactLegacyMetaAndModernDamagePredicatesAreBothSupported() {
        LegacyFuelRegistry.register("minecraft:stick", 4, LegacyFuelRegistry.ANY, 270);
        Identifier stick = Identifier.parse("minecraft:stick");
        assertNull(LegacyFuelRegistry.burnDuration(stick, 3, 0));
        assertEquals(270, LegacyFuelRegistry.burnDuration(stick, 4, 0));

        LegacyFuelRegistry.register("minecraft:iron_sword", LegacyFuelRegistry.ANY, 5, 80);
        Identifier sword = Identifier.parse("minecraft:iron_sword");
        assertNull(LegacyFuelRegistry.burnDuration(sword, 0, 4));
        assertEquals(80, LegacyFuelRegistry.burnDuration(sword, 0, 5));
    }
}
