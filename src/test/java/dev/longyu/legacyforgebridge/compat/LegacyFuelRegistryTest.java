package dev.longyu.legacyforgebridge.compat;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LegacyFuelRegistryTest {
    @BeforeAll static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        // Production registers LFB-owned data components during mod initialization, before
        // Minecraft freezes the built-in registries. Keep the unit-test bootstrap in that same
        // order; registering legacy_meta after Bootstrap.bootStrap() is intentionally illegal.
        LegacyStackComponents.bootstrap();
        Bootstrap.bootStrap();
    }

    @AfterEach void clear() {
        LegacyFuelRegistry.clearForTests();
    }

    @Test void firstMatchingLegacyRuleWinsAndUnmatchedStacksFallThrough() {
        LegacyFuelRegistry.register("minecraft:stick", LegacyFuelRegistry.ANY, LegacyFuelRegistry.ANY, 30);
        LegacyFuelRegistry.register("minecraft:stick", 4, LegacyFuelRegistry.ANY, 270);

        ItemStack stack = new ItemStack(Items.STICK);
        LegacyStackComponents.set(stack, 4);
        assertEquals(30, LegacyFuelRegistry.burnDuration(stack));
        assertNull(LegacyFuelRegistry.burnDuration(new ItemStack(Items.DIAMOND)));
    }

    @Test void exactLegacyMetaAndModernDamagePredicatesAreBothSupported() {
        LegacyFuelRegistry.register("minecraft:stick", 4, LegacyFuelRegistry.ANY, 270);
        ItemStack stick = new ItemStack(Items.STICK);
        LegacyStackComponents.set(stick, 3);
        assertNull(LegacyFuelRegistry.burnDuration(stick));
        LegacyStackComponents.set(stick, 4);
        assertEquals(270, LegacyFuelRegistry.burnDuration(stick));

        LegacyFuelRegistry.register("minecraft:iron_sword", LegacyFuelRegistry.ANY, 5, 80);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.setDamageValue(4);
        assertNull(LegacyFuelRegistry.burnDuration(sword));
        sword.setDamageValue(5);
        assertEquals(80, LegacyFuelRegistry.burnDuration(sword));
    }
}
