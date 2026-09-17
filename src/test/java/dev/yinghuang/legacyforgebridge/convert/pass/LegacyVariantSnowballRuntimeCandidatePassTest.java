package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.VariantSnowballSelectorEffectFixture;
import dev.yinghuang.legacyforgebridge.convert.VariantSnowballTeleportPresentationFixture;
import dev.yinghuang.legacyforgebridge.convert.VariantSnowballTeleportSafetyFixture;
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

    @Test void completePotionFamilyNormalizesToRuntimeCandidateWithoutRuntimeClaim()throws Exception{
        JsonObject root=run(VariantSnowballSelectorEffectFixture.write(tempDir.resolve("potion.jar")),tempDir.resolve("potion-staging"));
        assertEquals(1,root.get("schemaVersion").getAsInt());assertEquals(1,root.get("runtimeCandidateFamilies").getAsInt());assertEquals(0,root.get("rejectedFamilies").getAsInt());assertFalse(root.get("runtimeImplementationWired").getAsBoolean());assertFalse(root.get("projectileRuntimeWired").getAsBoolean());
        JsonObject rule=root.getAsJsonArray("rules").get(0).getAsJsonObject();assertEquals("test:variant_ball",rule.get("id").getAsString());assertEquals("test:variant_ball_projectile",rule.get("projectileId").getAsString());assertEquals("VARIANT_SNOWBALL",rule.get("adapter").getAsString());assertTrue(rule.get("runtimeCandidateReady").getAsBoolean());assertFalse(rule.get("runtimeImplementationWired").getAsBoolean());
        JsonArray variants=rule.getAsJsonArray("variants");assertEquals(2,variants.size());JsonObject plain=variants.get(0).getAsJsonObject(),poison=variants.get(1).getAsJsonObject();assertEquals(0,plain.get("metadata").getAsInt());assertEquals(2,plain.get("baseDamage").getAsInt());assertEquals("NONE",plain.get("effect").getAsString());assertEquals(7,poison.get("metadata").getAsInt());assertEquals("POTION",poison.get("effect").getAsString());assertEquals("poison",poison.get("potion").getAsString());assertEquals(30,poison.get("duration").getAsInt());assertEquals(3,poison.get("amplifier").getAsInt());
    }

    @Test void completeRandomTeleportFamilyNormalizesExactRuntimeConstants()throws Exception{
        JsonObject root=run(VariantSnowballTeleportPresentationFixture.write(tempDir.resolve("teleport.jar")),tempDir.resolve("teleport-staging"));assertEquals(1,root.get("runtimeCandidateFamilies").getAsInt());JsonObject rule=root.getAsJsonArray("rules").get(0).getAsJsonObject();JsonObject teleport=rule.getAsJsonArray("variants").get(1).getAsJsonObject();
        assertEquals(7,teleport.get("metadata").getAsInt());assertEquals("RANDOM_TELEPORT",teleport.get("effect").getAsString());assertEquals(16D,teleport.get("horizontalRandomRadius").getAsDouble());assertEquals(8,teleport.get("verticalRandomBound").getAsInt());assertEquals(-4,teleport.get("verticalRandomOffset").getAsInt());assertEquals(128,teleport.get("portalParticleCount").getAsInt());assertEquals("mob.endermen.portal",teleport.get("portalSound").getAsString());
    }

    @Test void gameplaySafetyWithoutPortalPresentationFailsClosed()throws Exception{
        JsonObject root=run(VariantSnowballTeleportSafetyFixture.write(tempDir.resolve("incomplete.jar")),tempDir.resolve("incomplete-staging"));assertEquals(0,root.get("runtimeCandidateFamilies").getAsInt());assertEquals(1,root.get("rejectedFamilies").getAsInt());assertEquals("source-impact-semantics-incomplete",root.getAsJsonArray("rejected").get(0).getAsJsonObject().get("reason").getAsString());
    }

    private JsonObject run(Path source,Path staging)throws Exception{
        Files.createDirectories(staging);LegacyModMetadata metadata=new LegacyModMetadata(source.getFileName().toString(),"test",List.of(new LegacyModMetadata.ModEntry("foreign","Foreign","1.0","1.7.10",List.of())));LegacyJarAnalyzer.Analysis jarAnalysis=new LegacyJarAnalyzer.Analysis(source.getFileName().toString(),0,0,false,false,0,0,0,0,Set.of(),Set.of(),Set.of());ConversionContext context=new ConversionContext(source,staging,tempDir.resolve("candidate-"+source.getFileName()),"sha",Files.size(source),metadata,jarAnalysis,new DiagnosticCollector(),"generic-test");
        new GenericContentPass().apply(context);new LegacyVariantSnowballPass().apply(context);new LegacyVariantSnowballRuntimeCandidatePass().apply(context);
        return JsonParser.parseString(Files.readString(staging.resolve(LegacyVariantSnowballRuntimeCandidatePass.OUTPUT))).getAsJsonObject();
    }
}
