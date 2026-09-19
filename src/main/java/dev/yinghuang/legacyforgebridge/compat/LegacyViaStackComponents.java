package dev.yinghuang.legacyforgebridge.compat;

import com.viaversion.viaversion.api.minecraft.data.StructuredData;
import com.viaversion.viaversion.api.minecraft.data.StructuredDataContainer;
import com.viaversion.viaversion.api.minecraft.data.StructuredDataKey;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.api.type.types.version.VersionedTypes;
import net.minecraft.core.registries.BuiltInRegistries;

/** One native component codec at the native edge, never an extra component in older protocols. */
public final class LegacyViaStackComponents {
    public static final StructuredDataKey<Integer> META = new StructuredDataKey<>("legacyforgebridge:legacy_meta", Types.VAR_INT);
    private LegacyViaStackComponents() { }

    public static StructuredDataKey<?> key(Object codec, int id) {
        if (codec != VersionedTypes.V1_21_11.structuredData) return null;
        int nativeId = BuiltInRegistries.DATA_COMPONENT_TYPE.getId(LegacyStackComponents.legacyMeta());
        return id >= 0 && id == nativeId ? META : null;
    }

    public static void toClient(StructuredDataContainer data, int metadata) {
        LegacyStackMetadataPolicy.require(metadata);
        int id = BuiltInRegistries.DATA_COMPONENT_TYPE.getId(LegacyStackComponents.legacyMeta());
        if (id < 0) throw new IllegalStateException("LFB native metadata component is not registered");
        // The vanilla mapping table deliberately has no serializer ID for a mod-owned component.
        // Write its actual native registry ID only AFTER all Via component mapping has completed.
        data.data().put(META, StructuredData.of(META, metadata, id));
    }

}
