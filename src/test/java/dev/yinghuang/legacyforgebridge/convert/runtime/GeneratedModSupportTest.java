package dev.yinghuang.legacyforgebridge.convert.runtime;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeneratedModSupportTest {
    @Test
    void legacyWeaponKeepsDamageVisibleAndHidesOnlyModernAttackSpeedRow() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        ItemAttributeModifiers modifiers = GeneratedModSupport.legacyWeaponAttributes(12.5F, -2.4F);
        assertEquals(2, modifiers.modifiers().size());

        var damage = modifiers.modifiers().stream()
                .filter(entry -> entry.attribute().equals(Attributes.ATTACK_DAMAGE))
                .findFirst().orElseThrow();
        var speed = modifiers.modifiers().stream()
                .filter(entry -> entry.attribute().equals(Attributes.ATTACK_SPEED))
                .findFirst().orElseThrow();

        assertEquals(12.5D, damage.modifier().amount());
        assertEquals(ItemAttributeModifiers.Display.Type.DEFAULT, damage.display().type());
        assertEquals(-2.4D, speed.modifier().amount(), 0.000001D);
        assertEquals(ItemAttributeModifiers.Display.Type.HIDDEN, speed.display().type());
    }
}
