package dev.yinghuang.legacyforgebridge.protocol;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.entities.EntityTypes1_8;
import dev.yinghuang.legacyforgebridge.compat.LegacyRemoteEntityMetadataBridge;
import dev.yinghuang.legacyforgebridge.network.FmlConnectionTrace;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Method;

/**
 * Registers proof-gated FML-spawned carriers in ViaLegacy's 1.7.10 -> 1.8 entity tracker.
 *
 * <p>FML custom entity spawn travels on a plugin channel and therefore bypasses ViaLegacy's normal
 * vanilla spawn handlers. Without this bridge ViaLegacy later cancels every 1.7.10
 * SET_ENTITY_DATA packet for that entity id as untracked. The tracker class itself remains an
 * optional runtime dependency and is reached reflectively; ViaVersion's public EntityTypes1_8 API
 * supplies the generic platform ENTITY type used only for packet lifecycle bookkeeping.</p>
 */
public final class LegacyViaFmlEntityTrackerBridge {
    private static final String TRACKER_CLASS =
            "net.raphimc.vialegacy.protocol.release.r1_7_6_10tor1_8.storage.EntityTracker";

    private LegacyViaFmlEntityTrackerBridge(){}

    @SuppressWarnings({"rawtypes","unchecked"})
    public static boolean trackFmlEntity(Minecraft client,int entityId,FmlConnectionTrace trace){
        if(client==null||entityId<0||!ViaFabricPlusBackend.INSTANCE.isMinecraft1710Target())return false;
        try{
            var listener=client.getConnection();
            if(listener==null){trace.event("ViaLegacy FML entity tracker unavailable: client packet listener is null; entity="+entityId);return false;}
            UserConnection user=ViaFabricPlusBackend.INSTANCE.userConnection(listener.getConnection());
            if(user==null){trace.event("ViaLegacy FML entity tracker unavailable: UserConnection is null; entity="+entityId);return false;}
            Class trackerClass=Class.forName(TRACKER_CLASS);
            Object tracker=user.get(trackerClass);
            if(tracker==null){trace.event("ViaLegacy FML entity tracker unavailable: tracker storage is absent; entity="+entityId);return false;}
            Method track=trackerClass.getMethod("trackEntity",int.class,EntityTypes1_8.EntityType.class);
            track.invoke(tracker,entityId,EntityTypes1_8.EntityType.ENTITY);
            LegacyRemoteEntityMetadataBridge.registerEntityId(entityId);
            trace.event("Registered proof-gated FML carrier in ViaLegacy entity tracker; entity="+entityId+" type=ENTITY");
            return true;
        }catch(ReflectiveOperationException|LinkageError|RuntimeException exception){
            trace.event("ViaLegacy FML entity tracker registration failed; entity="+entityId+" reason="+exception.getClass().getSimpleName()+": "+exception.getMessage());
            return false;
        }
    }
}
