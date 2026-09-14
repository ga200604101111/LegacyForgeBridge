package dev.longyu.legacyforgebridge.convert;

import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyVanillaStackDataFixTest {
    @Test void minecraftDataFixerFlattensReal1710NumericItemAndMetadataPairs() {
        assertEquals("minecraft:blue_wool", upgrade(35, 11).getStringOr("id", ""));
        assertEquals("minecraft:charcoal", upgrade(263, 1).getStringOr("id", ""));
        assertEquals("minecraft:birch_log", upgrade(17, 2).getStringOr("id", ""));
        assertEquals("minecraft:spruce_planks", upgrade(5, 1).getStringOr("id", ""));
    }

    private static CompoundTag upgrade(int numericId, int damage) {
        CompoundTag legacy = new CompoundTag();
        legacy.putShort("id", (short) numericId);
        legacy.putByte("Count", (byte) 1);
        legacy.putShort("Damage", (short) damage);
        return (CompoundTag) DataFixers.getDataFixer().update(
                References.ITEM_STACK,
                new Dynamic<>(NbtOps.INSTANCE, legacy),
                -1,
                SharedConstants.WORLD_VERSION).getValue();
    }
}
