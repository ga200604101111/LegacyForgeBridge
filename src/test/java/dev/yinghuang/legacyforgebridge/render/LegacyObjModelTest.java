package dev.yinghuang.legacyforgebridge.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyObjModelTest {
    @Test
    void repeatUvMatchesLegacyOpenGlTextureWrapForNegativeAndOverflowCoordinates() {
        assertEquals(0.25F, LegacyObjModel.repeatUv(0.25F), 1.0E-6F);
        assertEquals(0.75F, LegacyObjModel.repeatUv(-0.25F), 1.0E-6F);
        assertEquals(0.25F, LegacyObjModel.repeatUv(1.25F), 1.0E-6F);
        assertEquals(0.0F, LegacyObjModel.repeatUv(2.0F), 1.0E-6F);
    }
}
