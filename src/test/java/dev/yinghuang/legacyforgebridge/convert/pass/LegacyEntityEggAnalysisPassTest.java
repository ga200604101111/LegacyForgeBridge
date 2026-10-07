package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyEntityEggAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class LegacyEntityEggAnalysisPassTest {
    @TempDir Path temp;

    @Test void provenRulesArePersistedWithoutChangingIdentityOrColors()throws Exception{
        ConversionContext context=context();
        var analysis=new LegacyEntityEggAnalyzer.Analysis(List.of(
                new LegacyEntityEggAnalyzer.Rule(
                        "moss_beast","foreign/egg/MossBeast",37,0x123456,0xABCDEF,
                        "foreign/egg/Bootstrap","helper")
        ),List.of());

        LegacyEntityEggAnalysisPass.write(context,analysis);

        Path output=temp.resolve("staging").resolve(LegacyEntityEggAnalysisPass.PATH);
        assertTrue(Files.isRegularFile(output));
        JsonObject root=JsonParser.parseString(Files.readString(output,StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(1,root.getAsJsonArray("rules").size());
        JsonObject rule=root.getAsJsonArray("rules").get(0).getAsJsonObject();
        assertEquals("moss_beast",rule.get("registryName").getAsString());
        assertEquals(37,rule.get("numericId").getAsInt());
        assertEquals(0x123456,rule.get("primaryColor").getAsInt());
        assertEquals(0xABCDEF,rule.get("secondaryColor").getAsInt());
    }

    private ConversionContext context()throws Exception{
        Path staging=temp.resolve("staging");Files.createDirectories(staging);
        LegacyModMetadata metadata=new LegacyModMetadata(
                "fixture.jar","test",
                List.of(new LegacyModMetadata.ModEntry("fixture","Fixture","1","1.7.10",List.of())));
        LegacyJarAnalyzer.Analysis jarAnalysis=new LegacyJarAnalyzer.Analysis(
                "fixture.jar",0,0,false,false,0,0,0,0,Set.of(),Set.of(),Set.of());
        return new ConversionContext(
                temp.resolve("fixture.jar"),staging,temp.resolve("candidate.jar"),
                "source-sha",0L,metadata,jarAnalysis,new DiagnosticCollector(),"generic-test");
    }
}
