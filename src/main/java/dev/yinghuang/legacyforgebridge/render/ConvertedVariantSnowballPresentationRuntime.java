package dev.yinghuang.legacyforgebridge.render;

import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.compat.LegacyVariantSnowballRuntimeRegistry;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyVariantSnowballProjectile;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Client renderer registration for source-complete converted variant-snowball projectiles. */
public final class ConvertedVariantSnowballPresentationRuntime {
    private static final Set<Identifier> REGISTERED = ConcurrentHashMap.newKeySet();

    private ConvertedVariantSnowballPresentationRuntime() { }

    public static void initializeMod(String modId) {
        LegacyVariantSnowballRuntimeRegistry.loadMod(modId);
        int registered = 0;
        for (LegacyVariantSnowballRuntimeRegistry.Rule rule
                : LegacyVariantSnowballRuntimeRegistry.rules(modId)) {
            if (!REGISTERED.add(rule.projectileId())) continue;

            EntityType<ConvertedLegacyVariantSnowballProjectile> type =
                    LegacyVariantSnowballRuntimeRegistry.projectileType(rule.projectileId());
            if (type == null) {
                throw new IllegalStateException(
                        "Variant-snowball EntityType missing before renderer registration: "
                                + rule.projectileId());
            }
            EntityRendererRegistry.register(type, ThrownItemRenderer::new);
            registered++;
        }

        if (registered > 0) {
            LegacyForgeBridge.LOGGER.info(
                    "Registered converted variant-snowball thrown-item renderers: mod={}, renderers={}",
                    modId, registered);
        }
    }

    static void clearForTests() {
        REGISTERED.clear();
    }
}
