package dev.yinghuang.legacyforgebridge.render;

import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.compat.LegacyVisibleEntityRegistry;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyVisualEntity;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Registers modern client renderers for source-proven visible legacy Entity rules. */
public final class ConvertedVisibleEntityPresentationRuntime {
    private static final Set<Identifier> REGISTERED=ConcurrentHashMap.newKeySet();
    private ConvertedVisibleEntityPresentationRuntime(){}

    public static void initializeMod(String modId){
        LegacyVisibleEntityRegistry.loadMod(modId);int count=0;
        for(var rule:LegacyVisibleEntityRegistry.rules(modId)){
            if(!REGISTERED.add(rule.id()))continue;EntityType<ConvertedLegacyVisualEntity> type=LegacyVisibleEntityRegistry.type(rule.id());
            if(type==null)throw new IllegalStateException("Visible converted EntityType missing before renderer registration: "+rule.id());
            EntityRendererRegistry.register(type,context->new ConvertedLegacyVisualEntityRenderer(context,rule));count++;
        }
        if(count>0)LegacyForgeBridge.LOGGER.info("Registered converted visible Entity renderers: mod={}, renderers={}",modId,count);
    }
    static void clearForTests(){REGISTERED.clear();}
}
