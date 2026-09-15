package dev.longyu.legacyforgebridge.compat;

import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.NumberTag;
import com.viaversion.viaversion.api.minecraft.data.StructuredDataContainer;
import com.viaversion.viaversion.api.minecraft.data.StructuredDataKey;
import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.api.protocol.Protocol;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;

/**
 * Via item-carrier half of the Forge 1.7.10 modded item identity bridge.
 *
 * <p>The session mapping itself lives in {@link LegacyModItemRegistryMap} so the FML handshake
 * state machine stays independent of ViaVersion/NBT runtime classes. This class is loaded only by
 * the Via boundary mixins.</p>
 */
public final class LegacyModItemIdentityBridge {
    static final String MARKER_KEY = "LFB|legacy_item";
    private static final String MODERN_ID_KEY = "modern_id";
    private static final String LEGACY_ID_KEY = "legacy_id";
    private static final String LEGACY_DATA_KEY = "legacy_data";
    private static final int LEGACY_PAPER_ID = 339;

    private LegacyModItemIdentityBridge() {
    }

    /** Runs before ViaVersion's lossy 1.12.2 -> 1.13 clientbound item rewrite. */
    public static boolean prepareLegacyItemForVia(Item item) {
        if (item == null) {
            return false;
        }

        int legacyId = item.identifier();
        Identifier modernId = LegacyModItemRegistryMap.legacyIdentity(legacyId);
        if (modernId == null || !BuiltInRegistries.ITEM.containsKey(modernId)) {
            return false;
        }

        CompoundTag tag = item.tag();
        if (tag == null) {
            tag = new CompoundTag();
            item.setTag(tag);
        }
        tag.put(MARKER_KEY, marker(modernId, legacyId, item.data()));

        item.setIdentifier(LEGACY_PAPER_ID);
        item.setData((short) 0);
        return true;
    }

    /** Runs after the final modern clientbound StructuredItemRewriter. */
    public static boolean restoreModernItemFromVia(Item item) {
        if (item == null) {
            return false;
        }

        StructuredDataContainer data = item.dataContainer();
        CompoundTag customData = data.get(StructuredDataKey.CUSTOM_DATA);
        CompoundTag marker = customData == null ? null : customData.getCompoundTag(MARKER_KEY);
        if (marker == null) {
            return false;
        }

        String modernIdText = marker.getString(MODERN_ID_KEY);
        if (modernIdText == null || modernIdText.isBlank()) {
            return false;
        }

        Identifier modernId;
        try {
            modernId = Identifier.parse(modernIdText);
        } catch (RuntimeException invalidIdentifier) {
            return false;
        }
        if (!BuiltInRegistries.ITEM.containsKey(modernId)) {
            return false;
        }

        net.minecraft.world.item.Item modernItem = BuiltInRegistries.ITEM.getValue(modernId);
        item.setIdentifier(BuiltInRegistries.ITEM.getId(modernItem));

        int legacyData = number(marker, LEGACY_DATA_KEY, 0);
        if (legacyData > 0) {
            data.set(StructuredDataKey.DAMAGE, legacyData);
        } else {
            data.remove(StructuredDataKey.DAMAGE);
        }

        customData.remove(MARKER_KEY);
        if (customData.isEmpty()) {
            data.remove(StructuredDataKey.CUSTOM_DATA);
        }
        return true;
    }

    /** Runs before the final modern serverbound StructuredItemRewriter maps the modern raw ID. */
    public static boolean prepareModernItemForVia(Item item, Protocol<?, ?, ?, ?> protocol) {
        if (item == null || protocol == null) {
            return false;
        }

        net.minecraft.world.item.Item modernItem = BuiltInRegistries.ITEM.byId(item.identifier());
        Identifier modernId = BuiltInRegistries.ITEM.getKey(modernItem);
        Integer legacyId = LegacyModItemRegistryMap.legacyNumericId(modernId);
        if (legacyId == null) {
            return false;
        }

        StructuredDataContainer data = item.dataContainer();
        // At this hook the item still uses the mapped/client serializer namespace.
        data.setIdLookup(protocol, true);
        Integer damage = data.get(StructuredDataKey.DAMAGE);
        int legacyData = damage == null ? 0 : damage;

        CompoundTag customData = data.get(StructuredDataKey.CUSTOM_DATA);
        if (customData == null) {
            customData = new CompoundTag();
            data.set(StructuredDataKey.CUSTOM_DATA, customData);
        }
        customData.put(MARKER_KEY, marker(modernId, legacyId, legacyData));

        // Damage belongs to the legacy stack's data value, not to the temporary paper carrier.
        data.remove(StructuredDataKey.DAMAGE);
        item.setIdentifier(BuiltInRegistries.ITEM.getId(Items.PAPER));
        return true;
    }

    /**
     * Runs after ViaVersion's 1.13 -> 1.12.2 serverbound rewrite so the server receives its
     * original Forge numeric ID and metadata rather than the paper carrier.
     */
    public static boolean restoreLegacyItemFromVia(Item item) {
        if (item == null) {
            return false;
        }

        CompoundTag tag = item.tag();
        CompoundTag marker = tag == null ? null : tag.getCompoundTag(MARKER_KEY);
        if (marker == null) {
            return false;
        }

        int legacyId = number(marker, LEGACY_ID_KEY, -1);
        if (legacyId < 0) {
            return false;
        }

        int legacyData = number(marker, LEGACY_DATA_KEY, 0);
        item.setIdentifier(legacyId);
        item.setData((short) legacyData);

        tag.remove(MARKER_KEY);
        // 1.20.5+ component downgrading may materialize captured damage as root NBT.
        // The 1.7.10 ItemStack already carries that value in its metadata field.
        tag.remove("Damage");
        if (tag.isEmpty()) {
            item.setTag(null);
        }
        return true;
    }

    private static CompoundTag marker(Identifier modernId, int legacyId, int legacyData) {
        CompoundTag marker = new CompoundTag();
        marker.putString(MODERN_ID_KEY, modernId.toString());
        marker.putInt(LEGACY_ID_KEY, legacyId);
        marker.putInt(LEGACY_DATA_KEY, legacyData);
        return marker;
    }

    private static int number(CompoundTag tag, String key, int fallback) {
        NumberTag number = tag.getNumberTag(key);
        return number == null ? fallback : number.asInt();
    }
}
