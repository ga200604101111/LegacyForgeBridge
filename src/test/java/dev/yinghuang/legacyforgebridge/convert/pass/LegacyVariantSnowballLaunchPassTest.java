package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.VariantSnowballLaunchFixture;
import dev.yinghuang.legacyforgebridge.convert.VariantSnowballTeleportPresentationFixture;
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

class LegacyVariantSnowballLaunchPassTest {
    @TempDir Path tempDir;

    @Test void completeLaunchShellMaterializesIndependentSourceProof()throws Exception{
        JsonObject root=run(VariantSnowballLaunchFixture.write(tempDir.resolve("launch.jar")),tempDir.resolve("complete"));assertEquals(1,root.get("schemaVersion").getAsInt());assertEquals(1,root.get("launchProofFamilies").getAsInt());assertEquals(1,root.get("launchCompleteFamilies").getAsInt());assertFalse(root.get("runtimeImplementationWired").getAsBoolean());JsonObject rule=root.getAsJsonArray("rules").get(0).getAsJsonObject();assertTrue(rule.get("creativeConsumptionGuardProven").getAsBoolean());assertTrue(rule.get("serverOnlyLaunchGateProven").getAsBoolean());assertTrue(rule.get("legacyBowSoundProven").getAsBoolean());assertTrue(rule.get("metadataProjectileSpawnInsideGateProven").getAsBoolean());assertTrue(rule.get("originalStackReturnProven").getAsBoolean());assertTrue(rule.get("itemUseSemanticsComplete").getAsBoolean());assertFalse(rule.get("runtimeImplementationWired").getAsBoolean());
    }

    @Test void impactCompleteFixtureWithoutLegacyLaunchShellRemainsIncomplete()throws Exception{
        JsonObject root=run(VariantSnowballTeleportPresentationFixture.write(tempDir.resolve("impact-only.jar")),tempDir.resolve("impact-only"));assertEquals(1,root.get("launchProofFamilies").getAsInt());assertEquals(0,root.get("launchCompleteFamilies").getAsInt());JsonObject rule=root.getAsJsonArray("rules").get(0).getAsJsonObject();assertFalse(rule.get("itemUseSemanticsComplete").getAsBoolean());
    }

    private JsonObject run(Path source,Path staging)throws Exception{Files.createDirectories(staging);LegacyModMetadata metadata=new LegacyModMetadata(source.getFileName().toString(),"test",List.of(new LegacyModMetadata.ModEntry("foreign","Foreign","1.0","1.7.10",List.of())));LegacyJarAnalyzer.Analysis jarAnalysis=new LegacyJarAnalyzer.Analysis(source.getFileName().toString(),0,0,false,false,0,0,0,0,Set.of(),Set.of(),Set.of());ConversionContext context=new ConversionContext(source,staging,tempDir.resolve("candidate-"+source.getFileName()),"sha",Files.size(source),metadata,jarAnalysis,new DiagnosticCollector(),"generic-test");new LegacyVariantSnowballLaunchPass().apply(context);return JsonParser.parseString(Files.readString(staging.resolve(LegacyVariantSnowballLaunchPass.OUTPUT))).getAsJsonObject();}
}
