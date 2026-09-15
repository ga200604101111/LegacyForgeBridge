package dev.yinghuang.legacyforgebridge.compat;

import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.NumberTag;
import com.viaversion.nbt.tag.StringTag;
import com.viaversion.viaversion.api.minecraft.data.StructuredDataContainer;
import com.viaversion.viaversion.api.minecraft.data.StructuredDataKey;
import com.viaversion.viaversion.api.minecraft.item.data.BlocksAttacks;
import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.api.protocol.Protocol;
import net.minecraft.core.registries.BuiltInRegistries;
import dev.yinghuang.legacyforgebridge.behavior.LegacyBehaviorApi;
import dev.yinghuang.legacyforgebridge.behavior.LegacyBehaviorRegistry;
import dev.yinghuang.legacyforgebridge.behavior.LegacyBehaviorRuntime;

/** Restores the native use component skipped while a mod item travels as a paper carrier. */
public final class LegacyViaUseComponents {
    private LegacyViaUseComponents() { }
    public static void restore(Item item,Protocol<?,?,?,?> protocol){
        if(item==null)return;
        var data=item.dataContainer();data.setIdLookup(protocol,true);
        String id=BuiltInRegistries.ITEM.getKey(BuiltInRegistries.ITEM.byId(item.identifier())).toString();
        restore(id,data,data.get(StructuredDataKey.CUSTOM_DATA));
    }
    public static void restore(String itemId,StructuredDataContainer data,CompoundTag customData){
        var definition=LegacyBehaviorRegistry.item(itemId);
        if(definition==null||!definition.hooks().contains("action"))return;
        LegacyBehaviorApi.Tag tag=null;
        if(customData!=null&&!customData.isEmpty()){
            tag=new LegacyBehaviorApi.Tag();
            for(var entry:customData.entrySet()) {
                if(entry.getValue() instanceof StringTag string)tag.values.put(entry.getKey(),string.getValue());
                else if(entry.getValue() instanceof NumberTag number)tag.values.put(entry.getKey(),number.asDouble());
            }
        }
        var action=LegacyBehaviorRuntime.action(itemId,tag);
        if(action==LegacyBehaviorApi.UseAction.block){
            data.remove(StructuredDataKey.CONSUMABLE1_21_2);
            data.set(StructuredDataKey.BLOCKS_ATTACKS1_21_5,new BlocksAttacks(0F,0F,
                    new BlocksAttacks.DamageReduction[]{new BlocksAttacks.DamageReduction(90F,null,-.5F,.5F)},
                    new BlocksAttacks.ItemDamageFunction(0F,0F,0F),null,null,null));
        }else data.remove(StructuredDataKey.BLOCKS_ATTACKS1_21_5);
    }
}
