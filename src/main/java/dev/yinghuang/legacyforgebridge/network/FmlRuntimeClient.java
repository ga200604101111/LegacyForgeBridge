package dev.yinghuang.legacyforgebridge.network;

import dev.yinghuang.legacyforgebridge.compat.LegacySeatBedRegistry;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacySeatEntity;
import dev.yinghuang.legacyforgebridge.convert.runtime.LegacySeatEntityRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

/** Client-side dispatcher for the Forge 1.7.10 {@code FML} runtime channel. */
public final class FmlRuntimeClient {
    public enum Phase { CONFIGURATION, PLAY }

    public void handle(byte[] payload,Phase phase,FmlConnectionTrace trace){
        try{
            int discriminator=FmlRuntimeCodec.discriminator(payload);
            switch(discriminator){
                case FmlRuntimeCodec.COMPLETE_HANDSHAKE->handleCompleteHandshake(payload,phase,trace);
                case FmlRuntimeCodec.OPEN_GUI->handleOpenGui(payload,phase,trace);
                case FmlRuntimeCodec.ENTITY_SPAWN->handleEntitySpawn(payload,phase,trace);
                case FmlRuntimeCodec.ENTITY_ADJUST->handleEntityAdjust(payload,phase,trace);
                default->trace.packet("IN","FML",payload,"Unknown Forge/FML 1.7.10 runtime packet during "+phase);
            }
        }catch(RuntimeException exception){trace.packet("IN","FML",payload,"Failed to decode Forge/FML 1.7.10 runtime packet during "+phase+": "+exception.getMessage());}
    }

    private void handleCompleteHandshake(byte[] payload,Phase phase,FmlConnectionTrace trace){
        FmlRuntimeCodec.CompleteHandshake message=FmlRuntimeCodec.parseCompleteHandshake(payload);
        trace.packet("IN","FML",payload,"CompleteHandshake target="+message.target()+" ordinal="+message.targetOrdinal()+" trailingBytes="+message.trailingBytes()+" phase="+phase);
        trace.event("FML runtime CompleteHandshake accepted; target="+message.target());
    }

    private void handleOpenGui(byte[] payload,Phase phase,FmlConnectionTrace trace){
        FmlRuntimeCodec.OpenGui message=FmlRuntimeCodec.parseOpenGui(payload);
        trace.packet("IN","FML",payload,"OpenGui windowId="+message.windowId()+" modId="+message.modId()+" modGuiId="+message.modGuiId()+" pos="+message.x()+","+message.y()+","+message.z()+" trailingBytes="+message.trailingBytes()+" phase="+phase+" (decoded; converted-mod GUI dispatch not implemented yet)");
    }

    private void handleEntitySpawn(byte[] payload,Phase phase,FmlConnectionTrace trace){
        FmlRuntimeCodec.EntitySpawnHeader message=FmlRuntimeCodec.parseEntitySpawnHeader(payload);
        LegacySeatBedRegistry.Rule seatRule=LegacySeatBedRegistry.remoteSpawnRule(message.modId(),message.modEntityTypeId());
        trace.packet("IN","FML",payload,"EntitySpawnMessage entityId="+message.entityId()+" modId="+message.modId()+" modEntityTypeId="+message.modEntityTypeId()+" pos="+message.x()+","+message.y()+","+message.z()+" rot="+message.yaw()+","+message.pitch()+" headYaw="+message.headYaw()+" opaqueTailBytes="+message.remainingBytes()+" phase="+phase+(seatRule==null?" (header decoded; no admitted converted entity mapping)":" (matched proof-gated transient-seat mapping)"));
        if(phase!=Phase.PLAY||seatRule==null)return;

        final FmlRuntimeCodec.SimpleEntitySpawn spawn;
        try{spawn=FmlRuntimeCodec.parseSimpleEntitySpawn(payload);}
        catch(RuntimeException unsafe){trace.event("EntitySpawnMessage matched transient-seat identity but tail was outside the admitted simple-entity boundary; entity="+message.entityId()+" reason="+unsafe.getMessage());return;}
        if(!spawn.plainNonThrowable()){
            trace.event("EntitySpawnMessage matched transient-seat identity but carried throwable/additional spawn data; entity="+message.entityId()+" throwerId="+spawn.throwerId()+" additionalBytes="+spawn.additionalSpawnBytes());
            return;
        }
        Minecraft client=Minecraft.getInstance();client.execute(()->applyRemoteSeatSpawn(client,spawn,trace));
    }

    private void applyRemoteSeatSpawn(Minecraft client,FmlRuntimeCodec.SimpleEntitySpawn spawn,FmlConnectionTrace trace){
        ClientLevel level=client.level;FmlRuntimeCodec.EntitySpawnHeader message=spawn.header();
        if(level==null){trace.event("Converted transient-seat spawn skipped: client level is null; entity="+message.entityId());return;}
        ConvertedLegacySeatEntity entity=new ConvertedLegacySeatEntity(LegacySeatEntityRuntime.type(),level);
        entity.setId(message.entityId());entity.setPos(message.x(),message.y(),message.z());entity.setYRot(message.yaw());entity.setXRot(message.pitch());
        entity.syncPacketPositionCodec(message.x(),message.y(),message.z());level.addEntity(entity);
        trace.event("Converted legacy FML transient-seat entity spawned; entity="+message.entityId()+" legacy="+message.modId()+":"+message.modEntityTypeId()+" watcherEntries="+spawn.watcherEntries());
    }

    private void handleEntityAdjust(byte[] payload,Phase phase,FmlConnectionTrace trace){
        FmlRuntimeCodec.EntityAdjust message=FmlRuntimeCodec.parseEntityAdjust(payload);
        trace.packet("IN","FML",payload,"EntityAdjustMessage entityId="+message.entityId()+" serverPosRaw="+message.serverX()+","+message.serverY()+","+message.serverZ()+" baseline="+message.x()+","+message.y()+","+message.z()+" trailingBytes="+message.trailingBytes()+" phase="+phase);
        if(phase!=Phase.PLAY){trace.event("EntityAdjustMessage decoded outside PLAY; baseline update skipped for entity="+message.entityId());return;}
        Minecraft client=Minecraft.getInstance();client.execute(()->applyEntityAdjust(client,message,trace));
    }

    private void applyEntityAdjust(Minecraft client,FmlRuntimeCodec.EntityAdjust message,FmlConnectionTrace trace){
        ClientLevel level=client.level;if(level==null){trace.event("EntityAdjustMessage could not be applied: client level is null; entity="+message.entityId());return;}
        Entity entity=level.getEntity(message.entityId());if(entity==null){trace.event("EntityAdjustMessage target entity is not present; entity="+message.entityId());return;}
        entity.syncPacketPositionCodec(message.x(),message.y(),message.z());trace.event("EntityAdjustMessage applied to packet-position baseline; entity="+message.entityId()+" baseline="+message.x()+","+message.y()+","+message.z());
    }
}
