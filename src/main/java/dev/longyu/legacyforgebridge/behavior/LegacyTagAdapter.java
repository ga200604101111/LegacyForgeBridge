package dev.longyu.legacyforgebridge.behavior;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/** Preserve NBT number types and opaque compound/list/array fields without a JSON round trip. */
public final class LegacyTagAdapter {
    private LegacyTagAdapter() { }

    public static LegacyBehaviorApi.Tag read(CompoundTag input) {
        if (input == null) return null;
        var result = new LegacyBehaviorApi.Tag();
        for (var field : input.entrySet()) {
            Tag value = field.getValue();
            result.values.put(field.getKey(), value instanceof NumericTag number ? number.box()
                    : value instanceof StringTag string ? string.value() : value.copy());
        }
        return result;
    }

    public static CompoundTag write(LegacyBehaviorApi.Tag input) {
        CompoundTag result = new CompoundTag();
        if (input == null) return result;
        for (var field : input.values.entrySet()) {
            String key = field.getKey();
            Object value = field.getValue();
            if (value instanceof String text) result.putString(key, text);
            else if (value instanceof Byte number) result.putByte(key, number);
            else if (value instanceof Short number) result.putShort(key, number);
            else if (value instanceof Integer number) result.putInt(key, number);
            else if (value instanceof Long number) result.putLong(key, number);
            else if (value instanceof Float number) result.putFloat(key, number);
            else if (value instanceof Double number) result.putDouble(key, number);
            else if (value instanceof Tag tag) result.put(key, tag.copy());
            else throw new IllegalArgumentException("Unsupported source NBT value for " + key);
        }
        return result;
    }
}
