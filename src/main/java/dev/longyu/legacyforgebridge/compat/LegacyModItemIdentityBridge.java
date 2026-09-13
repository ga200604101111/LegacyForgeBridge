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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Session-local identity bridge for Forge 1.7.10 modded item IDs.
 *
 * <p>Forge sends semantic registry names during FML ModIdData, but the old wire item stack still
 * carries a numeric ID. ViaVersion's 1.12.2 -> 1.13 boundary cannot map arbitrary Forge IDs and
 * intentionally falls back to stone. LFB preserves the semantic identity in NBT/custom_data while
 * temporarily presenting a vanilla paper carrier to Via. At the modern edge the carrier is
 * restored to the converted Fabric item; serverbound traffic performs the inverse operation.</p>
 */
public final class LegacyModItemIdentityBridge {
    static final char ITEM_REGISTRY_PREFIX = '\u0002';
    static final String MARKER_KEY = "LFB|legacy_item";
    private static final String MODERN_ID_KEY = "modern_id";
    private static final String LEGACY_ID_KEY = "legacy_id";
    private static final String LEGACY_DATA_KEY = "legacy_data";
    private static final int LEGACY_PAPER_ID = 339;

    private static volatile Map<Integer, Identifier> legacyToModern = Map.of();
    private static volatile Map<Identifier, Integer> modernToLegacy = Map.of();

    private LegacyModItemIdentityBridge() {
    }

    /** Installs only non-vanilla ITEM identities from one FML registry synchronization. */
    public static synchronized int install(Map<String, Integer> registryIds) {
        Map<Integer, Identifier> byLegacyId = new LinkedHashMap<>();
        Map<Identifier, Integer> byModernId = new LinkedHashMap<>();

        if (registryIds != null) {
            for (Map.Entry<String, Integer> entry : registryIds.entrySet()) {
                String rawIdentity = entry.getKey();
                Integer legacyId = entry.getValue();
                if (rawIdentity == null
                        || rawIdentity.length() < 2
                        || rawIdentity.charAt(0) != ITEM_REGISTRY_PREFIX
                        || legacyId == null
                        || legacyId < 0) {
                    continue;
                }

                Identifier modernId;
                try {
                    modernId = Identifier.parse(rawIdentity.substring(1));
                } catch (RuntimeException invalidIdentifier) {
                    continue;
                }

                // Vanilla identities remain ViaVersion-owned. LFB only fills the Forge-mod gap.
                if ("minecraft".equals(modernId.getNamespace())) {
                    continue;
                }

                byLegacyId.put(legacyId, modernId);
                byModernId.put(modernId, legacyId);
            }
        }

        legacyToModern = Collections.unmodifiableMap(new LinkedHashMap<>(byLegacyId));
        modernToLegacy = Collections.unmodifiableMap(new LinkedHashMap<>(byModernId));
        return legacyToModern.size();
    }

    public static synchronized void clear() {
        legacyToModern = Map.of();
        modernToLegacy = Map.of();
    }

    public static int mappedItemCount() {
        return legacyToModern.size();
    }

    static Identifier legacyIdentity(int legacyId) {
        return legacyToModern.get(legacyId);
    }

    static Integer legacyNumericId(Identifier modernId) {
        return modernToLegacy.get(modernId);
    }

    /**
     * Runs before ViaVersion's lossy 1.12.2 -> 1.13 clientbound item rewrite.
     */
    public static boolean prepareLegacyItemForVia(Item item) {
        if (item == null) {
            return false;
        }

        int legacyId = item.identifier();
        Identifier modernId = legacyToModern.get(legacyId);
        if (modernId == null || !BuiltInRegistries.ITEM.containsKey(modernId)) {
            return false;
        }

        CompoundTag tag = item.tag();
        if (tag == null) {
            tag = new CompoundTag();
            item.setTag(tag);
        }

        CompoundTag marker = marker(modernId, legacyId, item.data());
        tag.put(MARKER_KEY, marker);

        item.setIdentifier(LEGACY_PAPER_ID);
        item.setData((short) 0);
        return true;
    }

    /**
     * Runs after the final modern clientbound StructuredItemRewriter.
     */
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

    /**
     * Runs before the final modern serverbound StructuredItemRewriter maps the modern raw ID.
     */
    public static boolean prepareModernItemForVia(Item item, Protocol<?, ?, ?, ?> protocol) {
        if (item == null || protocol == null) {
            return false;
        }

        net.minecraft.world.item.Item modernItem = BuiltInRegistries.ITEM.byId(item.identifier());
        Identifier modernId = BuiltInRegistries.ITEM.getKey(modernItem);
        Integer legacyId = modernToLegacy.get(modernId);
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
        // 1.20.5+ component downgrading may materialize the captured damage as root NBT.
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
