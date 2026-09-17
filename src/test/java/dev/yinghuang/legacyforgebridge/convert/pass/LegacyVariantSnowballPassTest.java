package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.VariantSnowballFixture;
import dev.yinghuang.legacyforgebridge.convert.VariantSnowballImpactFixture;
import dev.yinghuang.legacyforgebridge.convert.VariantSnowballMapBindingFixture;
import dev.yinghuang.legacyforgebridge.convert.VariantSnowballSelectorEffectFixture;
import dev.yinghuang.legacyforgebridge.convert.VariantSnowballTeleportWrapperFixture;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LegacyVariantSnowballPassTest {
    @TempDir Path tempDir;

    @Test void materializesLookupProofWithoutClaimingUnprovenMetadataBindingOrRuntime()throws Exception{
        JsonObject root=run(VariantSnowballFixture.write(tempDir.resolve("variant.jar")),tempDir.resolve("staging-unbound"));
        assertEquals(6,root.get("schemaVersion").getAsInt());assertFalse(root.get("runtimeImplementationWired").getAsBoolean());assertFalse(root.get("impactCompilerWired").getAsBoolean());
        assertEquals(1,root.get("proofCompleteFamilies").getAsInt());assertEquals(0,root.get("metadataBindingFamilies").getAsInt());assertEquals(0,root.get("commonImpactFamilies").getAsInt());assertEquals(0,root.get("selectorEffectDispatchFamilies").getAsInt());assertEquals(0,root.get("randomTeleportWrapperFamilies").getAsInt());
        JsonObject rule=root.getAsJsonArray("rules").get(0).getAsJsonObject();assertTrue(rule.get("metadataSelectorLookupProven").getAsBoolean());assertFalse(rule.get("metadataSelectorBindingProven").getAsBoolean());assertFalse(rule.get("commonImpactSemanticsProven").getAsBoolean());assertFalse(rule.get("selectorEffectDispatchProven").getAsBoolean());assertFalse(rule.get("randomTeleportWrapperProven").getAsBoolean());assertFalse(rule.get("impactSemanticsComplete").getAsBoolean());
        JsonObject first=rule.getAsJsonArray("variants").get(0).getAsJsonObject();assertEquals(0,first.get("selectorId").getAsInt());assertFalse(first.has("legacyMeta"));assertEquals("UNCOMPILED",first.get("impactEffect").getAsString());
    }

    @Test void exposesLegacyMetadataOnlyAfterCanonicalMapBindingProof()throws Exception{
        JsonObject root=run(VariantSnowballMapBindingFixture.write(tempDir.resolve("bound.jar")),tempDir.resolve("staging-bound"));
        assertEquals(1,root.get("metadataBindingFamilies").getAsInt());assertEquals(0,root.get("commonImpactFamilies").getAsInt());assertEquals(0,root.get("randomTeleportWrapperFamilies").getAsInt());JsonObject rule=root.getAsJsonArray("rules").get(0).getAsJsonObject();
        assertTrue(rule.get("metadataSelectorBindingProven").getAsBoolean());assertFalse(rule.has("metadataSelectorBindingBlocker"));assertFalse(rule.get("commonImpactSemanticsProven").getAsBoolean());
        JsonObject first=rule.getAsJsonArray("variants").get(0).getAsJsonObject();assertEquals(0,first.get("selectorId").getAsInt());assertEquals(0,first.get("legacyMeta").getAsInt());
    }

    @Test void materializesCommonImpactShellWithoutInventingSelectorEffects()throws Exception{
        JsonObject root=run(VariantSnowballImpactFixture.write(tempDir.resolve("impact.jar")),tempDir.resolve("staging-impact"));
        assertEquals(1,root.get("metadataBindingFamilies").getAsInt());assertEquals(1,root.get("commonImpactFamilies").getAsInt());assertEquals(0,root.get("selectorEffectDispatchFamilies").getAsInt());assertEquals(0,root.get("randomTeleportWrapperFamilies").getAsInt());
        JsonObject rule=root.getAsJsonArray("rules").get(0).getAsJsonObject();assertTrue(rule.get("selectorNullImpactGuardProven").getAsBoolean());assertTrue(rule.get("selectorBaseDamageAttackProven").getAsBoolean());assertTrue(rule.get("snowballPoofLoopProven").getAsBoolean());assertTrue(rule.get("serverTerminationProven").getAsBoolean());assertTrue(rule.get("commonImpactSemanticsProven").getAsBoolean());assertFalse(rule.get("selectorEffectDispatchProven").getAsBoolean());assertFalse(rule.get("impactSemanticsComplete").getAsBoolean());
    }

    @Test void sourceProvenSelectorEffectsReplacePerVariantUncompiledMarkersButRuntimeStaysClosed()throws Exception{
        JsonObject root=run(VariantSnowballSelectorEffectFixture.write(tempDir.resolve("effects.jar")),tempDir.resolve("staging-effects"));
        assertEquals(1,root.get("metadataBindingFamilies").getAsInt());assertEquals(1,root.get("commonImpactFamilies").getAsInt());assertEquals(1,root.get("selectorEffectDispatchFamilies").getAsInt());assertEquals(1,root.get("selectorSpecificCompleteFamilies").getAsInt());assertEquals(0,root.get("randomTeleportWrapperFamilies").getAsInt());
        JsonObject rule=root.getAsJsonArray("rules").get(0).getAsJsonObject();assertTrue(rule.get("selectorEffectDispatchProven").getAsBoolean());assertEquals(1,rule.get("selectorPotionEffectBranchCount").getAsInt());assertTrue(rule.get("selectorSpecificImpactSemanticsComplete").getAsBoolean());assertTrue(rule.get("impactSemanticsComplete").getAsBoolean());assertFalse(rule.get("runtimeImplementationWired").getAsBoolean());assertFalse(root.get("impactCompilerWired").getAsBoolean());
        JsonObject stone=rule.getAsJsonArray("variants").get(0).getAsJsonObject();JsonObject poison=rule.getAsJsonArray("variants").get(1).getAsJsonObject();assertEquals("NONE",stone.get("impactEffect").getAsString());assertEquals("POTION",poison.get("impactEffect").getAsString());assertEquals("poison",poison.get("potion").getAsString());assertEquals(30,poison.get("duration").getAsInt());assertEquals(3,poison.get("amplifier").getAsInt());
    }

    @Test void randomTeleportWrapperProofRefinesCustomHelperWithoutClaimingTeleportCoreOrRuntime()throws Exception{
        JsonObject root=run(VariantSnowballTeleportWrapperFixture.write(tempDir.resolve("teleport.jar")),tempDir.resolve("staging-teleport"));
        assertEquals(1,root.get("commonImpactFamilies").getAsInt());assertEquals(1,root.get("selectorEffectDispatchFamilies").getAsInt());assertEquals(0,root.get("selectorSpecificCompleteFamilies").getAsInt());assertEquals(1,root.get("randomTeleportWrapperFamilies").getAsInt());
        JsonObject rule=root.getAsJsonArray("rules").get(0).getAsJsonObject();assertTrue(rule.get("randomTeleportWrapperProven").getAsBoolean());assertEquals(1,rule.get("randomTeleportWrapperCount").getAsInt());assertFalse(rule.get("selectorSpecificImpactSemanticsComplete").getAsBoolean());assertFalse(rule.get("impactSemanticsComplete").getAsBoolean());assertFalse(rule.get("runtimeImplementationWired").getAsBoolean());
        JsonObject custom=rule.getAsJsonArray("variants").get(1).getAsJsonObject();assertEquals("RANDOM_TELEPORT_WRAPPER_PROVEN",custom.get("impactEffect").getAsString());assertEquals("teleportRandomly",custom.get("sourceImpactHelper").getAsString());assertEquals("teleportTo",custom.get("sourceTeleportHelper").getAsString());assertEquals(16.0D,custom.get("horizontalRandomRadius").getAsDouble());assertEquals(4,custom.get("verticalRandomRadius").getAsInt());
    }

    private JsonObject run(Path source,Path staging)throws Exception{
        Files.createDirectories(staging);LegacyModMetadata metadata=new LegacyModMetadata(source.getFileName().toString(),"test",List.of(new LegacyModMetadata.ModEntry("foreign","Foreign","1.0","1.7.10",List.of())));LegacyJarAnalyzer.Analysis jarAnalysis=new LegacyJarAnalyzer.Analysis(source.getFileName().toString(),0,0,false,false,0,0,0,0,Set.of(),Set.of(),Set.of());ConversionContext context=new ConversionContext(source,staging,tempDir.resolve("candidate-"+source.getFileName()),"sha",Files.size(source),metadata,jarAnalysis,new DiagnosticCollector(),"generic-test");new LegacyVariantSnowballPass().apply(context);return JsonParser.parseString(Files.readString(staging.resolve(LegacyVariantSnowballPass.OUTPUT))).getAsJsonObject();
    }
}
