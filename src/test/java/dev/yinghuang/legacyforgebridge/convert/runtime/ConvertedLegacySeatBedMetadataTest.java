package dev.yinghuang.legacyforgebridge.convert.runtime;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConvertedLegacySeatBedMetadataTest {
    @Test void legacyBedDirectionsRoundTripExactly() {
        assertEquals(0, ConvertedLegacySeatBedBlock.legacyDirectionForFacing(Direction.SOUTH));
        assertEquals(1, ConvertedLegacySeatBedBlock.legacyDirectionForFacing(Direction.WEST));
        assertEquals(2, ConvertedLegacySeatBedBlock.legacyDirectionForFacing(Direction.NORTH));
        assertEquals(3, ConvertedLegacySeatBedBlock.legacyDirectionForFacing(Direction.EAST));
        for (int meta = 0; meta < 4; meta++) {
            Direction facing = ConvertedLegacySeatBedBlock.facingForLegacyDirection(meta);
            assertEquals(meta, ConvertedLegacySeatBedBlock.legacyDirectionForFacing(facing));
            assertEquals(facing, ConvertedLegacySeatBedBlock.facingForLegacyMeta(meta + 8));
        }
    }

    @Test void headBitUsesTheLegacyEightMask() {
        for (int meta = 0; meta < 4; meta++) assertFalse(ConvertedLegacySeatBedBlock.isHeadMeta(meta));
        for (int meta = 8; meta < 12; meta++) assertTrue(ConvertedLegacySeatBedBlock.isHeadMeta(meta));
    }
}
