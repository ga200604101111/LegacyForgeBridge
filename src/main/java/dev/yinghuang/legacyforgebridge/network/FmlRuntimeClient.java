package dev.yinghuang.legacyforgebridge.network;

import dev.yinghuang.legacyforgebridge.compat.LegacyPlainEntityRegistry;
import dev.yinghuang.legacyforgebridge.compat.LegacyPlainEntityWatcherBridge;
import dev.yinghuang.legacyforgebridge.compat.LegacyProjectilePresentationRegistry;
import dev.yinghuang.legacyforgebridge.compat.LegacySeatBedRegistry;
import dev.yinghuang.legacyforgebridge.compat.LegacyVisibleEntityRegistry;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyRemoteProjectile;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacySeatEntity;
import dev.yinghuang.legacyforgebridge.convert.runtime.LegacySeatEntityRuntime;
import dev.yinghuang.legacyforgebridge.protocol.LegacyViaFmlEntityTrackerBridge;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyVisualEntity;
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
        LegacyVisibleEntityRegistry.Rule visibleRule=seatRule==null?LegacyVisibleEntityRegistry.remoteSpawnRule(message.modId(),message.modEntityTypeId()):null;
        LegacyProjectilePresentationRegistry.Rule projectileRule=seatRule==null&&visibleRule==null
                ?LegacyProjectilePresentationRegistry.remoteSpawnRule(message.modId(),message.modEntityTypeId()):null;
        LegacyPlainEntityRegistry.Rule plainRule=seatRule==null&&visibleRule==null&&projectileRule==null
                ?LegacyPlainEntityRegistry.remoteSpawnRule(message.modId(),message.modEntityTypeId()):null;
        String mapping=seatRule!=null?" (matched proof-gated transient-seat mapping)"
                :visibleRule!=null?" (matched proof-gated visible-entity mapping)"
                :projectileRule!=null?" (matched proof-gated remote-projectile mapping)"
                :plainRule!=null?" (matched proof-gated plain-entity mapping)"
                :" (header decoded; no admitted converted entity mapping)";
        trace.packet("IN","FML",payload,"EntitySpawnMessage entityId="+message.entityId()+" modId="+message.modId()+" modEntityTypeId="+message.modEntityTypeId()+" pos="+message.x()+","+message.y()+","+message.z()+" rot="+message.yaw()+","+message.pitch()+" headYaw="+message.headYaw()+" opaqueTailBytes="+message.remainingBytes()+" phase="+phase+mapping);
        if(phase!=Phase.PLAY||(seatRule==null&&visibleRule==null&&projectileRule==null&&plainRule==null))return;

        final FmlRuntimeCodec.SimpleEntitySpawn spawn;
        try{spawn=FmlRuntimeCodec.parseSimpleEntitySpawn(payload);}
        catch(RuntimeException unsafe){trace.event("EntitySpawnMessage matched converted entity identity but tail was outside the admitted simple-entity boundary; entity="+message.entityId()+" reason="+unsafe.getMessage());return;}
        if(projectileRule!=null){
            if(!spawn.additionalSpawnDataEmpty()){
                trace.event("EntitySpawnMessage matched remote projectile identity but carried unproven additional spawn data; entity="
                        +message.entityId()+" throwerId="+spawn.throwerId()+" additionalBytes="+spawn.additionalSpawnBytes());
                return;
            }
        }else if(!spawn.plainNonThrowable()){
            trace.event("EntitySpawnMessage matched non-projectile converted entity identity but carried throwable/additional spawn data; entity="
                    +message.entityId()+" throwerId="+spawn.throwerId()+" additionalBytes="+spawn.additionalSpawnBytes());
            return;
        }
        Minecraft client=Minecraft.getInstance();
        LegacyViaFmlEntityTrackerBridge.trackFmlEntity(client,message.entityId(),trace);
        if(seatRule!=null)client.execute(()->applyRemoteSeatSpawn(client,spawn,trace));
        else if(visibleRule!=null)client.execute(()->applyRemoteVisibleSpawn(client,spawn,visibleRule,trace));
        else if(projectileRule!=null)client.execute(()->applyRemoteProjectileSpawn(client,spawn,projectileRule,trace));
        else client.execute(()->applyRemotePlainSpawn(client,spawn,plainRule,trace));
    }

    private void applyRemoteSeatSpawn(Minecraft client,FmlRuntimeCodec.SimpleEntitySpawn spawn,FmlConnectionTrace trace){
        ClientLevel level=client.level;FmlRuntimeCodec.EntitySpawnHeader message=spawn.header();
        if(level==null){trace.event("Converted transient-seat spawn skipped: client level is null; entity="+message.entityId());return;}
        ConvertedLegacySeatEntity entity=new ConvertedLegacySeatEntity(LegacySeatEntityRuntime.type(),level);
        entity.setId(message.entityId());entity.setPos(message.x(),message.y(),message.z());entity.setYRot(message.yaw());entity.setXRot(message.pitch());
        entity.syncPacketPositionCodec(message.x(),message.y(),message.z());level.addEntity(entity);
        trace.event("Converted legacy FML transient-seat entity spawned; entity="+message.entityId()+" legacy="+message.modId()+":"+message.modEntityTypeId()+" watcherEntries="+spawn.watcherEntries());
    }

    private void applyRemoteVisibleSpawn(Minecraft client,FmlRuntimeCodec.SimpleEntitySpawn spawn,LegacyVisibleEntityRegistry.Rule rule,FmlConnectionTrace trace){
        ClientLevel level=client.level;FmlRuntimeCodec.EntitySpawnHeader message=spawn.header();
        if(level==null){trace.event("Converted visible Entity spawn skipped: client level is null; entity="+message.entityId());return;}
        ConvertedLegacyVisualEntity entity=LegacyVisibleEntityRegistry.create(rule.id(),level);
        if(entity==null){trace.event("Converted visible Entity spawn skipped: factory unavailable; entity="+message.entityId()+" id="+rule.id());return;}
        int customWatchers=0,baseWatchers=0;
        for(FmlRuntimeCodec.LegacyDataWatcherEntry watcher:spawn.watcherValues()){
            final boolean mapped;
            try{mapped=entity.applyLegacyWatcher(watcher.type(),watcher.id(),watcher.value());}
            catch(RuntimeException invalid){trace.event("Converted visible Entity spawn rejected while applying watcher; entity="+message.entityId()+" watcher="+watcher.id()+" type="+watcher.type()+" reason="+invalid.getClass().getSimpleName());return;}
            if(mapped){customWatchers++;continue;}
            if(isLegacyEntityBaseWatcher(watcher)){baseWatchers++;continue;}
            trace.event("Converted visible Entity spawn rejected: unmapped/non-default watcher; entity="+message.entityId()+" watcher="+watcher.id()+" type="+watcher.type());
            return;
        }
        entity.setId(message.entityId());entity.setPos(message.x(),message.y(),message.z());entity.setYRot(message.yaw());entity.setXRot(message.pitch());
        entity.syncPacketPositionCodec(message.x(),message.y(),message.z());level.addEntity(entity);
        trace.event("Converted legacy FML visible Entity spawned; entity="+message.entityId()+" legacy="+message.modId()+":"+message.modEntityTypeId()+" modern="+rule.id()+" adapter="+rule.adapter()+" baseWatchers="+baseWatchers+" customWatchers="+customWatchers);
    }

    private void applyRemoteProjectileSpawn(Minecraft client,FmlRuntimeCodec.SimpleEntitySpawn spawn,
                                            LegacyProjectilePresentationRegistry.Rule rule,FmlConnectionTrace trace){
        ClientLevel level=client.level;FmlRuntimeCodec.EntitySpawnHeader message=spawn.header();
        if(level==null){trace.event("Converted remote projectile spawn skipped: client level is null; entity="+message.entityId());return;}
        ConvertedLegacyRemoteProjectile entity=LegacyProjectilePresentationRegistry.create(rule.id(),level);
        if(entity==null){trace.event("Converted remote projectile spawn skipped: factory unavailable; entity="+message.entityId()+" id="+rule.id());return;}
        int customWatchers=0,baseWatchers=0;
        for(FmlRuntimeCodec.LegacyDataWatcherEntry watcher:spawn.watcherValues()){
            final boolean mapped;
            try{mapped=entity.applyLegacyWatcher(watcher.type(),watcher.id(),watcher.value());}
            catch(RuntimeException invalid){trace.event("Converted remote projectile spawn rejected while applying watcher; entity="+message.entityId()
                    +" watcher="+watcher.id()+" type="+watcher.type()+" reason="+invalid.getClass().getSimpleName());return;}
            if(mapped){customWatchers++;continue;}
            if(isLegacyEntityBaseWatcher(watcher)
                    ||(rule.baseFamily()==LegacyProjectilePresentationRegistry.BaseFamily.ARROW&&watcher.id()==16&&watcher.type()==0&&watcher.value() instanceof Byte)){
                baseWatchers++;continue;
            }
            trace.event("Converted remote projectile spawn rejected: unmapped watcher; entity="+message.entityId()
                    +" watcher="+watcher.id()+" type="+watcher.type()+" family="+rule.baseFamily());return;
        }
        entity.setId(message.entityId());entity.setPos(message.x(),message.y(),message.z());
        entity.setYRot(message.yaw());entity.setXRot(message.pitch());
        if(spawn.throwableEnvelope())entity.setDeltaMovement(spawn.velocityX(),spawn.velocityY(),spawn.velocityZ());
        entity.syncPacketPositionCodec(message.x(),message.y(),message.z());level.addEntity(entity);
        trace.event("Converted legacy FML remote projectile spawned; entity="+message.entityId()+" legacy="+message.modId()+":"
                +message.modEntityTypeId()+" modern="+rule.id()+" adapter="+rule.adapter()+" throwerId="+spawn.throwerId()
                +" velocity="+spawn.velocityX()+","+spawn.velocityY()+","+spawn.velocityZ()
                +" baseWatchers="+baseWatchers+" customWatchers="+customWatchers);
    }

    private void applyRemotePlainSpawn(Minecraft client,FmlRuntimeCodec.SimpleEntitySpawn spawn,LegacyPlainEntityRegistry.Rule rule,FmlConnectionTrace trace){
        ClientLevel level=client.level;FmlRuntimeCodec.EntitySpawnHeader message=spawn.header();
        if(level==null){trace.event("Converted plain Entity spawn skipped: client level is null; entity="+message.entityId());return;}
        Entity entity=LegacyPlainEntityRegistry.create(rule.id(),level);
        if(entity==null){trace.event("Converted plain Entity spawn skipped: registered Entity factory is unavailable; entity="+message.entityId()+" id="+rule.id());return;}
        if(!(entity instanceof LegacyPlainEntityWatcherBridge bridge)){
            trace.event("Converted plain Entity spawn skipped: generated Entity does not expose the proven watcher bridge; entity="+message.entityId()+" id="+rule.id());return;
        }
        int customWatchers=0,baseWatchers=0;
        for(FmlRuntimeCodec.LegacyDataWatcherEntry watcher:spawn.watcherValues()){
            final boolean mapped;
            try{mapped=bridge.legacyforgebridge$applyWatcher(watcher.id(),watcher.type(),watcher.value());}
            catch(RuntimeException invalid){trace.event("Converted plain Entity spawn rejected while applying legacy watcher; entity="+message.entityId()+" watcher="+watcher.id()+" type="+watcher.type()+" reason="+invalid.getClass().getSimpleName());return;}
            if(mapped){customWatchers++;continue;}
            if(isLegacyEntityBaseWatcher(watcher)){baseWatchers++;continue;}
            trace.event("Converted plain Entity spawn rejected: unmapped/non-default legacy base watcher; entity="+message.entityId()+" watcher="+watcher.id()+" type="+watcher.type()+" value="+watcher.value());
            return;
        }
        entity.setId(message.entityId());entity.setPos(message.x(),message.y(),message.z());entity.setYRot(message.yaw());entity.setXRot(message.pitch());
        entity.syncPacketPositionCodec(message.x(),message.y(),message.z());level.addEntity(entity);
        trace.event("Converted legacy FML plain Entity spawned; entity="+message.entityId()+" legacy="+message.modId()+":"+message.modEntityTypeId()+" modern="+rule.id()+" baseWatchers="+baseWatchers+" customWatchers="+customWatchers);
    }

    private static boolean isLegacyEntityBaseWatcher(FmlRuntimeCodec.LegacyDataWatcherEntry watcher){
        // Minecraft 1.7.10 Entity defines exactly watcher 0 (flags byte) and watcher 1 (air short).
        // Their values may legitimately be non-default by the time FML emits the spawn envelope.
        if(watcher.id()==0&&watcher.type()==0)return watcher.value() instanceof Byte;
        if(watcher.id()==1&&watcher.type()==1)return watcher.value() instanceof Short;
        return false;
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
