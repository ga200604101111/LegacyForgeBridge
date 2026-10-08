package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Renamed source bytecode fixtures; the production recognizer has no mod-specific selector. */
class LegacyBlockTileModelPreflightTest {
    @TempDir Path directory;
    private static final String BLOCK = LegacyBlockTileModelFixture.BLOCK;
    private static final String TILE = LegacyBlockTileModelFixture.TILE;
    private static final LegacyBlockTileModelPreflight.BlockIdentity BLOCK_ID =
            new LegacyBlockTileModelPreflight.BlockIdentity("generic_wall_light", BLOCK);
    private static final LegacyBlockTileModelPreflight.TileIdentity TILE_ID =
            new LegacyBlockTileModelPreflight.TileIdentity(TILE, "Generic Wall Display");

    private LegacyBlockTileModelPreflight.Analysis check(LegacyBlockTileModelFixture.Case caseId)
            throws Exception {
        return new LegacyBlockTileModelPreflight().inspect(
                LegacyBlockTileModelFixture.jar(directory.resolve(caseId.name()+".jar"), caseId),
                List.of(BLOCK_ID), List.of(TILE_ID));
    }
    @Test void ordinaryBlockMayCreateRegisteredAnimatedTileWithoutBlockContainer() throws Exception {
        var analysis = check(LegacyBlockTileModelFixture.Case.GOOD);
        var p = analysis.candidates().stream().findFirst()
                .orElseThrow(() -> new AssertionError(analysis.skipped()+" "+analysis.diagnostics()));
        assertEquals("generic_wall_light",p.registryName());
        assertEquals(LegacyBlockTileModelFixture.BASE,p.hasTileEntityOwner());
        assertEquals(BLOCK,p.sourceBlockClass());
        assertEquals(BLOCK,p.createTileEntityOwner());
        assertEquals(TILE,p.tileClass());
        assertEquals("Generic Wall Display",p.tileRegistryId());
        assertEquals(LegacyBlockTileModelFixture.RENDERER,p.rendererClass());
        assertEquals(LegacyBlockTileModelFixture.MODEL,p.modelClass());
        assertTrue(p.sourceTileTickPresent());
        assertTrue(p.rendererCallsTileModelMethod());
        assertTrue(p.rendererReadsTileFields());
        assertTrue(p.metadataDependentBounds());
        assertEquals(14,p.sourceConstantLight());
        assertTrue(p.sourceQuantityDroppedZero());
    }
    @Test void inheritedTileMarkerMustReturnTrue() throws Exception {
        assertTrue(check(LegacyBlockTileModelFixture.Case.MARKER_FALSE).candidates().isEmpty());
    }
    @Test void actualFactoryReturnValueMustBeConstructedTile() throws Exception {
        assertTrue(check(LegacyBlockTileModelFixture.Case.BLOCK_FACTORY_WRONG_RETURN).candidates().isEmpty());
    }
    @Test void missingTileEntityRegistrationIsRejected() throws Exception {
        Path jar=LegacyBlockTileModelFixture.jar(directory.resolve("missing-tile-reg.jar"),LegacyBlockTileModelFixture.Case.GOOD);
        var r=new LegacyBlockTileModelPreflight().inspect(jar,List.of(BLOCK_ID),List.of());
        assertTrue(r.candidates().isEmpty());
    }
    @Test void duplicateTileIdMustFailClosed() throws Exception {
        Path jar=LegacyBlockTileModelFixture.jar(directory.resolve("duplicate-tile-id.jar"),LegacyBlockTileModelFixture.Case.GOOD);
        var r=new LegacyBlockTileModelPreflight().inspect(jar,List.of(BLOCK_ID),List.of(
                TILE_ID,new LegacyBlockTileModelPreflight.TileIdentity("other/Tile","Generic Wall Display")));
        assertTrue(r.candidates().isEmpty());
    }
    @Test void duplicateBlockRegistryIdentityMustFailClosed() throws Exception {
        Path jar=LegacyBlockTileModelFixture.jar(directory.resolve("duplicate-block.jar"),LegacyBlockTileModelFixture.Case.GOOD);
        var r=new LegacyBlockTileModelPreflight().inspect(jar,List.of(BLOCK_ID,BLOCK_ID),List.of(TILE_ID));
        assertTrue(r.candidates().isEmpty());
    }
    @Test void rendererBindingIsNotGuessableFromSimilarClassNames() throws Exception {
        assertTrue(check(LegacyBlockTileModelFixture.Case.CLIENT_BIND_MISSING).candidates().isEmpty());
    }
    @Test void duplicateRendererBindingsAreAmbiguous() throws Exception {
        assertTrue(check(LegacyBlockTileModelFixture.Case.CLIENT_BIND_DUPLICATE).candidates().isEmpty());
    }
    @Test void rendererMustExtendActualLegacyTileRenderer() throws Exception {
        assertTrue(check(LegacyBlockTileModelFixture.Case.UNKNOWN_RENDERER_BASE).candidates().isEmpty());
    }
    @Test void rendererMustConstructSourceModelBase() throws Exception {
        assertTrue(check(LegacyBlockTileModelFixture.Case.MODEL_ALLOCATION_MISSING).candidates().isEmpty());
    }
    @Test void animationReferenceIsOptionalButNotInferred() throws Exception {
        var a=check(LegacyBlockTileModelFixture.Case.MODEL_ANIMATION_MISSING);
        assertEquals(1,a.candidates().size());
        assertFalse(a.candidates().getFirst().rendererCallsTileModelMethod());
    }
    @Test void inheritedTileMarkerCannotBeImagined() throws Exception {
        assertTrue(check(LegacyBlockTileModelFixture.Case.BLOCK_NO_INHERITED_MARKER).candidates().isEmpty());
    }
    @Test void noReachableModelDrawIsNotModelPresentation() throws Exception {
        assertTrue(check(LegacyBlockTileModelFixture.Case.NO_RENDER_DRAW).candidates().isEmpty());
    }
    @Test void combinedClientRegistryTileAndRendererRegistrationWorksGenerically() throws Exception {
        var a=check(LegacyBlockTileModelFixture.Case.CLIENT_REGISTER_WITH_ID);
        assertEquals(1,a.candidates().size());
        assertEquals(LegacyBlockTileModelFixture.MODEL,a.candidates().getFirst().modelClass());
    }
    @Test void aMatchingSourceClassWithoutBlockRegistryEntryMustNotQualify() throws Exception {
        Path jar=LegacyBlockTileModelFixture.jar(directory.resolve("no-source-registration.jar"),
                LegacyBlockTileModelFixture.Case.GOOD);
        var a=new LegacyBlockTileModelPreflight().inspect(jar,List.of(),List.of(TILE_ID));
        assertTrue(a.candidates().isEmpty());
    }
    @Test void unprovenClientRendererOperandsFailClosed() throws Exception {
        var a=check(LegacyBlockTileModelFixture.Case.UNRESOLVED_CLIENT_BIND);
        assertTrue(a.candidates().isEmpty());
        assertFalse(a.diagnostics().isEmpty());
    }
    @Test void dynamicLightIsNotMisreportedAsConstant() throws Exception {
        var a=check(LegacyBlockTileModelFixture.Case.DYNAMIC_LIGHT);
        assertEquals(1,a.candidates().size());
        assertNull(a.candidates().getFirst().sourceConstantLight());
    }
    @Test void missingSourceTileClassFailsBeforeRendererAdmission() throws Exception {
        assertTrue(check(LegacyBlockTileModelFixture.Case.MISSING_TILE_SOURCE).candidates().isEmpty());
    }
}
