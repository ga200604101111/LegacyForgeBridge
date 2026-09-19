package dev.yinghuang.legacyforgebridge.compat;

import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.Tag;
import com.viaversion.viaversion.api.minecraft.data.StructuredDataContainer;
import com.viaversion.viaversion.api.minecraft.data.StructuredDataKey;

/** Replaces carrier ITEM_NAME only; never edits CUSTOM_NAME (server/anvil/plugin names). */
public final class LegacyItemNameBridge {
    private static final String OWNED="client_item_name",PREVIOUS="previous_client_item_name";
    private LegacyItemNameBridge(){ }
    public static void toClient(StructuredDataContainer data,CompoundTag marker,String key) {
        if(data==null||marker==null||key==null||key.isBlank())return;
        Tag current=data.get(StructuredDataKey.ITEM_NAME);
        if(!marker.contains(OWNED)&&current!=null)marker.put(PREVIOUS,current.copy());
        CompoundTag name=new CompoundTag();name.putString("translate",key);
        data.set(StructuredDataKey.ITEM_NAME,name);marker.put(OWNED,name.copy());
    }
    public static void toServer(StructuredDataContainer data,CompoundTag marker) {
        if(data==null||marker==null)return;
        Tag owned=marker.get(OWNED),current=data.get(StructuredDataKey.ITEM_NAME);
        if(owned!=null&&owned.equals(current)) {
            Tag previous=marker.get(PREVIOUS);
            if(previous==null)data.remove(StructuredDataKey.ITEM_NAME);else data.set(StructuredDataKey.ITEM_NAME,previous.copy());
        }
        // Both bookkeeping entries remain in the marker until the terminal edge removes it.
        // They must never become display.Name or overwrite the original Forge NBT.
    }
}
