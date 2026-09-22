package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@Tag("exact-corpus")
class BambooRotatingAssemblyEntityExactTest {
    private static final String SHA="bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";

    @Test
    void exactWindAndWaterMillsAreTheOnlyAdmittedRotatingAssemblies()throws Exception{
        String input=System.getProperty("lfb.exactCorpus.jar");assertNotNull(input);
        Path source=Path.of(input);assertTrue(Files.isRegularFile(source));assertEquals(SHA,Hashing.sha256(source));

        var analysis=new LegacyRotatingAssemblyEntityAnalyzer().analyze(source);
        assertTrue(analysis.skipped().isEmpty(),analysis.skipped().toString());
        assertEquals(2,analysis.rules().size(),analysis.diagnostics().toString());
        Map<String,LegacyRotatingAssemblyEntityAnalyzer.Rule> rules=analysis.rules().stream()
                .collect(Collectors.toMap(LegacyRotatingAssemblyEntityAnalyzer.Rule::registryName,Function.identity()));

        var wind=rules.get("WindMill");assertNotNull(wind);
        assertEquals("ruby/bamboo/entity/EntityWindmill",wind.sourceClass());
        assertEquals("ruby/bamboo/render/RenderWindmill",wind.rendererClass());
        assertEquals("ruby/bamboo/render/ModelWindmill",wind.sourceModelClass());
        assertEquals(8,wind.legacyNumericId());assertEquals(80,wind.trackingRange());assertEquals(10,wind.updateFrequency());assertFalse(wind.velocityUpdates());
        assertEquals(LegacyRotatingAssemblyEntityAnalyzer.Adapter.VARIABLE_Z_RADIAL,wind.adapter());
        assertEquals(16,wind.directionWatcher());assertEquals(19,wind.sizeWatcher());assertEquals(17,wind.countWatcher());assertEquals(18,wind.textureWatcher());assertEquals(-1,wind.reverseWatcher());
        assertEquals(0,wind.directionDefault());assertEquals(1,wind.sizeDefault());assertEquals(1,wind.sizeMin());assertEquals(5,wind.sizeMax());
        assertEquals(0,wind.countDefault());assertEquals(0,wind.textureDefault());assertEquals(4,wind.countBase());assertEquals(8,wind.countMax());
        assertEquals(2,wind.staticParts().size());assertEquals(2,wind.repeatedPrimary().size());assertTrue(wind.repeatedSecondary().isEmpty());
        assertEquals(.0625F,wind.modelScale(),0.0001F);assertEquals(64,wind.modelTextureWidth());assertEquals(32,wind.modelTextureHeight());
        assertEquals(java.util.List.of("bamboo:textures/entitys/windmill.png","bamboo:textures/entitys/windmill_cloth.png"),wind.textures());
        assertTrue(wind.physicalCollision());assertTrue(wind.playerAttackRemoves());assertTrue(wind.randomInitialPhase());

        var water=rules.get("WaterMill");assertNotNull(water);
        assertEquals("ruby/bamboo/entity/EntityWaterwheel",water.sourceClass());
        assertEquals("ruby/bamboo/render/RenderWaterwheel",water.rendererClass());
        assertEquals("ruby/bamboo/render/ModelWaterwheel",water.sourceModelClass());
        assertEquals(9,water.legacyNumericId());assertEquals(80,water.trackingRange());assertEquals(10,water.updateFrequency());assertFalse(water.velocityUpdates());
        assertEquals(LegacyRotatingAssemblyEntityAnalyzer.Adapter.FLUID_X_RADIAL,water.adapter());
        assertEquals(16,water.directionWatcher());assertEquals(18,water.sizeWatcher());assertEquals(17,water.reverseWatcher());
        assertEquals(0,water.directionDefault());assertEquals(1,water.sizeDefault());assertEquals(1,water.sizeMin());assertEquals(2,water.sizeMax());assertEquals(0,water.reverseDefault());
        assertEquals(12,water.fixedRepeatCount());assertEquals(-20F,water.secondaryPhaseDegrees(),0.0001F);
        assertEquals(1,water.staticParts().size());assertEquals(3,water.repeatedPrimary().size());assertEquals(2,water.repeatedSecondary().size());
        assertEquals(.0625F,water.modelScale(),0.0001F);assertEquals(64,water.modelTextureWidth());assertEquals(32,water.modelTextureHeight());
        assertEquals(java.util.List.of("bamboo:textures/entitys/waterwheel.png"),water.textures());
        assertTrue(water.physicalCollision());assertTrue(water.playerAttackRemoves());assertTrue(water.randomInitialPhase());
    }
}
