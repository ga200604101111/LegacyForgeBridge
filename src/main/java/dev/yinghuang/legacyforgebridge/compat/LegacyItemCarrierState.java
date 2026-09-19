package dev.yinghuang.legacyforgebridge.compat;

import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.NumberTag;
import com.viaversion.nbt.tag.Tag;

/** Transport bookkeeping, independent of native Minecraft registries and bootstrap. */
public final class LegacyItemCarrierState {
    public static final String KEY="LFB|legacy_item";
    private static final String SAVED_MARKER="source_marker",SAVED_DAMAGE="source_damage";
    private LegacyItemCarrierState() { }

    public static CompoundTag capture(CompoundTag source,String modernId,int legacyId,int metadata) {
        if(modernId==null||modernId.isBlank()||legacyId<0||legacyId>32767||metadata<0||metadata>65535)
            throw new IllegalArgumentException("Invalid legacy item carrier identity");
        CompoundTag marker=new CompoundTag();
        marker.putInt("bridge_schema",1); marker.putString("modern_id",modernId);
        marker.putInt("legacy_id",legacyId);marker.putInt("legacy_data",metadata);
        if(source!=null){
            Tag old=source.get(KEY); if(old!=null)marker.put(SAVED_MARKER,old.copy());
            Tag damage=source.get("Damage");if(damage!=null)marker.put(SAVED_DAMAGE,damage.copy());
        }
        return marker;
    }
    public static boolean matches(CompoundTag marker,String modernId,int legacyId) {
        return marker!=null && number(marker,"bridge_schema",-1)==1
                && modernId.equals(marker.getString("modern_id"))
                && legacyId==number(marker,"legacy_id",-1)
                && number(marker,"legacy_data",-1)>=0 && number(marker,"legacy_data",-1)<=65535;
    }
    public static int metadata(CompoundTag marker){return number(marker,"legacy_data",0);}
    public static int legacyId(CompoundTag marker){return number(marker,"legacy_id",-1);}
    public static String modernId(CompoundTag marker){return marker==null?null:marker.getString("modern_id");}

    /** Removes only bookkeeping owned by this bridge and restores any colliding source NBT. */
    public static void restoreSourceTag(CompoundTag root,CompoundTag marker) {
        root.remove(KEY);
        Tag original=marker.get(SAVED_MARKER);if(original!=null)root.put(KEY,original.copy());
        // Component downgrades can synthesize a root Damage key. Restore the original exact tag,
        // including its numeric type, instead of erasing source-provided data unconditionally.
        root.remove("Damage");Tag damage=marker.get(SAVED_DAMAGE);if(damage!=null)root.put("Damage",damage.copy());
    }
    private static int number(CompoundTag tag,String name,int fallback){NumberTag n=tag==null?null:tag.getNumberTag(name);return n==null?fallback:n.asInt();}
}
