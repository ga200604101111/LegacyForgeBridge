package dev.yinghuang.legacyforgebridge.convert.runtime;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConvertedLegacyInertModelBlockOrientationTest {
    @Test void sourceYawQuadrantsMapToLegacyMetadata(){
        assertEquals(0,ConvertedLegacyInertModelBlock.legacyMetaForPlayerFacing(Direction.SOUTH));
        assertEquals(1,ConvertedLegacyInertModelBlock.legacyMetaForPlayerFacing(Direction.WEST));
        assertEquals(2,ConvertedLegacyInertModelBlock.legacyMetaForPlayerFacing(Direction.NORTH));
        assertEquals(3,ConvertedLegacyInertModelBlock.legacyMetaForPlayerFacing(Direction.EAST));
    }
}
