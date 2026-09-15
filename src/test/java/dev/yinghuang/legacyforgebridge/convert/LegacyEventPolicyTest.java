package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;

import static dev.yinghuang.legacyforgebridge.convert.LegacyEventPolicy.Execution.*;
import static org.junit.jupiter.api.Assertions.*;

class LegacyEventPolicyTest {
    @Test void presentationAndAuthoritativeFamiliesStaySeparated() {
        assertEquals(CLIENT_PRESENTATION, LegacyEventPolicy.execution(
                "net/minecraftforge/event/entity/player/ItemTooltipEvent"));
        assertEquals(CLIENT_PRESENTATION, LegacyEventPolicy.execution(
                "net/minecraftforge/event/entity/PlaySoundAtEntityEvent"));
        assertEquals(CLIENT_PRESENTATION, LegacyEventPolicy.execution(
                "net/minecraftforge/client/event/RenderLivingEvent$Specials$Pre"));

        assertEquals(SERVER_AUTHORITATIVE, LegacyEventPolicy.execution(
                "net/minecraftforge/event/entity/living/LivingDropsEvent"));
        assertEquals(SERVER_AUTHORITATIVE, LegacyEventPolicy.execution(
                "cpw/mods/fml/common/gameevent/PlayerEvent$ItemCraftedEvent"));
        assertEquals(SERVER_AUTHORITATIVE, LegacyEventPolicy.execution(
                "net/minecraftforge/event/entity/player/ArrowLooseEvent"));

        assertEquals(CONTEXTUAL, LegacyEventPolicy.execution(
                "cpw/mods/fml/common/gameevent/TickEvent$PlayerTickEvent"));
        assertEquals(UNSUPPORTED, LegacyEventPolicy.execution("third/party/event/UnknownEvent"));
        assertFalse(LegacyEventPolicy.mayAutoExecuteOnClient(
                "net/minecraftforge/event/entity/living/LivingDeathEvent"));
    }
}
