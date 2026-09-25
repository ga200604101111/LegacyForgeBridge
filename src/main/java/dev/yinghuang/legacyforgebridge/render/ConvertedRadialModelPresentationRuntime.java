package dev.yinghuang.legacyforgebridge.render;

import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.compat.LegacyRadialConditionalModelRegistry;
import dev.yinghuang.legacyforgebridge.compat.LegacyRadialModelBlockRegistry;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyRadialModelBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Client BER registration for source-proven radial TESR base and conditional presentation rules. */
public final class ConvertedRadialModelPresentationRuntime {
    private static final Set<String> INITIALIZED=ConcurrentHashMap.newKeySet();
    private ConvertedRadialModelPresentationRuntime(){}
    public static void initializeMod(String modId){
        if(modId==null||modId.isBlank()||!INITIALIZED.add(modId))return;int count=0;
        try{
            LegacyRadialConditionalModelRegistry.loadMod(modId);
            for(var rule:LegacyRadialModelBlockRegistry.rules(modId)){
                BlockEntityType<ConvertedLegacyRadialModelBlockEntity> type=LegacyRadialModelBlockRegistry.type(rule.id());
                if(type==null){LegacyForgeBridge.LOGGER.error("Radial model {} has no BlockEntityType",rule.id());continue;}
                BlockEntityRenderers.register(type,context->new ConvertedLegacyRadialModelRenderer(context,rule));count++;
            }
        }catch(Exception exception){INITIALIZED.remove(modId);LegacyForgeBridge.LOGGER.error("Failed to initialize radial model presentation for {}",modId,exception);return;}
        if(count>0)LegacyForgeBridge.LOGGER.info("Initialized converted radial model presentation: mod={}, worldRenderers={}, conditionalRules={}",
                modId,count,LegacyRadialConditionalModelRegistry.rules(modId).size());
    }
}
