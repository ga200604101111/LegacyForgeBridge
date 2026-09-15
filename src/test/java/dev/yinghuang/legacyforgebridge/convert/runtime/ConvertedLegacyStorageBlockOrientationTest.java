package dev.longyu.legacyforgebridge.convert.runtime;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConvertedLegacyStorageBlockOrientationTest {
    @Test
    void legacyYawQuadrantsMapToSourceMetadataExactly() {
        assertEquals(0, ConvertedLegacyStorageBlock.legacyMetaForPlayerFacing(Direction.SOUTH));
        assertEquals(1, ConvertedLegacyStorageBlock.legacyMetaForPlayerFacing(Direction.WEST));
        assertEquals(2, ConvertedLegacyStorageBlock.legacyMetaForPlayerFacing(Direction.NORTH));
        assertEquals(3, ConvertedLegacyStorageBlock.legacyMetaForPlayerFacing(Direction.EAST));
        assertThrows(IllegalArgumentException.class,
                () -> ConvertedLegacyStorageBlock.legacyMetaForPlayerFacing(Direction.UP));
    }
}
