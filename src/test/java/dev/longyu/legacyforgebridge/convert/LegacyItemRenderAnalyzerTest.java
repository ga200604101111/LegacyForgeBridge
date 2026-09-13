package dev.longyu.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LegacyItemRenderAnalyzerTest {
    @TempDir Path temp;
    @Test void discoversDifferentModNamespacesAndPreservesContextBranchAndOperationOrder() throws Exception {
        for (String namespace : List.of("alchemy", "astronomy")) {
            var jar = LegacyRenderFixture.create(temp.resolve(namespace + ".jar"), namespace, false);
            var result = new LegacyItemRenderAnalyzer().analyze(jar);
            assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
            assertEquals(1, result.bindings().size());
            var binding = result.bindings().getFirst();
            assertEquals(namespace + "/Client", binding.fieldOwner());
            assertEquals("TOOL", binding.fieldName());
            assertFalse(binding.contexts().get("INVENTORY").custom());
            var equipped = binding.contexts().get("EQUIPPED").draws().getFirst();
            assertEquals(namespace + ":models/tool.obj", equipped.model());
            assertEquals(namespace + ":textures/tool.png", equipped.texture());
            assertEquals(List.of("translate", "scale", "rotate"), equipped.operations().stream().map(LegacyItemRenderAnalyzer.Operation::op).toList());
            assertEquals(.25F, equipped.operations().getFirst().values().getFirst());
            var firstPerson = binding.contexts().get("EQUIPPED_FIRST_PERSON").draws().getFirst();
            assertEquals(1F, firstPerson.operations().getFirst().values().getFirst(), "JVM IDIV must truncate before I2F");
        }
    }
    @Test void unknownItemDependentBranchIsReportedRatherThanGuessed() throws Exception {
        var jar = LegacyRenderFixture.create(temp.resolve("dynamic.jar"), "dynamic", true);
        var result = new LegacyItemRenderAnalyzer().analyze(jar);
        assertEquals(1, result.bindings().size());
        assertFalse(result.bindings().getFirst().contexts().containsKey("EQUIPPED"));
        assertTrue(result.diagnostics().stream().anyMatch(s -> s.contains("dynamic branch")));
    }
}
