package dev.longyu.legacyforgebridge.convert;

import java.util.Map;

/**
 * Execution ownership for legacy Forge/FML event families.
 *
 * <p>This is intentionally independent from any mod ID. A callback compiler must
 * not turn a source-proven registration into client execution unless the event
 * family is presentation-only or a later adapter can prove equivalent side
 * semantics. In particular, authoritative gameplay callbacks remain on the
 * legacy server while a Fabric client is connected to it.</p>
 */
public final class LegacyEventPolicy {
    private LegacyEventPolicy() { }

    public enum Execution {
        /** Safe candidate for client-side presentation adaptation. */
        CLIENT_PRESENTATION,
        /** Gameplay authority belongs to the legacy server; never duplicate on the client. */
        SERVER_AUTHORITATIVE,
        /** The legacy event can be observed on multiple sides and needs more source/context proof. */
        CONTEXTUAL,
        /** No generic policy exists yet; fail closed. */
        UNSUPPORTED
    }

    private static final Map<String, Execution> POLICIES = Map.ofEntries(
            Map.entry("net/minecraftforge/event/entity/player/ItemTooltipEvent", Execution.CLIENT_PRESENTATION),
            Map.entry("net/minecraftforge/event/entity/PlaySoundAtEntityEvent", Execution.CLIENT_PRESENTATION),
            Map.entry("net/minecraftforge/client/event/RenderLivingEvent$Specials$Pre", Execution.CLIENT_PRESENTATION),
            Map.entry("net/minecraftforge/event/entity/player/PlayerEvent$NameFormat", Execution.CONTEXTUAL),
            Map.entry("cpw/mods/fml/common/gameevent/TickEvent$PlayerTickEvent", Execution.CONTEXTUAL),
            Map.entry("net/minecraftforge/event/entity/living/LivingDropsEvent", Execution.SERVER_AUTHORITATIVE),
            Map.entry("net/minecraftforge/event/entity/living/LivingDeathEvent", Execution.SERVER_AUTHORITATIVE),
            Map.entry("net/minecraftforge/event/entity/player/AttackEntityEvent", Execution.SERVER_AUTHORITATIVE),
            Map.entry("cpw/mods/fml/common/gameevent/PlayerEvent$ItemCraftedEvent", Execution.SERVER_AUTHORITATIVE),
            Map.entry("net/minecraftforge/event/entity/player/ArrowNockEvent", Execution.SERVER_AUTHORITATIVE),
            Map.entry("net/minecraftforge/event/entity/player/ArrowLooseEvent", Execution.SERVER_AUTHORITATIVE),
            // Existing compatibility callbacks already have explicit client bridge semantics.
            Map.entry("net/minecraftforge/event/entity/living/LivingEvent$LivingJumpEvent", Execution.CONTEXTUAL),
            Map.entry("net/minecraftforge/event/entity/living/LivingFallEvent", Execution.CONTEXTUAL),
            Map.entry("net/minecraftforge/event/entity/living/LivingHurtEvent", Execution.CONTEXTUAL)
    );

    public static Execution execution(String eventType) {
        return POLICIES.getOrDefault(eventType, Execution.UNSUPPORTED);
    }

    public static boolean mayAutoExecuteOnClient(String eventType) {
        return execution(eventType) == Execution.CLIENT_PRESENTATION;
    }
}
