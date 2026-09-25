package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.*;
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
        assertEquals(9,root.get("schemaVersion").getAsInt());assertFalse(root.get("runtimeImplementationWired").getAsBoolean());assertFalse(root.get("impactCompilerWired").getAsBoolean());
        assertEquals(1,root.get("proofCompleteFamilies").getAsInt());assertEquals(0,root.get("metadataBindingFamilies").getAsInt());assertEquals(0,root.get("commonImpactFamilies").getAsInt());assertEquals(0,root.get("selectorEffectDispatchFamilies").getAsInt());assertEquals(0,root.get("randomTeleportWrapperFamilies").getAsInt());assertEquals(0,root.get("teleportStateSkeletonFamilies").getAsInt());assertEquals(0,root.get("teleportGameplaySafetyFamilies").getAsInt());assertEquals(0,root.get("teleportPresentationFamilies").getAsInt());
        JsonObject rule=root.getAsJsonArray("rules").get(0).getAsJsonObject();assertFalse(rule.get("metadataSelectorBindingProven").getAsBoolean());assertFalse(rule.get("commonImpactSemanticsProven").getAsBoolean());assertFalse(rule.get("selectorEffectDispatchProven").getAsBoolean());assertFalse(rule.get("randomTeleportWrapperProven").getAsBoolean());assertFalse(rule.get("teleportStateSkeletonProven").getAsBoolean());assertFalse(rule.get("teleportGameplaySafetyProven").getAsBoolean());assertFalse(rule.get("teleportPresentationProven").getAsBoolean());assertFalse(rule.get("impactSemanticsComplete").getAsBoolean());
        JsonObject first=rule.getAsJsonArray("variants").get(0).getAsJsonObject();assertFalse(first.has("legacyMeta"));assertEquals("UNCOMPILED",first.get("impactEffect").getAsString());
    }

    @Test void exposesLegacyMetadataOnlyAfterCanonicalMapBindingProof()throws Exception{
        JsonObject root=run(VariantSnowballMapBindingFixture.write(tempDir.resolve("bound.jar")),tempDir.resolve("staging-bound"));assertEquals(1,root.get("metadataBindingFamilies").getAsInt());assertEquals(0,root.get("teleportPresentationFamilies").getAsInt());JsonObject first=root.getAsJsonArray("rules").get(0).getAsJsonObject().getAsJsonArray("variants").get(0).getAsJsonObject();assertEquals(0,first.get("legacyMeta").getAsInt());
    }

    @Test void materializesCommonImpactShellWithoutInventingSelectorEffects()throws Exception{
        JsonObject root=run(VariantSnowballImpactFixture.write(tempDir.resolve("impact.jar")),tempDir.resolve("staging-impact"));assertEquals(1,root.get("commonImpactFamilies").getAsInt());assertEquals(0,root.get("teleportPresentationFamilies").getAsInt());JsonObject rule=root.getAsJsonArray("rules").get(0).getAsJsonObject();assertTrue(rule.get("commonImpactSemanticsProven").getAsBoolean());assertFalse(rule.get("selectorEffectDispatchProven").getAsBoolean());assertFalse(rule.get("impactSemanticsComplete").getAsBoolean());
    }

    @Test void sourceProvenSelectorEffectsReplacePerVariantUncompiledMarkersButRuntimeStaysClosed()throws Exception{
        JsonObject root=run(VariantSnowballSelectorEffectFixture.write(tempDir.resolve("effects.jar")),tempDir.resolve("staging-effects"));assertEquals(1,root.get("selectorEffectDispatchFamilies").getAsInt());assertEquals(1,root.get("selectorSpecificCompleteFamilies").getAsInt());assertEquals(0,root.get("teleportPresentationFamilies").getAsInt());JsonObject rule=root.getAsJsonArray("rules").get(0).getAsJsonObject();assertTrue(rule.get("impactSemanticsComplete").getAsBoolean());assertFalse(rule.get("runtimeImplementationWired").getAsBoolean());JsonObject poison=rule.getAsJsonArray("variants").get(1).getAsJsonObject();assertEquals("POTION",poison.get("impactEffect").getAsString());
    }

    @Test void randomTeleportWrapperProofRefinesCustomHelperWithoutClaimingTeleportCoreOrRuntime()throws Exception{
        JsonObject root=run(VariantSnowballTeleportWrapperFixture.write(tempDir.resolve("teleport.jar")),tempDir.resolve("staging-teleport"));assertEquals(1,root.get("randomTeleportWrapperFamilies").getAsInt());assertEquals(0,root.get("teleportPresentationFamilies").getAsInt());JsonObject rule=root.getAsJsonArray("rules").get(0).getAsJsonObject();assertTrue(rule.get("randomTeleportWrapperProven").getAsBoolean());assertFalse(rule.get("teleportStateSkeletonProven").getAsBoolean());assertFalse(rule.get("impactSemanticsComplete").getAsBoolean());JsonObject custom=rule.getAsJsonArray("variants").get(1).getAsJsonObject();assertEquals("RANDOM_TELEPORT_WRAPPER_PROVEN",custom.get("impactEffect").getAsString());
    }

    @Test void teleportStateTransactionSkeletonIsMaterializedWithoutOpeningSuccessCriteriaOrRuntime()throws Exception{
        JsonObject root=run(VariantSnowballTeleportStateFixture.write(tempDir.resolve("state.jar")),tempDir.resolve("staging-state"));assertEquals(1,root.get("teleportStateSkeletonFamilies").getAsInt());assertEquals(0,root.get("teleportGameplaySafetyFamilies").getAsInt());assertEquals(0,root.get("teleportPresentationFamilies").getAsInt());JsonObject rule=root.getAsJsonArray("rules").get(0).getAsJsonObject();assertTrue(rule.get("teleportStateSkeletonProven").getAsBoolean());assertFalse(rule.get("impactSemanticsComplete").getAsBoolean());JsonObject custom=rule.getAsJsonArray("variants").get(1).getAsJsonObject();assertEquals("RANDOM_TELEPORT_STATE_SKELETON_PROVEN",custom.get("impactEffect").getAsString());
    }

    @Test void teleportGameplaySafetyCoreIsMaterializedWithoutClaimingPresentationOrRuntime()throws Exception{
        JsonObject root=run(VariantSnowballTeleportSafetyFixture.write(tempDir.resolve("safety.jar")),tempDir.resolve("staging-safety"));assertEquals(1,root.get("teleportGameplaySafetyFamilies").getAsInt());assertEquals(0,root.get("teleportPresentationFamilies").getAsInt());JsonObject rule=root.getAsJsonArray("rules").get(0).getAsJsonObject();assertTrue(rule.get("teleportGameplaySafetyProven").getAsBoolean());assertFalse(rule.get("selectorSpecificImpactSemanticsComplete").getAsBoolean());assertFalse(rule.get("impactSemanticsComplete").getAsBoolean());JsonObject custom=rule.getAsJsonArray("variants").get(1).getAsJsonObject();assertEquals("RANDOM_TELEPORT_GAMEPLAY_SAFETY_PROVEN",custom.get("impactEffect").getAsString());assertFalse(custom.get("teleportPresentationComplete").getAsBoolean());
    }

    @Test void completePortalPresentationClosesSelectorSourceSemanticsButNotRuntime()throws Exception{
        JsonObject root=run(VariantSnowballTeleportPresentationFixture.write(tempDir.resolve("presentation.jar")),tempDir.resolve("staging-presentation"));assertEquals(1,root.get("teleportGameplaySafetyFamilies").getAsInt());assertEquals(1,root.get("teleportPresentationFamilies").getAsInt());assertEquals(1,root.get("selectorSpecificCompleteFamilies").getAsInt());
        JsonObject rule=root.getAsJsonArray("rules").get(0).getAsJsonObject();assertTrue(rule.get("teleportPresentationProven").getAsBoolean());assertTrue(rule.get("selectorSpecificImpactSemanticsComplete").getAsBoolean());assertTrue(rule.get("impactSemanticsComplete").getAsBoolean());assertFalse(rule.get("runtimeImplementationWired").getAsBoolean());assertFalse(root.get("impactCompilerWired").getAsBoolean());
        JsonObject custom=rule.getAsJsonArray("variants").get(1).getAsJsonObject();assertEquals("RANDOM_TELEPORT_SEMANTICS_PROVEN",custom.get("impactEffect").getAsString());assertEquals(128,custom.get("portalParticleCount").getAsInt());assertTrue(custom.get("portalParticleLoopProven").getAsBoolean());assertTrue(custom.get("portalInterpolationProven").getAsBoolean());assertTrue(custom.get("portalRandomizationProven").getAsBoolean());assertEquals("mob.endermen.portal",custom.get("portalSound").getAsString());assertTrue(custom.get("portalOriginSoundProven").getAsBoolean());assertTrue(custom.get("portalEntitySoundProven").getAsBoolean());assertTrue(custom.get("teleportPresentationComplete").getAsBoolean());
    }

    private JsonObject run(Path source,Path staging)throws Exception{Files.createDirectories(staging);LegacyModMetadata metadata=new LegacyModMetadata(source.getFileName().toString(),"test",List.of(new LegacyModMetadata.ModEntry("foreign","Foreign","1.0","1.7.10",List.of())));LegacyJarAnalyzer.Analysis jarAnalysis=new LegacyJarAnalyzer.Analysis(source.getFileName().toString(),0,0,false,false,0,0,0,0,Set.of(),Set.of(),Set.of());ConversionContext context=new ConversionContext(source,staging,tempDir.resolve("candidate-"+source.getFileName()),"sha",Files.size(source),metadata,jarAnalysis,new DiagnosticCollector(),"generic-test");new LegacyVariantSnowballPass().apply(context);return JsonParser.parseString(Files.readString(staging.resolve(LegacyVariantSnowballPass.OUTPUT))).getAsJsonObject();}
}
