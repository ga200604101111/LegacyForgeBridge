package dev.yinghuang.legacyforgebridge.render;

import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.ListTag;
import com.viaversion.viaversion.api.minecraft.BlockPosition;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import dev.yinghuang.legacyforgebridge.compat.LegacyGridPotBlockRegistry;
import dev.yinghuang.legacyforgebridge.compat.LegacyModItemRegistryMap;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedLegacyGridPotBlockEntity;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedStackPresentation;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * Client-only bridge for the source-proven legacy GridPot action-5 S35 snapshot.
 *
 * <p>The legacy TileEntity writes exactly nine compounds under {@code slotsNBT}; each compound has
 * a {@code matrix} boolean and may have an {@code itemNBT} ItemStack compound. This bridge consumes
 * only that bounded shape and maps numeric item identity through the already-installed FML
 * ModIdData registry map. Unknown item identities fail closed to an empty visual slot.</p>
 */
public final class LegacyGridPotNetworkBridge {
    private static final int CELLS=9;
    private static final int MAX_PENDING=1024;
    private static final int PENDING_TICKS=200;

    public record Snapshot(int enabledMask,List<ItemStack> items){
        public Snapshot{
            items=List.copyOf(items);
            if((enabledMask&~0x1FF)!=0||items.size()!=CELLS)throw new IllegalArgumentException("Invalid GridPot snapshot");
        }
    }
    private record Pending(Snapshot snapshot,int ttl){}

    private static final Map<BlockPos,Pending> PENDING=new LinkedHashMap<>();
    private static boolean initialized;
    private static ClientLevel activeLevel;

    private LegacyGridPotNetworkBridge(){}

    public static synchronized void initialize(){
        if(initialized)return;initialized=true;
        ClientTickEvents.END_CLIENT_TICK.register(client->tick(client.level));
    }

    public static void capture(BlockPosition position,CompoundTag tag){
        if(position==null||tag==null)return;
        Snapshot snapshot=parse(tag);if(snapshot==null)return;
        Minecraft client=Minecraft.getInstance();
        client.execute(()->applyOrQueue(client.level,new BlockPos(position.x(),position.y(),position.z()),snapshot));
    }

    static Snapshot parse(CompoundTag tag){
        try{
            if(!tag.contains("slotsNBT"))return null;
            ListTag<CompoundTag> list=tag.getListTag("slotsNBT",CompoundTag.class);
            if(list==null||list.size()!=CELLS)return null;
            int mask=0;List<ItemStack> items=new ArrayList<>(CELLS);
            for(int slot=0;slot<CELLS;slot++){
                CompoundTag cell=list.get(slot);if(cell==null)return null;
                boolean enabled=cell.contains("matrix")&&cell.getBoolean("matrix");
                if(enabled)mask|=1<<slot;
                ItemStack stack=enabled&&cell.contains("itemNBT")?stack(cell.getCompoundTag("itemNBT")):ItemStack.EMPTY;
                items.add(stack);
            }
            return new Snapshot(mask,items);
        }catch(RuntimeException invalid){
            LegacyForgeBridge.LOGGER.warn("Rejected malformed legacy GridPot S35 snapshot: {}",invalid.toString());
            return null;
        }
    }

    private static ItemStack stack(CompoundTag tag){
        if(tag==null||!tag.contains("id"))return ItemStack.EMPTY;
        int legacyId=Short.toUnsignedInt(tag.getShort("id"));
        Identifier modern=LegacyModItemRegistryMap.legacyAnyIdentity(legacyId);
        if(modern==null||!BuiltInRegistries.ITEM.containsKey(modern))return ItemStack.EMPTY;
        Item item=BuiltInRegistries.ITEM.getValue(modern);if(item==null)return ItemStack.EMPTY;
        int count=tag.contains("Count")?Byte.toUnsignedInt(tag.getByte("Count")):1;
        if(count<=0)return ItemStack.EMPTY;
        int metadata=tag.contains("Damage")?Short.toUnsignedInt(tag.getShort("Damage")):0;
        ItemStack stack=ConvertedStackPresentation.create(item,metadata);
        if(stack.isEmpty())return ItemStack.EMPTY;
        stack.setCount(Math.min(count,stack.getMaxStackSize()));
        ConvertedStackPresentation.apply(stack,metadata);
        return stack;
    }

    private static void applyOrQueue(ClientLevel level,BlockPos pos,Snapshot snapshot){
        bind(level);if(level==null)return;
        if(apply(level,pos,snapshot))return;
        if(PENDING.size()>=MAX_PENDING){
            Iterator<BlockPos> iterator=PENDING.keySet().iterator();if(iterator.hasNext()){iterator.next();iterator.remove();}
        }
        PENDING.put(pos.immutable(),new Pending(snapshot,PENDING_TICKS));
    }

    private static boolean apply(ClientLevel level,BlockPos pos,Snapshot snapshot){
        var state=level.getBlockState(pos);
        var rule=LegacyGridPotBlockRegistry.rule(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        if(rule==null)return false;
        var blockEntity=level.getBlockEntity(pos);
        if(!(blockEntity instanceof ConvertedLegacyGridPotBlockEntity grid))return false;
        try{grid.applyLegacySnapshot(snapshot.enabledMask(),snapshot.items());}
        catch(RuntimeException invalid){
            LegacyForgeBridge.LOGGER.warn("Rejected GridPot snapshot at {} for {}: {}",pos,rule.id(),invalid.toString());
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
