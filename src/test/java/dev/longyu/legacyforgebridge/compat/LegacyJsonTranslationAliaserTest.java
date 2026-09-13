package dev.longyu.legacyforgebridge.compat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyJsonTranslationAliaserTest {
    @Test
    void aliasesViaLegacyHardcodedCommandUsagesBeforeTheFirstRewrite() {
        assertTranslateAlias(
                "{\"translate\":\"commands.achievement.usage\"}",
                "lfb.minecraft.commands.achievement.usage"
        );
        assertTranslateAlias(
                "{\"translate\":\"commands.clear.usage\"}",
                "lfb.minecraft.commands.clear.usage"
        );
        assertTranslateAlias(
                "{\"translate\":\"commands.effect.usage\"}",
                "lfb.minecraft.commands.effect.usage"
        );
    }

    @Test
    void aliasesDisconnectAndLegacyPresentationKeys() {
        assertTranslateAlias(
                "{\"translate\":\"disconnect.loginFailedInfo.serversUnavailable\"}",
                "lfb.minecraft.disconnect.loginFailedInfo.serversUnavailable"
        );
        assertTranslateAlias(
                "{\"translate\":\"gui.toMenu\"}",
                "lfb.minecraft.gui.toMenu"
        );
    }

    @Test
    void aliasesNestedLegacyMessagesWithoutTouchingArguments() {
        String input = """
                {
                  "translate": "chat.type.admin",
                  "with": [
                    "Server",
                    {"translate": "commands.clear.usage"}
                  ],
                  "extra": [
                    {"translate": "commands.forge.usage"}
                  ]
                }
                """;

        JsonObject output = JsonParser.parseString(
                LegacyJsonTranslationAliaser.aliasPreservedTranslations(input)
        ).getAsJsonObject();

        assertEquals("lfb.minecraft.chat.type.admin", output.get("translate").getAsString());

        JsonElement nestedCommand = output.getAsJsonArray("with").get(1);
        assertEquals(
                "lfb.minecraft.commands.clear.usage",
                nestedCommand.getAsJsonObject().get("translate").getAsString()
        );

        JsonElement nestedForge = output.getAsJsonArray("extra").get(0);
        assertEquals(
                "lfb.forge.commands.forge.usage",
                nestedForge.getAsJsonObject().get("translate").getAsString()
        );
    }

    @Test
    void leavesContentKeysAndMalformedInputUntouched() {
        String item = "{\"translate\":\"item.swordIron.name\"}";
        String block = "{\"translate\":\"tile.stone.name\"}";
        String malformed = "{\"translate\":";

        assertEquals(item, LegacyJsonTranslationAliaser.aliasPreservedTranslations(item));
        assertEquals(block, LegacyJsonTranslationAliaser.aliasPreservedTranslations(block));
        assertEquals(malformed, LegacyJsonTranslationAliaser.aliasPreservedTranslations(malformed));
    }

    private void assertTranslateAlias(String input, String expected) {
        JsonObject output = JsonParser.parseString(
                LegacyJsonTranslationAliaser.aliasPreservedTranslations(input)
        ).getAsJsonObject();
        assertEquals(expected, output.get("translate").getAsString());
    }
}
