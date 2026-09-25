package dev.yinghuang.legacyforgebridge.compat;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LegacyFoodItemRegistryTest {
    @AfterEach void clear() { LegacyFoodItemRegistry.clearForTests(); }

    @Test void provenRuleIsStableAndCanMaterializeModernFoodProperties() {
        Identifier id = Identifier.parse("foreign:berry");
        var rule = new LegacyFoodItemRegistry.Rule(id, 4, 0.3F, true);
        LegacyFoodItemRegistry.registerForTests(rule);
        LegacyFoodItemRegistry.registerForTests(rule);
        assertEquals(rule, LegacyFoodItemRegistry.rule(id));
        assertNotNull(LegacyFoodItemRegistry.foodProperties(rule));
    }

    @Test void conflictingOrInvalidRulesFailClosed() {
        Identifier id = Identifier.parse("foreign:berry");
        LegacyFoodItemRegistry.registerForTests(new LegacyFoodItemRegistry.Rule(id, 4, 0.3F, false));
        assertThrows(IllegalStateException.class, () -> LegacyFoodItemRegistry.registerForTests(
                new LegacyFoodItemRegistry.Rule(id, 5, 0.3F, false)));
        assertThrows(IllegalArgumentException.class, () ->
                new LegacyFoodItemRegistry.Rule(id, -1, 0.3F, false));
        assertThrows(IllegalArgumentException.class, () ->
                new LegacyFoodItemRegistry.Rule(id, 1, Float.NaN, false));
    }
}
