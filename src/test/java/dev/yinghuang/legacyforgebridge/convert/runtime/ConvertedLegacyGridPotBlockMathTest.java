package dev.yinghuang.legacyforgebridge.convert.runtime;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConvertedLegacyGridPotBlockMathTest {
    @Test
    void hitGridMatchesLegacyThreeByThreeSlotFormulaAndSideBias() {
        assertEquals(0, ConvertedLegacyGridPotBlock.slotForHit(0.01F, 0.01F, Direction.UP));
        assertEquals(4, ConvertedLegacyGridPotBlock.slotForHit(0.50F, 0.50F, Direction.UP));
        assertEquals(8, ConvertedLegacyGridPotBlock.slotForHit(0.99F, 0.99F, Direction.UP));

        // +X bias crosses from the centre column into the east column; the inverse direction does not.
        assertEquals(5, ConvertedLegacyGridPotBlock.slotForHit(0.50F, 0.50F, Direction.EAST));
        assertEquals(3, ConvertedLegacyGridPotBlock.slotForHit(0.50F, 0.50F, Direction.WEST));
        assertEquals(7, ConvertedLegacyGridPotBlock.slotForHit(0.50F, 0.50F, Direction.SOUTH));
        assertEquals(1, ConvertedLegacyGridPotBlock.slotForHit(0.50F, 0.50F, Direction.NORTH));
    }
}
