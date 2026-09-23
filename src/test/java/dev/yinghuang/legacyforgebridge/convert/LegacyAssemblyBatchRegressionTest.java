package dev.yinghuang.legacyforgebridge.convert;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.convert.api.*;
import dev.yinghuang.legacyforgebridge.convert.pass.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import static org.junit.jupiter.api.Assertions.*;

class LegacyAssemblyBatchRegressionTest {
    @TempDir Path temp;
    @Test void javacIntegerYawAdmitsBothRadialFamiliesAndCompleteTray()throws Exception{
        Path source=LegacyAssemblySourceFixture.create(temp,false,true);
        var analysis=new LegacyRotatingAssemblyEntityAnalyzer().analyze(source);
        assertEquals(2,analysis.rules().size(),analysis.toString());
        assertTrue(analysis.skipped().isEmpty());assertTrue(analysis.diagnostics().isEmpty());
        var wind=analysis.rules().stream().filter(r->r.legacyNumericId()==8).findFirst().orElseThrow();
        assertEquals(LegacyRotatingAssemblyEntityAnalyzer.Adapter.VARIABLE_Z_RADIAL,wind.adapter());
        assertEquals(16,wind.directionWatcher());assertEquals(19,wind.sizeWatcher());assertEquals(17,wind.countWatcher());
        assertEquals(18,wind.textureWatcher());assertEquals(4,wind.countBase());assertEquals(8,wind.countMax());
        assertEquals(2,wind.staticParts().size());assertEquals(2,wind.repeatedPrimary().size());
        var wheel=analysis.rules().stream().filter(r->r.legacyNumericId()==9).findFirst().orElseThrow();
        assertEquals(LegacyRotatingAssemblyEntityAnalyzer.Adapter.FLUID_X_RADIAL,wheel.adapter());
        assertEquals(12,wheel.fixedRepeatCount());assertEquals(-20F,wheel.secondaryPhaseDegrees());
        assertEquals(3,wheel.repeatedPrimary().size());assertEquals(2,wheel.repeatedSecondary().size());
        var visible=new LegacyVisibleEntityPresentationAnalyzer().analyze(source);
        assertEquals(1,visible.rules().size(),visible.toString());var tray=visible.rules().getFirst();
        assertEquals(LegacyVisibleEntityPresentationAnalyzer.Adapter.TRAY_ITEMS,tray.adapter());
        assertEquals(12,tray.legacyNumericId());assertEquals(9,tray.parts().size());
        assertEquals(17,tray.itemWatcherBase());assertEquals(5,tray.itemWatcherCount());
        for(int slot=17;slot<=21;slot++)assertEquals(5,tray.watcherTypes().get(slot));
    }
    @Test void existingFloatYawRepresentationStillAdmitted()throws Exception{
        Path source=LegacyAssemblySourceFixture.create(temp,true,true);
        assertEquals(2,new LegacyRotatingAssemblyEntityAnalyzer().analyze(source).rules().size());
    }
    @Test void missingSourceTexturesAreNotReplacedByGuessedModels()throws Exception{
        Path source=LegacyAssemblySourceFixture.create(temp,false,false);
        assertTrue(new LegacyRotatingAssemblyEntityAnalyzer().analyze(source).rules().isEmpty());
        assertTrue(new LegacyVisibleEntityPresentationAnalyzer().analyze(source).rules().isEmpty());
    }
    @Test void correctedRulesReachValidatedCandidateSidecars()throws Exception{
        Path source=LegacyAssemblySourceFixture.create(temp,false,true);
        var context=LegacyPresentationBatchTestContext.create(source,temp.resolve("staging"));
        new LegacyRotatingAssemblyEntityPass().apply(context);new LegacyVisibleEntityPresentationPass().apply(context);
        var rotor=JsonParser.parseString(Files.readString(context.stagingDir().resolve(LegacyRotatingAssemblyEntityPass.OUTPUT))).getAsJsonObject();
        assertEquals(2,rotor.getAsJsonArray("rules").size());assertTrue(rotor.get("remoteSpawnRuntimeWired").getAsBoolean());
        var tray=JsonParser.parseString(Files.readString(context.stagingDir().resolve(LegacyVisibleEntityPresentationPass.OUTPUT))).getAsJsonObject();
        assertEquals(1,tray.getAsJsonArray("rules").size());
        assertEquals(12,tray.getAsJsonArray("rules").get(0).getAsJsonObject().get("legacyNumericId").getAsInt());
        // Both passes invoke the same strict rule validator used by the runtime registries.
    }
    @Test void headOfMethodGuardAndUnsafeUnboundedGetterAreDistinguished()throws Exception{
        Path source=LegacyAssemblySourceFixture.create(temp,false,true);
        try(JarFile jar=new JarFile(source.toFile())){
            ClassNode c=new ClassNode();try(var in=jar.getInputStream(jar.getJarEntry("fixture/rotors/Tray.class"))){new ClassReader(in).accept(c,0);}
            MethodNode method=c.methods.stream().filter(m->m.name.equals("item")).findFirst().orElseThrow();
            assertArrayEquals(new int[]{17,5},LegacyVisibleEntityPresentationAnalyzer.itemWatcherRange(method));
            for(var insn:method.instructions.toArray())if(insn instanceof JumpInsnNode j&&j.getOpcode()==Opcodes.IF_ICMPGE)method.instructions.remove(j);
            assertNull(LegacyVisibleEntityPresentationAnalyzer.itemWatcherRange(method));
        }
    }
}
