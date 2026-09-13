package dev.longyu.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LegacyEquipmentRenderAnalyzerTest {
    @TempDir Path temp;
    @Test void recoversSourceRootPivotsAndOpposedAnimationAcrossIndependentNamespaces() throws Exception {
        for (String ns : List.of("alchemy", "astronomy")) {
            Path jar = EquipmentFixture.create(temp.resolve(ns),ns,false);
            var a = new LegacyItemRenderAnalyzer().analyzeEquipment(jar);
            assertTrue(a.diagnostics().isEmpty(), a.diagnostics().toString());
            assertEquals(1,a.bindings().size());
            var b = a.bindings().getFirst();
            assertEquals("relic",b.itemName()); assertEquals(1,b.armorSlot());
            assertEquals(2,b.standing().size()); assertEquals(2,b.crouching().size());
            var left = b.standing().getFirst(); var right = b.standing().get(1);
            assertEquals(ns+":models/relic.obj",left.model());
            assertEquals(ns+":textures/relic.png",left.texture());
            assertFalse(left.lighting()); assertFalse(left.cull());
            assertEquals("scale",left.operations().get(0).op());
            assertEquals("translate",left.operations().get(1).op());
            assertEquals(1.25F,left.operations().get(1).values().get(1).evaluate(new float[6]));
            float[] inputs={0,0,10,0,0,0.0625F};
            float angle=left.operations().get(2).values().get(0).evaluate(inputs);
            assertEquals(-angle,right.operations().get(1).values().get(0).evaluate(inputs),0.00001F);
            float crouch=b.crouching().getFirst().operations().get(2).values().get(0).evaluate(inputs);
            assertEquals(25F,crouch-angle,0.00001F);
        }
    }
    @Test void unknownEntityDependentBehaviorIsDiagnosedNotInvented() throws Exception {
        Path jar=EquipmentFixture.create(temp.resolve("dynamic"),"unresolved",true);
        var a=new LegacyItemRenderAnalyzer().analyzeEquipment(jar);
        assertTrue(a.bindings().isEmpty());
        assertTrue(a.diagnostics().stream().anyMatch(d->d.contains("unknown")));
    }
}
