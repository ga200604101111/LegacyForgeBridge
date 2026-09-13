package dev.longyu.legacyforgebridge.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyTranslationBridgeTest {
    @Test
    void preservesVanillaServerMessageKeys() {
        assertTrue(LegacyTranslationBridge.shouldPreserveKey("commands.help.header"));
        assertTrue(LegacyTranslationBridge.shouldPreserveKey("chat.type.achievement"));
        assertTrue(LegacyTranslationBridge.shouldPreserveKey("death.attack.player"));
        assertTrue(LegacyTranslationBridge.shouldPreserveKey("multiplayer.player.joined"));
        assertTrue(LegacyTranslationBridge.shouldPreserveKey("achievement.openInventory"));
        assertTrue(LegacyTranslationBridge.shouldPreserveKey("stat.mineBlock"));
        assertTrue(LegacyTranslationBridge.shouldPreserveKey("tile.bed.noSleep"));
    }

    @Test
    void leavesContentAndUiTranslationsToViaAndModernMinecraft() {
        assertFalse(LegacyTranslationBridge.shouldPreserveKey("item.swordIron.name"));
        assertFalse(LegacyTranslationBridge.shouldPreserveKey("item.minecraft.iron_sword"));
        assertFalse(LegacyTranslationBridge.shouldPreserveKey("tile.stone.name"));
        assertFalse(LegacyTranslationBridge.shouldPreserveKey("block.minecraft.stone"));
        assertFalse(LegacyTranslationBridge.shouldPreserveKey("entity.Zombie.name"));
        assertFalse(LegacyTranslationBridge.shouldPreserveKey("container.inventory"));
        assertFalse(LegacyTranslationBridge.shouldPreserveKey("options.language"));
    }

    @Test
    void preservesForgeAndFmlTranslationIdentity() {
        assertTrue(LegacyTranslationBridge.shouldPreserveKey("forge.some.message"));
        assertTrue(LegacyTranslationBridge.shouldPreserveKey("fml.some.message"));
    }
}
