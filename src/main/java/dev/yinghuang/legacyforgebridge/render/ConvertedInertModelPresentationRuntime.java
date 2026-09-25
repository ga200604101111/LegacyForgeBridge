package dev.yinghuang.legacyforgebridge.render;

import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.compat.LegacyInertModelBlockRegistry;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyInertModelBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Client-only registration for converted inert fixed-cuboid BlockEntity renderers. */
public final class ConvertedInertModelPresentationRuntime {
    private static final Set<String> INITIALIZED_MODS=ConcurrentHashMap.newKeySet();
    private ConvertedInertModelPresentationRuntime() { }
    public static void initializeMod(String modId){
        if(modId==null||modId.isBlank()||!INITIALIZED_MODS.add(modId))return;int renderers=0;
        try{
            for(var rule:LegacyInertModelBlockRegistry.rules(modId)){
                BlockEntityType<ConvertedLegacyInertModelBlockEntity> type=LegacyInertModelBlockRegistry.type(rule.id());
                if(type==null){LegacyForgeBridge.LOGGER.error("Inert model presentation {} has no BlockEntityType",rule.id());continue;}
                BlockEntityRenderers.register(type,context->new ConvertedLegacyInertModelRenderer(context,rule));renderers++;
            }
        }catch(Exception exception){INITIALIZED_MODS.remove(modId);LegacyForgeBridge.LOGGER.error("Failed to initialize inert model presentation for {}",modId,exception);return;}
        if(renderers>0)LegacyForgeBridge.LOGGER.info("Initialized converted inert model presentation: mod={}, worldRenderers={}",modId,renderers);
    }
}
