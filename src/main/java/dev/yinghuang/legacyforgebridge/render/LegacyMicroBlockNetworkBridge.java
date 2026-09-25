package dev.yinghuang.legacyforgebridge.render;

import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.ListTag;
import com.viaversion.viaversion.api.minecraft.BlockPosition;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.compat.LegacyMicroBlockRegistry;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyMicroBlockBlockEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Client-only handoff for legacy action-5 S35 micro-block snapshots before Via removes them. */
public final class LegacyMicroBlockNetworkBridge {
    private static final int MAX_PENDING=4096;
    private static final int PENDING_TICKS=200;
    private static final Map<BlockPos,Pending> PENDING=new LinkedHashMap<>();
    private static boolean initialized;
    private static ClientLevel activeLevel;

    private record Pending(LegacyMicroBlockRegistry.Snapshot snapshot,int ttl){}

    private LegacyMicroBlockNetworkBridge(){}

    public static synchronized void initialize(){
        if(initialized)return;initialized=true;
        ClientTickEvents.END_CLIENT_TICK.register(client->tick(client.level));
    }

    public static void capture(BlockPosition position,CompoundTag tag){
        if(position==null||tag==null)return;
        LegacyMicroBlockRegistry.Snapshot snapshot=parse(tag);
        if(snapshot==null)return;
        Minecraft client=Minecraft.getInstance();
        client.execute(()->applyOrQueue(client.level,new BlockPos(position.x(),position.y(),position.z()),snapshot));
    }

    static LegacyMicroBlockRegistry.Snapshot parse(CompoundTag tag){
        try{
            if(!tag.contains("this.slotLength")||!tag.contains("slotsNBT"))return null;
            int size=Byte.toUnsignedInt(tag.getByte("this.slotLength"));
            if(size<1||size>16)return null;
            ListTag<CompoundTag> list=tag.getListTag("slotsNBT",CompoundTag.class);
            int expected=size*size*size;
            if(list==null||list.size()!=expected)return null;
            List<LegacyMicroBlockRegistry.LegacyCell> cells=new ArrayList<>(expected);
            for(CompoundTag cell:list){
                if(cell==null||!cell.contains("id")){cells.add(LegacyMicroBlockRegistry.LegacyCell.EMPTY);continue;}
                int id=Short.toUnsignedInt(cell.getShort("id"));
                int damage=cell.contains("Damage")?Short.toUnsignedInt(cell.getShort("Damage")):0;
                cells.add(new LegacyMicroBlockRegistry.LegacyCell(id,damage));
            }
            return new LegacyMicroBlockRegistry.Snapshot(size,cells);
        }catch(RuntimeException invalid){
            LegacyForgeBridge.LOGGER.warn("Rejected malformed legacy micro-block S35 snapshot: {}",invalid.toString());
            return null;
        }
    }

    private static void applyOrQueue(ClientLevel level,BlockPos pos,LegacyMicroBlockRegistry.Snapshot snapshot){
        bind(level);if(level==null)return;
        if(apply(level,pos,snapshot))return;
        if(PENDING.size()>=MAX_PENDING){
            Iterator<BlockPos> iterator=PENDING.keySet().iterator();if(iterator.hasNext()){iterator.next();iterator.remove();}
        }
        PENDING.put(pos.immutable(),new Pending(snapshot,PENDING_TICKS));
    }

    private static boolean apply(ClientLevel level,BlockPos pos,LegacyMicroBlockRegistry.Snapshot snapshot){
        var state=level.getBlockState(pos);
        var rule=LegacyMicroBlockRegistry.rule(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        if(rule==null)return false;
        var blockEntity=level.getBlockEntity(pos);
        if(!(blockEntity instanceof ConvertedLegacyMicroBlockBlockEntity micro))return false;
        try{micro.applySnapshot(snapshot);}
        catch(RuntimeException invalid){
            LegacyForgeBridge.LOGGER.warn("Rejected micro-block snapshot at {} for {}: {}",pos,rule.id(),invalid.toString());
            return true;
        }
        Minecraft client=Minecraft.getInstance();
        if(client.levelRenderer!=null)client.levelRenderer.setSectionDirty(pos.getX()>>4,pos.getY()>>4,pos.getZ()>>4);
        return true;
    }

    private static void tick(ClientLevel level){
        bind(level);if(level==null||PENDING.isEmpty())return;
        Iterator<Map.Entry<BlockPos,Pending>> iterator=PENDING.entrySet().iterator();
        while(iterator.hasNext()){
            var entry=iterator.next();Pending pending=entry.getValue();
            if(apply(level,entry.getKey(),pending.snapshot())||pending.ttl()<=1)iterator.remove();
            else entry.setValue(new Pending(pending.snapshot(),pending.ttl()-1));
        }
    }

    private static void bind(ClientLevel level){
        if(activeLevel!=level){activeLevel=level;PENDING.clear();}
    }

    static synchronized void clearForTests(){PENDING.clear();activeLevel=null;}
}
