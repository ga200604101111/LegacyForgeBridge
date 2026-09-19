package dev.yinghuang.legacyforgebridge.convert;
import org.junit.jupiter.api.Test;
class Presentation140RegressionTest {
    @Test void sourceEnumeration()throws Exception{Presentation140Checks.creativeUsesSourceEnumeration();}
    @Test void emptyCreativeOutput()throws Exception{Presentation140Checks.creativeEmptyIsNotDefaultZero();}
    @Test void unsignedCreativeData()throws Exception{Presentation140Checks.creativePreservesUnsignedMetadata();}
    @Test void foreignCreativeItem()throws Exception{Presentation140Checks.creativeRejectsOtherItem();}
    @Test void unknownCreativeTab()throws Exception{Presentation140Checks.creativeRejectsUnknownTabBranch();}
    @Test void statefulCreativeCallback()throws Exception{Presentation140Checks.creativeRejectsMutation();}
    @Test void creativeNbtIsNotDiscarded()throws Exception{Presentation140Checks.creativeRejectsUnrepresentedNbt();}
    @Test void ownershipIsHashBound()throws Exception{Presentation140Checks.changedSpecializedModelRevokesOwnership();}
    @Test void ownershipIsStagingBound()throws Exception{Presentation140Checks.ownershipRejectsPathsOutsideStaging();}
    @Test void wrongButPresentTextureIsReplaced()throws Exception{Presentation140Checks.sourceIconsReplaceTextureBackedBaseline();}
    @Test void specializedModelIsRetained()throws Exception{Presentation140Checks.sourceIconsPreserveSpecializedGeometry();}
    @Test void specializedItemRendererIsRetained()throws Exception{Presentation140Checks.sourceIconsPreserveSpecializedItemRenderer();}
}
