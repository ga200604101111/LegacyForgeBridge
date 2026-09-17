package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.VariantSnowballFixture;
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

    @Test void materializesLookupProofWithoutClaimingMetadataBindingOrRuntime()throws Exception{
        Path source=VariantSnowballFixture.write(tempDir.resolve("variant.jar"));
        Path staging=tempDir.resolve("staging");Files.createDirectories(staging);
        LegacyModMetadata metadata=new LegacyModMetadata("variant.jar","test",List.of(new LegacyModMetadata.ModEntry("foreign","Foreign","1.0","1.7.10",List.of())));
        LegacyJarAnalyzer.Analysis jarAnalysis=new LegacyJarAnalyzer.Analysis("variant.jar",0,0,false,false,0,0,0,0,Set.of(),Set.of(),Set.of());
        ConversionContext context=new ConversionContext(source,staging,tempDir.resolve("candidate.jar"),"sha",Files.size(source),metadata,jarAnalysis,new DiagnosticCollector(),"generic-test");
        new LegacyVariantSnowballPass().apply(context);
        JsonObject root=JsonParser.parseString(Files.readString(staging.resolve(LegacyVariantSnowballPass.OUTPUT))).getAsJsonObject();
        assertEquals(2,root.get("schemaVersion").getAsInt());assertFalse(root.get("runtimeImplementationWired").getAsBoolean());assertFalse(root.get("impactCompilerWired").getAsBoolean());
        assertEquals(1,root.get("proofCompleteFamilies").getAsInt());JsonObject rule=root.getAsJsonArray("rules").get(0).getAsJsonObject();
        assertTrue(rule.get("metadataSelectorLookupProven").getAsBoolean());assertFalse(rule.get("metadataSelectorBindingProven").getAsBoolean());
        assertTrue(rule.get("projectileSelectorStorageProven").getAsBoolean());assertTrue(rule.get("selectorEnumConstantsProven").getAsBoolean());
        assertFalse(rule.get("impactSemanticsComplete").getAsBoolean());assertFalse(rule.get("runtimeImplementationWired").getAsBoolean());assertEquals(2,rule.get("variantCount").getAsInt());
        JsonObject first=rule.getAsJsonArray("variants").get(0).getAsJsonObject();assertEquals(0,first.get("selectorId").getAsInt());assertFalse(first.has("legacyMeta"));assertEquals("UNCOMPILED",first.get("impactEffect").getAsString());
    }
}
