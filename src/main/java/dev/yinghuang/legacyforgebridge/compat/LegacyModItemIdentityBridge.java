package dev.yinghuang.legacyforgebridge.compat;

import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.viaversion.api.minecraft.data.StructuredDataContainer;
import com.viaversion.viaversion.api.minecraft.data.StructuredDataKey;
import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.api.minecraft.item.data.ItemModel;
import com.viaversion.viaversion.api.protocol.Protocol;
import com.viaversion.viaversion.util.Key;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedIconModelCatalog;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedItemNameCatalog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;

/** Keeps Forge identity in a vanilla paper carrier from the FIRST to the LAST Via boundary. */
public final class LegacyModItemIdentityBridge {
    static final String MARKER_KEY=LegacyItemCarrierState.KEY;
    private static final int LEGACY_PAPER_ID=339;
    private static final Set<String> REPORTED=ConcurrentHashMap.newKeySet();
    private LegacyModItemIdentityBridge() { }

    /** Before ViaLegacy 1.7.10 -> 1.8 touches an ID that may collide with a later vanilla item. */
    public static boolean prepareLegacyItemForVia(Item item) {
        if(item==null)return false;
        int id=item.identifier();Identifier modern=LegacyModItemRegistryMap.legacyIdentity(id);
        if(modern==null)return false; // Vanilla/unmapped items remain on the normal Via path.
        if(!BuiltInRegistries.ITEM.containsKey(modern)){
            report("missing:"+modern,"LFB-ITEM-IDENTITY-0001: legacy ID "+id+" maps to absent native item "+modern);
            return false;
        }
        CompoundTag tag=item.tag();if(tag==null){tag=new CompoundTag();item.setTag(tag);}
        tag.put(MARKER_KEY,LegacyItemCarrierState.capture(tag,modern.toString(),id,Short.toUnsignedInt(item.data())));
        item.setIdentifier(LEGACY_PAPER_ID);item.setData((short)0);
        report("edge-ready","LFB item carrier active at the 1.7.10 ViaLegacy edge; metadata is preserved independently of durability.");return true;
    }

    /** Final clientbound boundary only. Retain metadata independently of modern durability. */
    public static boolean restoreModernItemFromVia(Item item) {
        if(item==null)return false;
        StructuredDataContainer data=item.dataContainer();CompoundTag custom=data.get(StructuredDataKey.CUSTOM_DATA);
        CompoundTag marker=custom==null?null:custom.getCompoundTag(MARKER_KEY);
        Identifier modern=validatedIdentity(marker);if(modern==null)return false;
        var nativeItem=BuiltInRegistries.ITEM.getValue(modern);
        item.setIdentifier(BuiltInRegistries.ITEM.getId(nativeItem));
        int metadata=LegacyItemCarrierState.metadata(marker);
        if(nativeItem.getDefaultInstance().isDamageableItem())data.set(StructuredDataKey.DAMAGE,metadata);
        else data.remove(StructuredDataKey.DAMAGE);
        String model=ConvertedIconModelCatalog.itemModel(modern.toString(),metadata);
        if(model!=null)data.set(StructuredDataKey.ITEM_MODEL,new ItemModel(Key.of(model)));
        // ITEM_NAME is the translated default; CUSTOM_NAME belongs to the server/anvil/plugin.
        LegacyItemNameBridge.toClient(data,marker,ConvertedItemNameCatalog.translationKey(modern.toString(),metadata));
        // Do not delete the state here: non-damageable subtypes cannot round-trip through DAMAGE.
        // It is removed at the final server edge, so the Forge server never receives bridge NBT.
        return true;
    }

    public static boolean prepareModernItemForVia(Item item,Protocol<?,?,?,?> protocol) {
        if(item==null||protocol==null)return false;
        var nativeItem=BuiltInRegistries.ITEM.byId(item.identifier());
        Identifier modern=BuiltInRegistries.ITEM.getKey(nativeItem);
        Integer legacy=LegacyModItemRegistryMap.legacyNumericId(modern);if(legacy==null)return false;
        StructuredDataContainer data=item.dataContainer();data.setIdLookup(protocol,true);
        CompoundTag custom=data.get(StructuredDataKey.CUSTOM_DATA);
        if(custom==null){custom=new CompoundTag();data.set(StructuredDataKey.CUSTOM_DATA,custom);}
        CompoundTag marker=custom.getCompoundTag(MARKER_KEY);
        if(!LegacyItemCarrierState.matches(marker,modern.toString(),legacy)){
            Integer damage=data.get(StructuredDataKey.DAMAGE);
            int metadata=damage==null?0:damage;
            if(metadata<0||metadata>65535)return false;
            marker=LegacyItemCarrierState.capture(custom,modern.toString(),legacy,metadata);
            custom.put(MARKER_KEY,marker);
        }
        LegacyItemNameBridge.toServer(data,marker);
        data.remove(StructuredDataKey.DAMAGE);
        // Only bridge-owned model overrides are stripped. They are not legacy gameplay NBT.
        ItemModel model=data.get(StructuredDataKey.ITEM_MODEL);
        String owned=ConvertedIconModelCatalog.itemModel(modern.toString(),LegacyItemCarrierState.metadata(marker));
        if(model!=null&&owned!=null&&model.key().equals(owned))data.remove(StructuredDataKey.ITEM_MODEL);
        item.setIdentifier(BuiltInRegistries.ITEM.getId(Items.PAPER));return true;
    }

    /** After ViaLegacy's LAST 1.8 -> 1.7.10 non-existent-item fallback, never at 1.13. */
    public static boolean restoreLegacyItemFromVia(Item item) {
        if(item==null)return false;
        CompoundTag tag=item.tag();CompoundTag marker=tag==null?null:tag.getCompoundTag(MARKER_KEY);
        Identifier modern=validatedIdentity(marker);if(modern==null)return false;
        item.setIdentifier(LegacyItemCarrierState.legacyId(marker));
        item.setData((short)LegacyItemCarrierState.metadata(marker));
        LegacyItemCarrierState.restoreSourceTag(tag,marker);
        if(tag.isEmpty())item.setTag(null);return true;
    }
    private static void report(String key,String message){if(REPORTED.size()<256&&REPORTED.add(key))LegacyForgeBridge.LOGGER.info(message);}
    private static Identifier validatedIdentity(CompoundTag marker) {
        String text=LegacyItemCarrierState.modernId(marker);if(text==null||text.isBlank())return null;
        Identifier modern;try{modern=Identifier.parse(text);}catch(RuntimeException bad){return null;}
        Integer id=LegacyModItemRegistryMap.legacyNumericId(modern);
        return id!=null&&LegacyItemCarrierState.matches(marker,text,id)&&BuiltInRegistries.ITEM.containsKey(modern)?modern:null;
    }
}
