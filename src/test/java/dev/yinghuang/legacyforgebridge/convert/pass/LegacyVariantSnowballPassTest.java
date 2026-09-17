package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.VariantSnowballFixture;
import dev.yinghuang.legacyforgebridge.convert.VariantSnowballImpactFixture;
import dev.yinghuang.legacyforgebridge.convert.VariantSnowballMapBindingFixture;
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
        assertEquals(4,root.get("schemaVersion").getAsInt());assertFalse(root.get("runtimeImplementationWired").getAsBoolean());assertFalse(root.get("impactCompilerWired").getAsBoolean());
        assertEquals(1,root.get("proofCompleteFamilies").getAsInt());assertEquals(0,root.get("metadataBindingFamilies").getAsInt());assertEquals(0,root.get("commonImpactFamilies").getAsInt());
        JsonObject rule=root.getAsJsonArray("rules").get(0).getAsJsonObject();assertTrue(rule.get("metadataSelectorLookupProven").getAsBoolean());assertFalse(rule.get("metadataSelectorBindingProven").getAsBoolean());
        assertTrue(rule.get("projectileSelectorStorageProven").getAsBoolean());assertTrue(rule.get("selectorEnumConstantsProven").getAsBoolean());assertFalse(rule.get("commonImpactSemanticsProven").getAsBoolean());
        assertFalse(rule.get("selectorSpecificImpactSemanticsComplete").getAsBoolean());assertFalse(rule.get("impactSemanticsComplete").getAsBoolean());assertFalse(rule.get("runtimeImplementationWired").getAsBoolean());
        JsonObject first=rule.getAsJsonArray("variants").get(0).getAsJsonObject();assertEquals(0,first.get("selectorId").getAsInt());assertFalse(first.has("legacyMeta"));assertEquals("UNCOMPILED",first.get("impactEffect").getAsString());
    }

    @Test void exposesLegacyMetadataOnlyAfterCanonicalMapBindingProof()throws Exception{
        JsonObject root=run(VariantSnowballMapBindingFixture.write(tempDir.resolve("bound.jar")),tempDir.resolve("staging-bound"));
        assertEquals(1,root.get("metadataBindingFamilies").getAsInt());assertEquals(0,root.get("commonImpactFamilies").getAsInt());JsonObject rule=root.getAsJsonArray("rules").get(0).getAsJsonObject();
        assertTrue(rule.get("metadataSelectorBindingProven").getAsBoolean());assertFalse(rule.has("metadataSelectorBindingBlocker"));assertFalse(rule.get("commonImpactSemanticsProven").getAsBoolean());
        JsonObject first=rule.getAsJsonArray("variants").get(0).getAsJsonObject();assertEquals(0,first.get("selectorId").getAsInt());assertEquals(0,first.get("legacyMeta").getAsInt());
    }

    @Test void materializesCommonImpactShellWithoutOpeningSelectorSwitchCompiler()throws Exception{
        JsonObject root=run(VariantSnowballImpactFixture.write(tempDir.resolve("impact.jar")),tempDir.resolve("staging-impact"));
        assertEquals(1,root.get("metadataBindingFamilies").getAsInt());assertEquals(1,root.get("commonImpactFamilies").getAsInt());
        JsonObject rule=root.getAsJsonArray("rules").get(0).getAsJsonObject();assertTrue(rule.get("selectorNullImpactGuardProven").getAsBoolean());assertTrue(rule.get("selectorBaseDamageAttackProven").getAsBoolean());
        assertTrue(rule.get("snowballPoofLoopProven").getAsBoolean());assertTrue(rule.get("serverTerminationProven").getAsBoolean());assertTrue(rule.get("commonImpactSemanticsProven").getAsBoolean());
        assertFalse(rule.get("selectorSpecificImpactSemanticsComplete").getAsBoolean());assertFalse(rule.get("impactSemanticsComplete").getAsBoolean());assertFalse(root.get("impactCompilerWired").getAsBoolean());
    }

    private JsonObject run(Path source,Path staging)throws Exception{
        Files.createDirectories(staging);
        LegacyModMetadata metadata=new LegacyModMetadata(source.getFileName().toString(),"test",List.of(new LegacyModMetadata.ModEntry("foreign","Foreign","1.0","1.7.10",List.of())));
        LegacyJarAnalyzer.Analysis jarAnalysis=new LegacyJarAnalyzer.Analysis(source.getFileName().toString(),0,0,false,false,0,0,0,0,Set.of(),Set.of(),Set.of());
        ConversionContext context=new ConversionContext(source,staging,tempDir.resolve("candidate-"+source.getFileName()),"sha",Files.size(source),metadata,jarAnalysis,new DiagnosticCollector(),"generic-test");
        new LegacyVariantSnowballPass().apply(context);
        return JsonParser.parseString(Files.readString(staging.resolve(LegacyVariantSnowballPass.OUTPUT))).getAsJsonObject();
    }
}
