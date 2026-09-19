package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

class Presentation139RegressionTest {
    @TempDir Path temp;
    @Test void enumFieldsAndMapAndNullDefaults() throws Exception { Presentation139Checks.enumFieldsAndMapAndNullDefaults(temp); }
    @Test void arrayCopyPreservesEachEntry() throws Exception { Presentation139Checks.arrayCopyPreservesEachEntry(temp); }
    @Test void multipleLayersRetainMetadataTints() throws Exception { Presentation139Checks.multipleLayersRetainMetadataTints(temp); }
    @Test void coordinatesAreNotInvented() throws Exception { Presentation139Checks.coordinatesAreNotInvented(temp); }
    @Test void neighborMetadataIsNotSelfMetadata() throws Exception { Presentation139Checks.neighborMetadataIsNotSelfMetadata(temp); }
    @Test void statefulBaseSetterRejected() throws Exception { Presentation139Checks.statefulBaseSetterRejected(temp); }
    @Test void namesFollowSourceVariantRules() throws Exception { Presentation139Checks.namesFollowSourceVariantRules(temp); }
    @Test void unknownNamingStateRejected() throws Exception { Presentation139Checks.unknownNamingStateRejected(temp); }
    @Test void customDisplayNameExcluded() throws Exception { Presentation139Checks.customDisplayNameExcluded(temp); }
    @Test void carrierNamesRoundTripWithoutChangingCustomNames() throws Exception { Presentation139Checks.carrierNamesRoundTripWithoutChangingCustomNames(); }
    @Test void externallyChangedDefaultNameIsNotErased() throws Exception { Presentation139Checks.externallyChangedDefaultNameIsNotErased(); }
    @Test void absentOriginalNameIsRemovedOnReturn() throws Exception { Presentation139Checks.absentOriginalNameIsRemovedOnReturn(); }
}
