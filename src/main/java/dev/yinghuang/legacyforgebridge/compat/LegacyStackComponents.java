package dev.yinghuang.legacyforgebridge.compat;

import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import dev.yinghuang.legacyforgebridge.convert.runtime.ConvertedStackPresentation;
import dev.yinghuang.legacyforgebridge.LegacyForgeBridge;

/**
 * LFB-owned modern stack state that must never be exposed as source 1.7.10 NBT.
 *
 * <p>The raw legacy ItemStack data value is overloaded by old Minecraft as durability and subtype
 * metadata. Conversion stages preserve that raw value here first; later item-behavior stages may
 * additionally project it onto a modern semantic component when source evidence proves what it
 * means. Keeping the raw value in an independent component prevents subtype recipes from abusing
 * {@code minecraft:damage} while also keeping bridge bookkeeping out of {@code custom_data}.</p>
 */
public final class LegacyStackComponents {
    public static final Identifier LEGACY_META_ID = Identifier.fromNamespaceAndPath(
            LegacyForgeBridge.MOD_ID, "legacy_meta");

    private static volatile DataComponentType<Integer> legacyMeta;

    private LegacyStackComponents() { }

    /** Register before converted content items are constructed. Safe to call repeatedly. */
    public static synchronized void bootstrap() {
        if (legacyMeta != null) return;
        if (BuiltInRegistries.DATA_COMPONENT_TYPE.containsKey(LEGACY_META_ID)) {
            @SuppressWarnings("unchecked")
            DataComponentType<Integer> existing = (DataComponentType<Integer>) BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(LEGACY_META_ID);
            legacyMeta = existing;
            return;
        }
        legacyMeta = Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                LEGACY_META_ID,
                DataComponentType.<Integer>builder()
                        .persistent(Codec.INT)
                        .networkSynchronized(ByteBufCodecs.VAR_INT)
                        .build());
    }

    public static DataComponentType<Integer> legacyMeta() {
        DataComponentType<Integer> value = legacyMeta;
        if (value == null) {
            bootstrap();
            value = legacyMeta;
        }
        return value;
    }

    public static int get(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        Integer value = stack.get(legacyMeta());
        return value == null ? 0 : value;
    }

    public static void set(ItemStack stack, int value) {
        if (stack == null || stack.isEmpty()) throw new IllegalArgumentException("Cannot attach legacy metadata to an empty stack");
        LegacyStackMetadataPolicy.require(value);
        stack.set(legacyMeta(), value);
        ConvertedStackPresentation.apply(stack, value);
    }
}
