package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** NBT persistence is NOT evidence that the 1.7.10 server sent animated tile fields. */
class LegacyTileNbtPersistenceAnalyzerTest {
    @TempDir Path folder;
    private LegacyTileNbtPersistenceAnalyzer.Analysis inspect(LegacyTileNbtPersistenceFixture.Shape shape,
                                                               String... fields) throws Exception {
        Path source=LegacyTileNbtPersistenceFixture.jar(folder.resolve(shape.name()+".jar"),shape);
        return new LegacyTileNbtPersistenceAnalyzer().analyze(source,LegacyTileNbtPersistenceFixture.TILE,List.of(fields));
    }
    @Test void exactGetterSetterKeyAndThisReceiverProveOnlyNbtPair()throws Exception {
        var r=inspect(LegacyTileNbtPersistenceFixture.Shape.EXACT,"currentYaw");
        var audit=r.audit().orElseThrow(()->new AssertionError(r.diagnostics()));
        assertEquals(LegacyTileNbtPersistenceAnalyzer.Status.PAIRED_NBT_ONLY,audit.status());
        assertEquals(1,audit.pairedNbtFields().size());
        var f=audit.pairedNbtFields().getFirst();
        assertEquals("currentYaw",f.name());assertEquals("yaw",f.key());assertEquals("I",f.descriptor());
        assertTrue(audit.descriptionPacketHookObserved());assertTrue(audit.dataPacketHookObserved());
        assertFalse(audit.networkPayloadProven());assertFalse(audit.clientRuntimeWired());
    }
    @Test void recognizedSrgGetAndSetPairsAreSourceBacked()throws Exception {
        var audit=inspect(LegacyTileNbtPersistenceFixture.Shape.SRG,"currentYaw").audit().orElseThrow();
        assertEquals(1,audit.pairedNbtFields().size());
    }
    @Test void floatAndIntFieldsMayBePairedIndependently()throws Exception {
        var audit=inspect(LegacyTileNbtPersistenceFixture.Shape.EXTRA_FLOAT_PAIR,"currentYaw","framePhase").audit().orElseThrow();
        assertEquals(2,audit.pairedNbtFields().size());assertTrue(audit.unpairedVisualFields().isEmpty());
    }
    @Test void missingWriteDoesNotProvePersistence()throws Exception {
        var a=inspect(LegacyTileNbtPersistenceFixture.Shape.NO_WRITE,"currentYaw").audit().orElseThrow();
        assertEquals(0,a.pairedNbtFields().size());assertEquals(List.of("currentYaw"),a.unpairedVisualFields());
    }
    @Test void missingReadDoesNotProvePersistence()throws Exception {
        assertTrue(inspect(LegacyTileNbtPersistenceFixture.Shape.NO_READ,"currentYaw")
                .audit().orElseThrow().pairedNbtFields().isEmpty());
    }
    @Test void mismatchedNbtTagNamesAreRejected()throws Exception {
        assertTrue(inspect(LegacyTileNbtPersistenceFixture.Shape.DIFFERENT_KEY,"currentYaw")
                .audit().orElseThrow().pairedNbtFields().isEmpty());
    }
    @Test void writeToOtherFieldCannotBeAReadPair()throws Exception {
        assertTrue(inspect(LegacyTileNbtPersistenceFixture.Shape.WRITE_OTHER_FIELD,"currentYaw")
                .audit().orElseThrow().pairedNbtFields().isEmpty());
    }
    @Test void arithmeticPostprocessOfReadValueIsNotSameLosslessField()throws Exception {
        assertTrue(inspect(LegacyTileNbtPersistenceFixture.Shape.EXTRA_ARITHMETIC,"currentYaw")
                .audit().orElseThrow().pairedNbtFields().isEmpty());
    }
    @Test void multipleWritesToSameFieldCannotBecomeUnambiguousPair()throws Exception {
        assertTrue(inspect(LegacyTileNbtPersistenceFixture.Shape.DUPLICATE_WRITE,"currentYaw")
                .audit().orElseThrow().pairedNbtFields().isEmpty());
    }
    @Test void twoDifferentSourceFieldsWritingOneNbtKeyMustNotBothBePersistent()throws Exception {
        var audit=inspect(LegacyTileNbtPersistenceFixture.Shape.TAG_COLLISION,"currentYaw")
                .audit().orElseThrow();
        assertTrue(audit.pairedNbtFields().isEmpty());
        assertEquals(LegacyTileNbtPersistenceAnalyzer.Status.SOURCE_NBT_UNPROVABLE,audit.status());
    }
    @Test void conditionalReadNeedsAPathProofBeyondThisFamily()throws Exception {
        assertTrue(inspect(LegacyTileNbtPersistenceFixture.Shape.BRANCHED_READ,"currentYaw")
                .audit().orElseThrow().pairedNbtFields().isEmpty());
    }
    @Test void multipleReturnInstructionsCannotBeClaimedUnconditionallyPersistent()throws Exception {
        var a=inspect(LegacyTileNbtPersistenceFixture.Shape.EXTRA_RETURN,"currentYaw").audit().orElseThrow();
        assertTrue(a.pairedNbtFields().isEmpty());
        assertEquals(LegacyTileNbtPersistenceAnalyzer.Status.SOURCE_NBT_UNPROVABLE,a.status());
    }
    @Test void unprovedHelperCannotBeAValidDirectSerialization()throws Exception {
        assertTrue(inspect(LegacyTileNbtPersistenceFixture.Shape.UNPROVED_HELPER,"currentYaw")
                .audit().orElseThrow().pairedNbtFields().isEmpty());
    }
    @Test void writerReceiverMustBeTheNbtArgument()throws Exception {
        assertTrue(inspect(LegacyTileNbtPersistenceFixture.Shape.WRITE_OTHER_COMPOUND,"currentYaw")
                .audit().orElseThrow().pairedNbtFields().isEmpty());
    }
    @Test void readerReceiverMustBeTheNbtArgument()throws Exception {
        assertTrue(inspect(LegacyTileNbtPersistenceFixture.Shape.READ_OTHER_COMPOUND,"currentYaw")
                .audit().orElseThrow().pairedNbtFields().isEmpty());
    }
    @Test void fieldReceiverMustBeTheActualTileInstance()throws Exception {
        assertTrue(inspect(LegacyTileNbtPersistenceFixture.Shape.FIELD_OTHER_RECEIVER,"currentYaw")
                .audit().orElseThrow().pairedNbtFields().isEmpty());
    }
    @Test void onlyActualInvokespecialSuperCallsAdmitInheritedFieldSerialization()throws Exception {
        var audit=inspect(LegacyTileNbtPersistenceFixture.Shape.SOURCE_SUPER_CHAIN,"currentYaw","guardDelay")
                .audit().orElseThrow();
        assertEquals(2,audit.pairedNbtFields().size());assertTrue(audit.unpairedVisualFields().isEmpty());
    }
    @Test void subclassOverrideWithoutSuperCallCannotPretendInheritedNbtRan()throws Exception {
        var audit=inspect(LegacyTileNbtPersistenceFixture.Shape.OVERRIDE_WITHOUT_SUPER,"currentYaw","guardDelay")
                .audit().orElseThrow();
        assertEquals(1,audit.pairedNbtFields().size());assertEquals(List.of("guardDelay"),audit.unpairedVisualFields());
    }
    @Test void sourceSuperCallWithForeignNbtReceiverCannotProveParentFields()throws Exception {
        var audit=inspect(LegacyTileNbtPersistenceFixture.Shape.SUPER_WRONG_COMPOUND,
                "currentYaw","guardDelay").audit().orElseThrow();
        assertTrue(audit.pairedNbtFields().isEmpty());
    }
    @Test void sourceSuperCallWithForeignTileReceiverCannotProveParentFields()throws Exception {
        var audit=inspect(LegacyTileNbtPersistenceFixture.Shape.SUPER_WRONG_RECEIVER,
                "currentYaw","guardDelay").audit().orElseThrow();
        assertTrue(audit.pairedNbtFields().isEmpty());
    }
    @Test void inheritedFieldShadowedBySubclassIsUnsafe()throws Exception {
        assertTrue(inspect(LegacyTileNbtPersistenceFixture.Shape.SHADOWED_FIELD,"currentYaw").audit().isEmpty());
    }
    @Test void inheritedNbtPassthroughWithoutFieldEntriesLeavesTickVisualStateUnsynced()throws Exception {
        var audit=inspect(LegacyTileNbtPersistenceFixture.Shape.INHERITED_NO_STATE_PAIRS,
                "currentYaw","desiredYaw","guardDelay").audit().orElseThrow();
        assertEquals(0,audit.pairedNbtFields().size());
        assertEquals(List.of("currentYaw","desiredYaw","guardDelay"),audit.unpairedVisualFields());
        assertFalse(audit.descriptionPacketHookObserved());
        assertFalse(audit.dataPacketHookObserved());
        assertFalse(audit.networkPayloadProven());
        assertEquals(LegacyTileNbtPersistenceAnalyzer.Status.NO_NBT_PAIR,audit.status());
    }
    @Test void packetHooksWithoutAnyNbtFieldsDoNotConferSync()throws Exception {
        var audit=inspect(LegacyTileNbtPersistenceFixture.Shape.PACKET_HOOKS_WITHOUT_NBT,"currentYaw")
                .audit().orElseThrow();
        assertTrue(audit.descriptionPacketHookObserved());assertTrue(audit.dataPacketHookObserved());
        assertTrue(audit.pairedNbtFields().isEmpty());assertFalse(audit.networkPayloadProven());
        assertEquals(LegacyTileNbtPersistenceAnalyzer.Status.NO_NBT_PAIR,audit.status());
    }
    @Test void getNbtWithoutAssigningToFieldDoesNotProveRead()throws Exception {
        assertTrue(inspect(LegacyTileNbtPersistenceFixture.Shape.GETTER_WITHOUT_STORE,"currentYaw")
                .audit().orElseThrow().pairedNbtFields().isEmpty());
    }
    @Test void methodWithSameNameOnAnotherOwnerIsNotNbtSetter()throws Exception {
        assertTrue(inspect(LegacyTileNbtPersistenceFixture.Shape.OPAQUE_SETTER,"currentYaw")
                .audit().orElseThrow().pairedNbtFields().isEmpty());
    }
    @Test void writingUnknownTileStateWhilePackingIsNotPureSerialization()throws Exception {
        assertTrue(inspect(LegacyTileNbtPersistenceFixture.Shape.SOURCE_EXTRA_WRITE,"currentYaw")
                .audit().orElseThrow().pairedNbtFields().isEmpty());
    }
    @Test void computedTagNamesCannotBeTreatedAsSourceConstants()throws Exception {
        assertTrue(inspect(LegacyTileNbtPersistenceFixture.Shape.SOURCE_NBT_GETTER_DYNAMIC_KEY,"currentYaw")
                .audit().orElseThrow().pairedNbtFields().isEmpty());
    }
    @Test void missingSourceFieldIsNotInvented()throws Exception {
        assertTrue(inspect(LegacyTileNbtPersistenceFixture.Shape.EXACT,"inventedField").audit().isEmpty());
    }
    @Test void emptyDependencySetDoesNotClaimAnyPersistentState()throws Exception {
        assertTrue(inspect(LegacyTileNbtPersistenceFixture.Shape.EXACT).audit().isEmpty());
    }
    @Test void auditRecordCannotClaimNetworkOrModernRuntime()throws Exception {
        var audit=inspect(LegacyTileNbtPersistenceFixture.Shape.EXACT,"currentYaw").audit().orElseThrow();
        boolean networkDenied=false, runtimeDenied=false;
        try { new LegacyTileNbtPersistenceAnalyzer.Audit(
                audit.sourceTileClass(),audit.requiredVisualFields(),audit.pairedNbtFields(),
                audit.unpairedVisualFields(),true,true,true,false,audit.status()); }
        catch(IllegalArgumentException correct){ networkDenied=true; }
        try { new LegacyTileNbtPersistenceAnalyzer.Audit(
                audit.sourceTileClass(),audit.requiredVisualFields(),audit.pairedNbtFields(),
                audit.unpairedVisualFields(),true,true,false,true,audit.status()); }
        catch(IllegalArgumentException correct){ runtimeDenied=true; }
        assertTrue(networkDenied);assertTrue(runtimeDenied);
    }
}
