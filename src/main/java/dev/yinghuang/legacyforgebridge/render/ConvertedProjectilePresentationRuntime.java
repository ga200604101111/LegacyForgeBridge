package dev.yinghuang.legacyforgebridge.render;

import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.compat.LegacyProjectilePresentationRegistry;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyRemoteProjectile;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client renderer registration for source-proven remote projectile carriers.
 *
 * <p>The source-bound converted item model is used as the presentation primitive. Throwable icon
 * renderers map directly to a modern thrown-item billboard. Arrow-family custom renderers use the
 * same bound item identity as a conservative visible carrier until their source quad geometry can
 * be materialized separately.</p>
 */
public final class ConvertedProjectilePresentationRuntime {
    private static final Set<Identifier> REGISTERED=ConcurrentHashMap.newKeySet();
    private ConvertedProjectilePresentationRuntime(){}

    public static void initializeMod(String modId){
        LegacyProjectilePresentationRegistry.loadMod(modId);int count=0;
        for(var rule:LegacyProjectilePresentationRegistry.rules(modId)){
            if(!REGISTERED.add(rule.id()))continue;
            EntityType<ConvertedLegacyRemoteProjectile> type=LegacyProjectilePresentationRegistry.type(rule.id());
            if(type==null)throw new IllegalStateException("Remote projectile EntityType missing before renderer registration: "+rule.id());
            EntityRendererRegistry.register(type,ThrownItemRenderer::new);count++;
        }
        if(count>0)LegacyForgeBridge.LOGGER.info("Registered converted remote projectile renderers: mod={}, renderers={}",modId,count);
    }

    static void clearForTests(){REGISTERED.clear();}
}
