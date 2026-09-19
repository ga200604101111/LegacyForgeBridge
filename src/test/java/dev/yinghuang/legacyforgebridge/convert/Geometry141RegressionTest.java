package dev.yinghuang.legacyforgebridge.convert;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
class Geometry141RegressionTest {
 @TempDir Path temp;
 @Test void cubeAndWinding(){Geometry141Checks.cubeAndWinding();}
 @Test void slabDimensions(){Geometry141Checks.slabDimensions();}
 @Test void everyStairOrientation(){Geometry141Checks.everyStairOrientation();}
 @Test void outerAndInnerCorners(){Geometry141Checks.outerAndInnerCorners();}
 @Test void cornerContinuationGuard(){Geometry141Checks.cornerContinuationGuard();}
 @Test void everyPaneConnection(){Geometry141Checks.everyPaneConnection();}
 @Test void curtainsHaveNoHorizontalCaps(){Geometry141Checks.curtainsHaveNoHorizontalCaps();}
 @Test void invalidGeometryRejected(){Geometry141Checks.invalidGeometryRejected();}
 @Test void heldModelsUseBlockTransforms(){Geometry141Checks.heldModelsUseBlockTransforms();}
 @Test void strictSchema(){Geometry141Checks.strictSchema();}
 @Test void mimicDirectionsCyclesAndBudget(){Geometry141Checks.mimicDirectionsCyclesAndBudget();}
 @Test void sourceGeometry()throws Exception{Geometry141Checks.sourceGeometry(temp);}
 @Test void sourcePassAndSpecialOwnership()throws Exception{Geometry141Checks.sourcePassAndSpecialOwnership(temp);}
}
