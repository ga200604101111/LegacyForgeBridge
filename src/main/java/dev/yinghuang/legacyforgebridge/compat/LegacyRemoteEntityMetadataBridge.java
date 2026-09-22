package dev.yinghuang.legacyforgebridge.compat;

import com.viaversion.viaversion.api.minecraft.entitydata.EntityData;
import com.viaversion.viaversion.api.minecraft.item.Item;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyRemoteProjectile;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyVisualEntity;
import dev.yinghuang.legacyforgebridge.network.FmlRuntimeCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mirrors raw 1.7.10 DataWatcher updates into LFB-owned FML entity carriers before ViaLegacy
 * removes unknown custom watcher indices.
 */
public final class LegacyRemoteEntityMetadataBridge {
    private record Update(int type,int index,Object value){}

    private static final Set<Integer> FML_ENTITY_IDS=ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<Integer> PENDING_REWRITE_ENTITY=new ThreadLocal<>();

    private LegacyRemoteEntityMetadataBridge(){}

    public static void registerEntityId(int entityId){if(entityId>=0)FML_ENTITY_IDS.add(entityId);}
    public static void unregisterEntityId(int entityId){FML_ENTITY_IDS.remove(entityId);}
    public static void clear(){FML_ENTITY_IDS.clear();PENDING_REWRITE_ENTITY.remove();}
    public static boolean isRegistered(int entityId){return FML_ENTITY_IDS.contains(entityId);}

    /**
     * Called at ViaLegacy EntityTracker#updateEntityData HEAD while entries still use 1.7.10
     * watcher indices/types. Values are copied immediately because ViaLegacy mutates the list next.
     */
    public static void capture(int entityId,List<?> rawEntries){
        if(!FML_ENTITY_IDS.contains(entityId)||rawEntries==null)return;
        List<Update> updates=new ArrayList<>();
        for(Object raw:rawEntries){
            if(!(raw instanceof EntityData data))continue;
            int type=data.dataType().typeId();
            Object value=copyLegacyValue(type,data.getValue());
            if(value!=null)updates.add(new Update(type,data.id(),value));
        }
        PENDING_REWRITE_ENTITY.set(entityId);
        if(updates.isEmpty())return;

        Minecraft client=Minecraft.getInstance();
        client.execute(()->apply(client,entityId,updates));
    }

    /**
     * ViaLegacy only understands vanilla metadata indices for the generic tracking type. LFB has
     * already captured every raw custom watcher above, so remove indices above the 1.7 Entity base
     * range (0 flags, 1 air) before ViaLegacy logs/drops them.
     */
    public static void prepareForViaRewrite(List<EntityData> entries){
        Integer entityId=PENDING_REWRITE_ENTITY.get();
        if(entityId==null)return;
        PENDING_REWRITE_ENTITY.remove();
        if(!FML_ENTITY_IDS.contains(entityId)||entries==null)return;
        entries.removeIf(data->data.id()>1);
    }

    private static void apply(Minecraft client,int entityId,List<Update> updates){
        if(client.level==null||!FML_ENTITY_IDS.contains(entityId))return;
        Entity entity=client.level.getEntity(entityId);if(entity==null)return;
        for(Update update:updates){
            try{
                if(entity instanceof ConvertedLegacyVisualEntity visible){
                    visible.applyLegacyWatcher(update.type(),update.index(),update.value());
                }else if(entity instanceof ConvertedLegacyRemoteProjectile projectile){
                    projectile.applyLegacyWatcher(update.type(),update.index(),update.value());
                }else if(entity instanceof LegacyPlainEntityWatcherBridge plain){
                    plain.legacyforgebridge$applyWatcher(update.index(),update.type(),update.value());
                }
            }catch(RuntimeException ignored){
                // A source-proven carrier rejects mismatched watcher ids/types rather than coercing.
            }
        }
    }

    private static Object copyLegacyValue(int type,Object raw){
        if(raw==null)return type==5?FmlRuntimeCodec.LegacyItemStack.EMPTY:null;
        return switch(type){
            case 0->raw instanceof Number number?number.byteValue():null;
            case 1->raw instanceof Number number?number.shortValue():null;
            case 2->raw instanceof Number number?number.intValue():null;
            case 3->raw instanceof Number number?number.floatValue():null;
            case 4->raw instanceof String text?text:null;
            case 5->legacyStack(raw);
            default->null;
        };
    }

    private static FmlRuntimeCodec.LegacyItemStack legacyStack(Object raw){
        if(!(raw instanceof Item item)||Item.isEmpty(item))return FmlRuntimeCodec.LegacyItemStack.EMPTY;
        int count=Math.max(0,Math.min(255,item.amount()));
        int damage=Short.toUnsignedInt(item.data());
        return new FmlRuntimeCodec.LegacyItemStack(item.identifier(),count,damage,new byte[0]);
    }
}
