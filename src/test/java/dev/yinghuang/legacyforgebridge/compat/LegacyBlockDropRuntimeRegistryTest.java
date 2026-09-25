package dev.yinghuang.legacyforgebridge.compat;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyBlockDropRuntimeRegistryTest {
    @AfterEach
    void clearRegistry() {
        LegacyBlockDropRuntimeRegistry.clearForTests();
    }

    @Test
    void absentRuleFailsClosed() {
        Identifier id = Identifier.fromNamespaceAndPath("fixture", "missing");
        LegacyBlockDropRuntimeRegistry.suppressLoadForTests("fixture");
        assertNull(LegacyBlockDropRuntimeRegistry.rule(id));
        assertFalse(LegacyBlockDropRuntimeRegistry.hasRule(id));
        assertFalse(LegacyBlockDropRuntimeRegistry.shouldDropFromExplosion(id, 0.0F, 4.0F));
    }

    @Test
    void admittedRuleUsesExactLegacyInverseRadiusThreshold() {
        Identifier id = Identifier.fromNamespaceAndPath("fixture", "wood");
        LegacyBlockDropRuntimeRegistry.installForTests(id);

        assertNotNull(LegacyBlockDropRuntimeRegistry.rule(id));
        assertTrue(LegacyBlockDropRuntimeRegistry.hasRule(id));
        assertTrue(LegacyBlockDropRuntimeRegistry.shouldDropFromExplosion(id, 0.0F, 4.0F));
        assertTrue(LegacyBlockDropRuntimeRegistry.shouldDropFromExplosion(id, 0.25F, 4.0F));
        assertFalse(LegacyBlockDropRuntimeRegistry.shouldDropFromExplosion(id, 0.25001F, 4.0F));
        assertTrue(LegacyBlockDropRuntimeRegistry.shouldDropFromExplosion(id, 0.5F, 2.0F));
        assertFalse(LegacyBlockDropRuntimeRegistry.shouldDropFromExplosion(id, 0.50001F, 2.0F));
    }
}
