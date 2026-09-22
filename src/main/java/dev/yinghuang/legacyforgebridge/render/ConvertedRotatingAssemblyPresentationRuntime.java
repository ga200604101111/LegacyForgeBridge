package dev.yinghuang.legacyforgebridge.render;

import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.compat.LegacyRotatingAssemblyEntityRegistry;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyRotatingAssemblyEntity;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Client renderer registration for source-proven rotating legacy Entity assemblies. */
public final class ConvertedRotatingAssemblyPresentationRuntime {
    private static final Set<Identifier> REGISTERED=ConcurrentHashMap.newKeySet();
    private ConvertedRotatingAssemblyPresentationRuntime(){}

    public static void initializeMod(String modId){
        LegacyRotatingAssemblyEntityRegistry.loadMod(modId);int count=0;
        for(var rule:LegacyRotatingAssemblyEntityRegistry.rules(modId)){
            if(!REGISTERED.add(rule.id()))continue;
            EntityType<ConvertedLegacyRotatingAssemblyEntity> type=LegacyRotatingAssemblyEntityRegistry.type(rule.id());
            if(type==null)throw new IllegalStateException("Rotating assembly EntityType missing before renderer registration: "+rule.id());
            EntityRendererRegistry.register(type,context->new ConvertedLegacyRotatingAssemblyEntityRenderer(context,rule));count++;
        }
        if(count>0)LegacyForgeBridge.LOGGER.info("Registered converted rotating assembly renderers: mod={}, renderers={}",modId,count);
    }

    static void clearForTests(){REGISTERED.clear();}
}
