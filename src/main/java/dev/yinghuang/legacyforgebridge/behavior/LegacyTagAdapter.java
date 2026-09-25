package dev.yinghuang.legacyforgebridge.behavior;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/** Preserve exact primitive types; expose only proven compound lists through the source API. */
public final class LegacyTagAdapter {
    private LegacyTagAdapter() { }

    public static LegacyBehaviorApi.Tag read(CompoundTag input) {
        if (input == null) return null;
        var result = new LegacyBehaviorApi.Tag();
        for (var field : input.entrySet()) result.values.put(field.getKey(), readValue(field.getValue()));
        return result;
    }

    private static Object readValue(Tag value) {
        if (value instanceof NumericTag number) return number.box();
        if (value instanceof StringTag string) return string.value();
        if (value instanceof CompoundTag compound) return read(compound);
        if (value instanceof ListTag list) {
            var compounds = compoundList(list);
            if (compounds != null) return compounds;
        }
        return value.copy();
    }

    private static LegacyBehaviorApi.TagList compoundList(ListTag input) {
        if (input.size() > 4096) throw new IllegalArgumentException("Source NBT list budget exceeded");
        var values = new java.util.ArrayList<LegacyBehaviorApi.Tag>();
        for (Tag value : input) {
            if (!(value instanceof CompoundTag compound)) return null;
            values.add(read(compound));
        }
        return new LegacyBehaviorApi.TagList(values);
    }

    public static CompoundTag write(LegacyBehaviorApi.Tag input) {
        CompoundTag result = new CompoundTag();
        if (input == null) return result;
        for (var field : input.values.entrySet()) result.put(field.getKey(), writeValue(field.getKey(),field.getValue()));
        return result;
    }

    private static Tag writeValue(String key,Object value) {
        if (value instanceof String text) return StringTag.valueOf(text);
        if (value instanceof Byte number) return net.minecraft.nbt.ByteTag.valueOf(number);
        if (value instanceof Short number) return net.minecraft.nbt.ShortTag.valueOf(number);
        if (value instanceof Integer number) return net.minecraft.nbt.IntTag.valueOf(number);
        if (value instanceof Long number) return net.minecraft.nbt.LongTag.valueOf(number);
        if (value instanceof Float number) return net.minecraft.nbt.FloatTag.valueOf(number);
        if (value instanceof Double number) return net.minecraft.nbt.DoubleTag.valueOf(number);
        if (value instanceof LegacyBehaviorApi.Tag compound) return write(compound);
        if (value instanceof LegacyBehaviorApi.TagList list) {
            ListTag result=new ListTag();
            if(list.values.size()>4096)throw new IllegalArgumentException("Source NBT list budget exceeded");
            for(LegacyBehaviorApi.Tag compound:list.values)result.add(write(compound));
            return result;
        }
        if (value instanceof Tag tag) return tag.copy();
        throw new IllegalArgumentException("Unsupported source NBT value for " + key);
    }
}
