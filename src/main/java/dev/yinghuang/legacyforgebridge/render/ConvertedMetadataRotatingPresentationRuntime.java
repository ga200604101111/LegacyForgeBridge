package dev.yinghuang.legacyforgebridge.render;

import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.compat.LegacyMetadataRotatingTesrRegistry;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyMetadataRotatingBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Client BER registration for metadata-speed rotating legacy TESR presentation. */
public final class ConvertedMetadataRotatingPresentationRuntime {
    private static final Set<String> INITIALIZED=ConcurrentHashMap.newKeySet();
    private ConvertedMetadataRotatingPresentationRuntime(){}
    public static void initializeMod(String modId){
        if(modId==null||modId.isBlank()||!INITIALIZED.add(modId))return;int count=0;
        try{
            for(var rule:LegacyMetadataRotatingTesrRegistry.rules(modId)){
                BlockEntityType<ConvertedLegacyMetadataRotatingBlockEntity> type=LegacyMetadataRotatingTesrRegistry.type(rule.id());
                if(type==null){LegacyForgeBridge.LOGGER.error("Metadata rotating model {} has no BlockEntityType",rule.id());continue;}
                BlockEntityRenderers.register(type,context->new ConvertedLegacyMetadataRotatingRenderer(context,rule));count++;
            }
        }catch(Exception exception){INITIALIZED.remove(modId);LegacyForgeBridge.LOGGER.error("Failed to initialize metadata rotating TESR presentation for {}",modId,exception);return;}
        if(count>0)LegacyForgeBridge.LOGGER.info("Initialized metadata rotating TESR presentation: mod={}, worldRenderers={}",modId,count);
    }
}
