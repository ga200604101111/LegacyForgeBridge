package dev.yinghuang.legacyforgebridge.runtime;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class LegacyRuntimeManifestTest {
    static final String VALID = """
            {"schemaVersion":1,"status":"converted","installable":true,
             "mod":{"fabricId":"fixture","logicalMods":[{"modid":"ExampleMod"}]},
             "registries":{"entities":{"ExampleMod:7":"fixture:beast"},"guis":{"ExampleMod:3":"fixture:bag"}}}
            """;
    @Test void acceptsOnlyInstalledOwnerAndKeepsCaseSensitiveLegacyIdentity() {
        var manifest = LegacyRuntimeManifest.parse(VALID, "fixture");
        assertEquals("fixture:beast", manifest.entities().get(new LegacyRuntimeManifest.Identity("ExampleMod", 7)));
        assertFalse(manifest.entities().containsKey(new LegacyRuntimeManifest.Identity("examplemod", 7)));
        assertThrows(IllegalArgumentException.class, () -> LegacyRuntimeManifest.parse(VALID, "other"));
    }
    @Test void partialRpgToolCandidatesCannotBecomeRuntimeProviders() {
        String partial = VALID.replace("converted", "partial").replace("ExampleMod", "rpgtool1");
        assertThrows(IllegalArgumentException.class, () -> LegacyRuntimeManifest.parse(partial, "fixture"));
        assertThrows(IllegalArgumentException.class, () -> LegacyRuntimeManifest.parse(VALID.replace("true", "false"), "fixture"));
        assertThrows(IllegalArgumentException.class, () -> LegacyRuntimeManifest.parse(VALID.replace("true", "\"true\""), "fixture"));
    }
    @Test void rejectsUnknownSchemaAndUnownedOrNoncanonicalIds() {
        assertThrows(IllegalArgumentException.class, () -> LegacyRuntimeManifest.parse(VALID.replace("\"schemaVersion\":1", "\"schemaVersion\":2"), "fixture"));
        assertThrows(IllegalArgumentException.class, () -> LegacyRuntimeManifest.parse(VALID.replace("ExampleMod:7", "AnotherMod:7"), "fixture"));
        assertThrows(IllegalArgumentException.class, () -> LegacyRuntimeManifest.parse(VALID.replace("ExampleMod:7", "ExampleMod:07"), "fixture"));
        assertThrows(IllegalArgumentException.class, () -> LegacyRuntimeManifest.parse(VALID.replace("fixture:beast", "7"), "fixture"));
    }
    @Test void rejectsDuplicateLogicalModsAndOversizedManifest() {
        String duplicate = VALID.replace("[{\"modid\":\"ExampleMod\"}]", "[{\"modid\":\"ExampleMod\"},{\"modid\":\"ExampleMod\"}]");
        assertThrows(IllegalArgumentException.class, () -> LegacyRuntimeManifest.parse(duplicate, "fixture"));
        assertThrows(IllegalArgumentException.class, () -> LegacyRuntimeManifest.parse(" ".repeat(LegacyRuntimeManifest.MAX_BYTES + 1), "fixture"));
    }
    @Test void manifestsDefensivelyCopyIdentityMaps() {
        var id = new LegacyRuntimeManifest.Identity("test", -7);
        var input = new HashMap<>(Map.of(id, "fixture:beast"));
        var manifest = new LegacyRuntimeManifest("fixture", Set.of("test"), input, Map.of());
        input.clear(); assertEquals(1, manifest.entities().size());
        assertThrows(UnsupportedOperationException.class, () -> manifest.entities().clear());
    }
    @Test void bindingsRequireFactoriesAndInstallBothCategoriesAtomically() {
        var manifest = LegacyRuntimeManifest.parse(VALID, "fixture");
        var bindings = new LegacyRuntimeBindings<String, String>();
        assertThrows(IllegalArgumentException.class, () -> bindings.install(manifest, Map.of("fixture:beast", "entity-adapter"), Map.of()));
        assertTrue(bindings.entity("ExampleMod", 7).isEmpty());
        bindings.install(manifest, Map.of("fixture:beast", "entity-adapter"), Map.of("fixture:bag", "gui-adapter"));
        assertEquals("entity-adapter", bindings.entity("ExampleMod", 7).orElseThrow().adapter());
        assertEquals("gui-adapter", bindings.gui("ExampleMod", 3).orElseThrow().adapter());
        assertTrue(bindings.gui("ExampleMod", 7).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> bindings.install(manifest, Map.of("fixture:beast", "replacement"), Map.of("fixture:bag", "other")));
        assertEquals("entity-adapter", bindings.entity("ExampleMod", 7).orElseThrow().adapter());
    }
    @Test void unlistedFactoriesAreRejectedAndNumericIdsDoNotCollideAcrossMods() {
        var bindings = new LegacyRuntimeBindings<String, String>();
        var manifest = LegacyRuntimeManifest.parse(VALID, "fixture");
        assertThrows(IllegalArgumentException.class, () -> bindings.install(manifest,
                Map.of("fixture:beast", "a", "other:beast", "b"), Map.of("fixture:bag", "c")));
        bindings.install(manifest, Map.of("fixture:beast", "first"), Map.of("fixture:bag", "bag"));
        var second = new LegacyRuntimeManifest("another", Set.of("SecondMod"),
                Map.of(new LegacyRuntimeManifest.Identity("SecondMod", 7), "another:beast"), Map.of());
        bindings.install(second, Map.of("another:beast", "second"), Map.of());
        assertEquals("first", bindings.entity("ExampleMod", 7).orElseThrow().adapter());
        assertEquals("second", bindings.entity("SecondMod", 7).orElseThrow().adapter());
    }
    @Test void multipleLegacyIdsMayShareOneExplicitAdapter() {
        var a = new LegacyRuntimeManifest.Identity("test", 1);
        var b = new LegacyRuntimeManifest.Identity("test", 2);
        var manifest = new LegacyRuntimeManifest("fixture", Set.of("test"), Map.of(a, "fixture:beast", b, "fixture:beast"), Map.of());
        var bindings = new LegacyRuntimeBindings<String, String>();
        bindings.install(manifest, Map.of("fixture:beast", "shared"), Map.of());
        assertEquals(bindings.entity("test", 1), bindings.entity("test", 2));
    }
}
