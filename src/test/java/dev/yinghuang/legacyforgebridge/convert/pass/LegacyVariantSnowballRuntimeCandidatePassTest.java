package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.VariantSnowballLaunchFixture;
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

class LegacyVariantSnowballRuntimeCandidatePassTest {
    @TempDir Path tempDir;

    @Test void completePotionFamilyNormalizesLaunchAndImpactRuntimeCandidateWithoutRuntimeClaim()throws Exception{
        JsonObject root=run(VariantSnowballLaunchFixture.writePotion(tempDir.resolve("potion.jar")),tempDir.resolve("potion-staging"),true);
        assertEquals(2,root.get("schemaVersion").getAsInt());assertEquals(1,root.get("runtimeCandidateFamilies").getAsInt());assertEquals(0,root.get("rejectedFamilies").getAsInt());assertFalse(root.get("runtimeImplementationWired").getAsBoolean());assertFalse(root.get("projectileRuntimeWired").getAsBoolean());
        JsonObject rule=root.getAsJsonArray("rules").get(0).getAsJsonObject();assertEquals("foreign:variant_ball",rule.get("id").getAsString());assertEquals("foreign:variant_ball_projectile",rule.get("projectileId").getAsString());assertEquals("VARIANT_SNOWBALL",rule.get("adapter").getAsString());assertTrue(rule.get("runtimeCandidateReady").getAsBoolean());assertTrue(rule.get("itemUseSemanticsComplete").getAsBoolean());assertTrue(rule.get("impactSemanticsComplete").getAsBoolean());assertTrue(rule.get("sourceSemanticsComplete").getAsBoolean());assertFalse(rule.get("runtimeImplementationWired").getAsBoolean());
        assertEquals("random.bow",rule.get("launchSound").getAsString());assertEquals(0.5F,rule.get("launchVolume").getAsFloat());assertEquals(0.4F,rule.get("launchPitchNumerator").getAsFloat());assertEquals(0.4F,rule.get("launchPitchRandomScale").getAsFloat());assertEquals(0.8F,rule.get("launchPitchBase").getAsFloat());assertTrue(rule.get("consumeOutsideCreative").getAsBoolean());assertTrue(rule.get("serverAuthoritativeLaunch").getAsBoolean());
        JsonArray variants=rule.getAsJsonArray("variants");assertEquals(2,variants.size());JsonObject plain=variants.get(0).getAsJsonObject(),poison=variants.get(1).getAsJsonObject();assertEquals(0,plain.get("metadata").getAsInt());assertEquals("NONE",plain.get("effect").getAsString());assertEquals(7,poison.get("metadata").getAsInt());assertEquals("POTION",poison.get("effect").getAsString());assertEquals("poison",poison.get("potion").getAsString());assertEquals(30,poison.get("duration").getAsInt());assertEquals(3,poison.get("amplifier").getAsInt());
    }

    @Test void completeRandomTeleportFamilyNormalizesExactRuntimeConstants()throws Exception{
        JsonObject root=run(VariantSnowballLaunchFixture.write(tempDir.resolve("teleport.jar")),tempDir.resolve("teleport-staging"),true);assertEquals(1,root.get("runtimeCandidateFamilies").getAsInt());JsonObject rule=root.getAsJsonArray("rules").get(0).getAsJsonObject();JsonObject teleport=rule.getAsJsonArray("variants").get(1).getAsJsonObject();
        assertEquals(7,teleport.get("metadata").getAsInt());assertEquals("RANDOM_TELEPORT",teleport.get("effect").getAsString());assertEquals(16D,teleport.get("horizontalRandomRadius").getAsDouble());assertEquals(8,teleport.get("verticalRandomBound").getAsInt());assertEquals(-4,teleport.get("verticalRandomOffset").getAsInt());assertEquals(128,teleport.get("portalParticleCount").getAsInt());assertEquals("mob.endermen.portal",teleport.get("portalSound").getAsString());
    }

    @Test void completeImpactWithoutLaunchSidecarFailsClosed()throws Exception{
        JsonObject root=run(VariantSnowballLaunchFixture.write(tempDir.resolve("missing-launch.jar")),tempDir.resolve("missing-launch-staging"),false);assertEquals(0,root.get("runtimeCandidateFamilies").getAsInt());assertEquals(1,root.get("rejectedFamilies").getAsInt());assertEquals("item-use-launch-proof-missing-or-stale",root.getAsJsonArray("rejected").get(0).getAsJsonObject().get("reason").getAsString());
    }

    @Test void incompleteLaunchSemanticsFailClosedEvenWhenImpactIsComplete()throws Exception{
        JsonObject root=run(VariantSnowballLaunchFixture.writeMissingCreativeGuard(tempDir.resolve("incomplete-launch.jar")),tempDir.resolve("incomplete-launch-staging"),true);assertEquals(0,root.get("runtimeCandidateFamilies").getAsInt());assertEquals(1,root.get("rejectedFamilies").getAsInt());assertEquals("item-use-semantics-incomplete",root.getAsJsonArray("rejected").get(0).getAsJsonObject().get("reason").getAsString());
    }

    private JsonObject run(Path source,Path staging,boolean launchProof)throws Exception{
        Files.createDirectories(staging);LegacyModMetadata metadata=new LegacyModMetadata(source.getFileName().toString(),"test",List.of(new LegacyModMetadata.ModEntry("foreign","Foreign","1.0","1.7.10",List.of())));LegacyJarAnalyzer.Analysis jarAnalysis=new LegacyJarAnalyzer.Analysis(source.getFileName().toString(),0,0,false,false,0,0,0,0,Set.of(),Set.of(),Set.of());ConversionContext context=new ConversionContext(source,staging,tempDir.resolve("candidate-"+source.getFileName()),"sha",Files.size(source),metadata,jarAnalysis,new DiagnosticCollector(),"generic-test");
        new GenericContentPass().apply(context);new LegacyVariantSnowballPass().apply(context);if(launchProof)new LegacyVariantSnowballLaunchPass().apply(context);new LegacyVariantSnowballRuntimeCandidatePass().apply(context);
        return JsonParser.parseString(Files.readString(staging.resolve(LegacyVariantSnowballRuntimeCandidatePass.OUTPUT))).getAsJsonObject();
    }
}
