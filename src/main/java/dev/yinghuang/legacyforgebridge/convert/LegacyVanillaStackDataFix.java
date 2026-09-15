package dev.yinghuang.legacyforgebridge.convert;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;

/** Uses Minecraft's own DataFixerUpper chain for vanilla 1.7.10 ItemStack migration. */
public final class LegacyVanillaStackDataFix {
    private LegacyVanillaStackDataFix() { }

    public record ModernStack(String id, JsonObject components) {
        public ModernStack {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id");
            components = components == null ? new JsonObject() : components.deepCopy();
        }

        public boolean hasComponents() {
            return !components.isEmpty();
        }
    }

    /**
     * Migrates one vanilla 1.7 registry identity plus raw ItemStack data value to current data.
     * The input identity may be the historical 1.7 path (for example {@code wool}); DFU owns all
     * renames/flattening. A wildcard metadata value is not a concrete stack and is rejected.
     */
    public static ModernStack upgrade(String legacyRegistryName, int meta) {
        if (legacyRegistryName == null || legacyRegistryName.isBlank()) {
            throw new IllegalArgumentException("Missing legacy vanilla registry name");
        }
        if (meta < 0 || meta >= LegacyRecipeStackResolver.WILDCARD_META) {
            throw new IllegalArgumentException("DFU requires concrete legacy metadata, got " + meta);
        }

        String legacyId = legacyRegistryName.indexOf(':') >= 0
                ? legacyRegistryName
                : "minecraft:" + legacyRegistryName;
        CompoundTag legacy = new CompoundTag();
        legacy.putString("id", legacyId);
        legacy.putByte("Count", (byte) 1);
        legacy.putShort("Damage", (short) meta);

        Dynamic<?> fixed = DataFixers.getDataFixer().update(
                References.ITEM_STACK,
                new Dynamic<>(NbtOps.INSTANCE, legacy),
                -1,
                SharedConstants.WORLD_VERSION
        );
        JsonElement converted = fixed.convert(JsonOps.INSTANCE).getValue();
        if (!converted.isJsonObject()) {
            throw new IllegalArgumentException("DFU returned non-object ItemStack for " + legacyId + ":" + meta);
        }
        JsonObject object = converted.getAsJsonObject();
        JsonElement id = object.get("id");
        if (id == null || !id.isJsonPrimitive() || id.getAsString().isBlank()) {
            throw new IllegalArgumentException("DFU returned ItemStack without an id for " + legacyId + ":" + meta);
        }
        JsonObject components = object.has("components") && object.get("components").isJsonObject()
                ? object.getAsJsonObject("components").deepCopy()
                : new JsonObject();
        return new ModernStack(id.getAsString(), components);
    }
}
