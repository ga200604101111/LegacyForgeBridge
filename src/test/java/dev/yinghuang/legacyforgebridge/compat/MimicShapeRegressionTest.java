package dev.yinghuang.legacyforgebridge.compat;

import org.junit.jupiter.api.Test;

final class MimicShapeRegressionTest {
    @Test void explicitMaterialFallbackIsStrictlyValidated() {
        MimicShapeRegressionChecks.checkSchema();
    }
    @Test void stairsAndThinPanelsRetainTheirGeometry() {
        MimicShapeRegressionChecks.checkGeometry();
    }
}
