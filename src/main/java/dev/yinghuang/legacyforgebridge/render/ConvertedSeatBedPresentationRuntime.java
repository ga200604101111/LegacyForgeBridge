package dev.yinghuang.legacyforgebridge.render;

import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.compat.LegacySeatBedPresentationRegistry;
import dev.yinghuang.legacyforgebridge.compat.LegacySeatBedRegistry;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacySeatBedBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Client-only registration for source-proven special two-part bed/seat BlockEntity presentation. */
public final class ConvertedSeatBedPresentationRuntime {
    private static final Set<String> INITIALIZED_MODS=ConcurrentHashMap.newKeySet();
    private ConvertedSeatBedPresentationRuntime(){ }

    public static void initializeMod(String modId){
        if(modId==null||modId.isBlank()||!INITIALIZED_MODS.add(modId))return;int renderers=0;
        try{
            LegacySeatBedPresentationRegistry.loadMod(modId);
            for(var rule:LegacySeatBedPresentationRegistry.rules(modId)){
                var core=LegacySeatBedRegistry.blockRule(rule.id());
                if(core==null||!core.specialPresentationRequired()){
                    LegacyForgeBridge.LOGGER.error("Seat-bed presentation {} has no proof-complete core rule",rule.id());
                    continue;
                }
                BlockEntityType<ConvertedLegacySeatBedBlockEntity> type=LegacySeatBedRegistry.type(rule.id());
                if(type==null){
                    LegacyForgeBridge.LOGGER.error("Seat-bed presentation {} has no BlockEntityType",rule.id());
                    continue;
                }
                BlockEntityRenderers.register(type,context->new ConvertedLegacySeatBedRenderer(context,rule));renderers++;
            }
        }catch(Exception e){
            INITIALIZED_MODS.remove(modId);LegacyForgeBridge.LOGGER.error("Failed to initialize seat-bed presentation for {}",modId,e);return;
        }
        if(renderers>0)LegacyForgeBridge.LOGGER.info("Initialized converted seat-bed presentation: mod={}, worldRenderers={}",modId,renderers);
    }
}
