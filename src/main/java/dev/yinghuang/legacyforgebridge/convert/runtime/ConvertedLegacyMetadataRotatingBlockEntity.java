package dev.yinghuang.legacyforgebridge.convert.runtime;

import dev.yinghuang.legacyforgebridge.compat.LegacyMetadataRotatingTesrRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Client visual phase accumulator for metadata-speed rotating legacy TESRs. */
public final class ConvertedLegacyMetadataRotatingBlockEntity extends BlockEntity {
    private long lastVisualGameTime=Long.MIN_VALUE;
    private float visualDegrees;

    public ConvertedLegacyMetadataRotatingBlockEntity(BlockPos pos,BlockState state){
        super(LegacyMetadataRotatingTesrRegistry.requireType(state.getBlock()),pos,state);
    }

    public float visualDegrees(long gameTime,int legacyMeta,LegacyMetadataRotatingTesrRegistry.Rule rule){
        int speed=legacyMeta&rule.metadataMask();
        if(speed==0){visualDegrees=0F;lastVisualGameTime=gameTime;return 0F;}
        if(lastVisualGameTime==Long.MIN_VALUE){lastVisualGameTime=gameTime;return visualDegrees;}
        long delta=gameTime-lastVisualGameTime;
        if(delta>0L){
            // Do not let an unload/reload or clock discontinuity manufacture an enormous frame jump.
            long bounded=Math.min(delta,20L*60L);
            visualDegrees=(visualDegrees+bounded*speed*rule.degreesPerMetadataPerTick())%360F;
            lastVisualGameTime=gameTime;
        }else if(delta<0L)lastVisualGameTime=gameTime;
        return visualDegrees;
    }
}
