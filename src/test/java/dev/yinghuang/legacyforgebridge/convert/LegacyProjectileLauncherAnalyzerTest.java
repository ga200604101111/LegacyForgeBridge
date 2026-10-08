package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LegacyProjectileLauncherAnalyzerTest {
    @TempDir Path directory;
    private static final String TARGET=LegacyProjectileLauncherFixture.TARGET;
    private static final String ITEM=LegacyProjectileLauncherFixture.ITEM;

    private LegacyProjectileLauncherAnalyzer.Analysis check(LegacyProjectileLauncherFixture.Shape shape,
            List<LegacyProjectileLauncherAnalyzer.ItemRegistration> items) throws Exception {
        Path jar=LegacyProjectileLauncherFixture.jar(directory.resolve(shape.name()+".jar"),shape);
        return new LegacyProjectileLauncherAnalyzer().inspect(jar,TARGET,items);
    }
    private LegacyProjectileLauncherAnalyzer.ItemRegistration item(){
        return new LegacyProjectileLauncherAnalyzer.ItemRegistration("renamed_queen",ITEM);
    }

    @Test void directReleaseUseProvesActualSpawnOperandAndWorldParameter() throws Exception {
        var a=check(LegacyProjectileLauncherFixture.Shape.RELEASE_DIRECT,List.of(item()));
        var p=a.proof().orElseThrow(()->new AssertionError(a.diagnostics()));
        assertEquals("renamed_queen",p.registryName());
        assertEquals(LegacyProjectileLauncherAnalyzer.Callback.RELEASE_USE,p.callback());
        assertEquals("onPlayerStoppedUsing",p.callbackName());
        assertEquals(TARGET,p.projectileClass());
    }
    @Test void aliasedLocalProjectileAndWorldRemainSourceProven() throws Exception {
        var a=check(LegacyProjectileLauncherFixture.Shape.RIGHT_CLICK_ALIASES,List.of(item()));
        assertEquals(LegacyProjectileLauncherAnalyzer.Callback.RIGHT_CLICK,a.proof().orElseThrow(() -> new AssertionError(a.diagnostics())).callback());
    }
    @Test void inheritedSourceOwnedReleaseCallbackRemainsProvable() throws Exception {
        var a=check(LegacyProjectileLauncherFixture.Shape.SOURCE_INHERITED_CALLBACK,List.of(item()));
        var p=a.proof().orElseThrow(()->new AssertionError(a.diagnostics()));
        assertEquals(LegacyProjectileLauncherFixture.ITEM_BASE,p.declaringOwner());
    }
    @Test void methodWideNewAndSpawnButOtherSpawnOperandMustReject() throws Exception {
        assertTrue(check(LegacyProjectileLauncherFixture.Shape.SPAWNS_OTHER_WITH_TARGET_ALLOCATED,
                List.of(item())).proof().isEmpty());
    }
    @Test void factoryResultIsNotDirectProjectileAllocation() throws Exception {
        assertTrue(check(LegacyProjectileLauncherFixture.Shape.FACTORY_RESULT,List.of(item())).proof().isEmpty());
    }
    @Test void receiverMustBeCallbackWorldParameter() throws Exception {
        assertTrue(check(LegacyProjectileLauncherFixture.Shape.STATIC_WORLD_RECEIVER,List.of(item())).proof().isEmpty());
    }
    @Test void differentMethodWithSameDescriptorIsNotSupportedCallback() throws Exception {
        assertTrue(check(LegacyProjectileLauncherFixture.Shape.NON_CALLBACK,List.of(item())).proof().isEmpty());
    }
    @Test void twoDirectSpawnsWithinOneCallbackAreNotAUniqueLaunchProof() throws Exception {
        assertTrue(check(LegacyProjectileLauncherFixture.Shape.MULTIPLE_SPAWNS,List.of(item())).proof().isEmpty());
    }
    @Test void mergedDistinctAllocationSitesAreAmbiguous() throws Exception {
        assertTrue(check(LegacyProjectileLauncherFixture.Shape.MERGED_TARGET_ALLOCATIONS,List.of(item())).proof().isEmpty());
    }
    @Test void multipleRegisteredItemsCannotClaimSameProjectile() throws Exception {
        var a=check(LegacyProjectileLauncherFixture.Shape.RELEASE_DIRECT,List.of(item(),
                new LegacyProjectileLauncherAnalyzer.ItemRegistration("second_item",
                        LegacyProjectileLauncherFixture.SECOND_ITEM)));
        assertTrue(a.proof().isEmpty());
    }
    @Test void duplicateRegistryKeysCannotPassAsOneLauncher() throws Exception {
        var a=check(LegacyProjectileLauncherFixture.Shape.RELEASE_DIRECT,List.of(item(),
                new LegacyProjectileLauncherAnalyzer.ItemRegistration("renamed_queen",
                        LegacyProjectileLauncherFixture.SECOND_ITEM)));
        assertTrue(a.proof().isEmpty());
    }
    @Test void duplicateRegistrationForSameSourceClassIsBlocked() throws Exception {
        var a=check(LegacyProjectileLauncherFixture.Shape.RELEASE_DIRECT,List.of(item(),item()));
        assertTrue(a.proof().isEmpty());
    }
    @Test void bytecodeConstructorDescriptorMustExistInActualSourceClass() throws Exception {
        assertTrue(check(LegacyProjectileLauncherFixture.Shape.MISSING_CONSTRUCTOR_DESCRIPTOR,
                List.of(item())).proof().isEmpty());
    }
    @Test void registeredItemsWithoutSourceProjectileCannotPass() throws Exception {
        var a=check(LegacyProjectileLauncherFixture.Shape.RELEASE_DIRECT,List.of(item()));
        var jar=LegacyProjectileLauncherFixture.jar(directory.resolve("alternate.jar"),
                LegacyProjectileLauncherFixture.Shape.RELEASE_DIRECT);
        var unavailable=new LegacyProjectileLauncherAnalyzer().inspect(jar,"foreign/missing/NoSuchEntity",List.of(item()));
        assertTrue(unavailable.proof().isEmpty());
        assertTrue(a.proof().isPresent());
    }
}
