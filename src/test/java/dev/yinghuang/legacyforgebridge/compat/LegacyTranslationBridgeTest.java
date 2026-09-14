package dev.yinghuang.legacyforgebridge.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        assertTrue(LegacyTranslationBridge.shouldPreserveKey("gui.toMenu"));
        assertTrue(LegacyTranslationBridge.shouldPreserveKey("gameMode.changed"));
    }

    @Test
    void leavesContentAndOrdinaryModernUiTranslationsToViaAndModernMinecraft() {
        assertFalse(LegacyTranslationBridge.shouldPreserveKey("item.swordIron.name"));
        assertFalse(LegacyTranslationBridge.shouldPreserveKey("item.minecraft.iron_sword"));
        assertFalse(LegacyTranslationBridge.shouldPreserveKey("tile.stone.name"));
        assertFalse(LegacyTranslationBridge.shouldPreserveKey("block.minecraft.stone"));
        assertFalse(LegacyTranslationBridge.shouldPreserveKey("entity.Zombie.name"));
        assertFalse(LegacyTranslationBridge.shouldPreserveKey("container.inventory"));
        assertFalse(LegacyTranslationBridge.shouldPreserveKey("options.language"));
        assertFalse(LegacyTranslationBridge.shouldPreserveKey("forge.configgui.forgeConfigTitle"));
        assertFalse(LegacyTranslationBridge.shouldPreserveKey("fml.configgui.gameRestartTitle"));
    }

    @Test
    void preservesOnlyForgeMessageKeys() {
        assertTrue(LegacyTranslationBridge.shouldPreserveKey("commands.forge.usage"));
        assertTrue(LegacyTranslationBridge.shouldPreserveKey("forge.update.newversion"));
        assertTrue(LegacyTranslationBridge.shouldPreserveKey("forge.texture.preload.warning"));
        assertTrue(LegacyTranslationBridge.shouldPreserveKey("forge.client.shutdown.internal"));
        assertEquals(
                "lfb.forge.commands.forge.usage",
                LegacyTranslationBridge.aliasForPreservedKey("commands.forge.usage")
        );
    }

    @Test
    void generatedTraditionalChineseAliasesContainViaLegacyProblemKeysButNoItemNames() throws Exception {
        JsonObject minecraftZh = loadJson("/assets/lfb-minecraft/lang/zh_tw.json");
        JsonObject forgeZh = loadJson("/assets/lfb-forge/lang/zh_tw.json");

        assertEquals(
                "--- 顯示說明第 %s/%s 頁 (/help <頁數>) ---",
                minecraftZh.get(LegacyTranslationBridge.minecraftAlias("commands.help.header")).getAsString()
        );
        assertEquals(
                "/achievement give <成就名稱> [玩家]",
                minecraftZh.get(LegacyTranslationBridge.minecraftAlias("commands.achievement.usage")).getAsString()
        );
        assertEquals(
                "/clear <玩家> [物品] [物品數據值]",
                minecraftZh.get(LegacyTranslationBridge.minecraftAlias("commands.clear.usage")).getAsString()
        );
        assertEquals(
                "/effect <玩家> <效果> [秒數] [增幅]",
                minecraftZh.get(LegacyTranslationBridge.minecraftAlias("commands.effect.usage")).getAsString()
        );
        assertEquals(
                "正版驗證系統下線維修中",
                minecraftZh.get(
                        LegacyTranslationBridge.minecraftAlias("disconnect.loginFailedInfo.serversUnavailable")
                ).getAsString()
        );
        assertEquals(
                "回到標題畫面",
                minecraftZh.get(LegacyTranslationBridge.minecraftAlias("gui.toMenu")).getAsString()
        );
        assertEquals(
                "您的遊戲模式已更新",
                minecraftZh.get(LegacyTranslationBridge.minecraftAlias("gameMode.changed")).getAsString()
        );
        assertEquals(
                "警告：材質 %s 未被預載，可能會導致畫面異常！",
                forgeZh.get(LegacyTranslationBridge.forgeAlias("forge.texture.preload.warning")).getAsString()
        );

        assertFalse(minecraftZh.has(LegacyTranslationBridge.minecraftAlias("item.swordIron.name")));
        assertFalse(minecraftZh.has(LegacyTranslationBridge.minecraftAlias("tile.stone.name")));
        assertFalse(forgeZh.has(LegacyTranslationBridge.forgeAlias("forge.configgui.forgeConfigTitle")));
        assertFalse(forgeZh.has(LegacyTranslationBridge.forgeAlias("fml.configgui.gameRestartTitle")));
    }

    @Test
    void generatedEnglishAliasesContainLegacyHelpAndGameModeFallbacks() throws Exception {
        JsonObject minecraftEn = loadJson("/assets/lfb-minecraft/lang/en_us.json");
        assertEquals(
                "--- Showing help page %s of %s (/help <page>) ---",
                minecraftEn.get(LegacyTranslationBridge.minecraftAlias("commands.help.header")).getAsString()
        );
        assertEquals(
                "Back to title screen",
                minecraftEn.get(LegacyTranslationBridge.minecraftAlias("gui.toMenu")).getAsString()
        );
        assertEquals(
                "Your game mode has been updated",
                minecraftEn.get(LegacyTranslationBridge.minecraftAlias("gameMode.changed")).getAsString()
        );
    }

    private JsonObject loadJson(String path) throws Exception {
        try (InputStream stream = LegacyTranslationBridgeTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "Missing generated language resource: " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
