package dev.yinghuang.legacyforgebridge.render;

import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.compat.LegacyPlainEntityRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Client registration bridge for proof-complete no-op plain converted entities. */
public final class ConvertedPlainEntityPresentationRuntime {
    private static final Set<Identifier> REGISTERED = ConcurrentHashMap.newKeySet();

    private ConvertedPlainEntityPresentationRuntime() { }

    public static void initializeMod(String modId) {
        LegacyPlainEntityRegistry.loadMod(modId);
        int registered = 0;
        for (LegacyPlainEntityRegistry.Rule rule : LegacyPlainEntityRegistry.rules(modId)) {
            if (!REGISTERED.add(rule.id())) continue;
            EntityType<? extends Entity> type = LegacyPlainEntityRegistry.type(rule.id());
            if (type == null) throw new IllegalStateException("Plain converted EntityType missing before client renderer registration: " + rule.id());
            register(type);
            registered++;
        }
        if (registered > 0) LegacyForgeBridge.LOGGER.info(
                "Registered converted plain Entity no-op renderers: mod={}, renderers={}", modId, registered);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void register(EntityType<? extends Entity> type) {
        EntityRendererRegistry.register((EntityType) type, context -> new ConvertedLegacyNoOpEntityRenderer(context));
    }

    static void clearForTests() { REGISTERED.clear(); }
}
