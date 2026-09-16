package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyBlockDropRuntimeRulePassTest {
    @TempDir Path tempDir;

    @Test
    void materializesOnlyFullyProvenNormalSilkAndPerAffectedBlockExplosionRules() throws Exception {
        Path jar = tempDir.resolve("fixture.jar");
        try (JarOutputStream ignored = new JarOutputStream(Files.newOutputStream(jar))) { }
        ConversionContext context = context(jar);

        JsonObject readiness = new JsonObject();
        readiness.addProperty("schemaVersion", 2);
        readiness.addProperty("sourceSha256", "sha");
        JsonArray ready = new JsonArray();
        ready.add(entry("good", true, true, true, true, true));
        ready.add(entry("no_explosion", true, false, true, true, true));
        ready.add(entry("custom_destroy", true, true, false, true, true));
        ready.add(entry("formula", true, true, true, false, true));
        ready.add(entry("event", true, true, true, true, false));
        readiness.add("ready", ready);
        readiness.add("blocked", new JsonArray());

        Path input = tempDir.resolve("staging/" + LegacyBlockDropRuntimeReadinessPass.OUTPUT_PATH);
        Files.createDirectories(input.getParent());
        Files.writeString(input, readiness.toString(), StandardCharsets.UTF_8);

        LegacyBlockDropRuntimeRulePass pass = new LegacyBlockDropRuntimeRulePass();
        assertEquals("legacy-block-drop-runtime-rules", pass.id());
        pass.apply(context);

        JsonObject output = JsonParser.parseString(Files.readString(
                tempDir.resolve("staging/" + LegacyBlockDropRuntimeRulePass.OUTPUT_PATH),
                StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(1, output.get("schemaVersion").getAsInt());
        assertEquals(2, output.get("sourceReadinessSchemaVersion").getAsInt());
        assertTrue(output.get("runtimeImplementationWired").getAsBoolean());
        assertEquals(1, output.get("runtimeRuleCount").getAsInt());
        JsonObject rule = output.getAsJsonArray("rules").get(0).getAsJsonObject();
        assertEquals("fixture:good", rule.get("id").getAsString());
        assertEquals(LegacyBlockDropRuntimeRulePass.MODE, rule.get("mode").getAsString());
        assertEquals("SELF_BLOCK_ITEM", rule.get("dropKind").getAsString());
        assertEquals(1, rule.get("quantity").getAsInt());
        assertEquals(0, rule.get("legacyDamage").getAsInt());
        assertTrue(rule.get("metadataIndependent").getAsBoolean());
        assertEquals("inverse_explosion_radius", rule.get("legacyExplosionChanceMode").getAsString());
        assertTrue(rule.get("normalSilkStaticSelfDropProofComplete").getAsBoolean());
        assertTrue(rule.get("explosionSourceProofComplete").getAsBoolean());
        assertTrue(rule.get("sourceExplosionDestructionOverrideFree").getAsBoolean());
        assertTrue(rule.get("explosionDecayFormulaProofComplete").getAsBoolean());
        assertTrue(rule.get("explosionAffectedSetSourceProofComplete").getAsBoolean());
    }

    private static JsonObject entry(String name, boolean normal, boolean explosion,
                                    boolean destruction, boolean formula, boolean event) {
        JsonObject value = new JsonObject();
        value.addProperty("legacyRegistryName", name);
        value.addProperty("legacyNamespace", "fixture");
        value.addProperty("sourceClass", "foreign/" + name);
        value.addProperty("id", "fixture:" + name);
        value.addProperty("normalSilkStaticSelfDropReady", normal);
        value.addProperty("explosionSourceProofComplete", explosion);
        value.addProperty("sourceExplosionDestructionOverrideFree", destruction);
        value.addProperty("explosionDecayFormulaProofComplete", formula);
        value.addProperty("explosionAffectedSetSourceProofComplete", event);
        value.addProperty("dropKind", "SELF_BLOCK_ITEM");
        value.addProperty("quantity", 1);
        value.addProperty("legacyDamage", 0);
        value.addProperty("metadataIndependent", true);
        return value;
    }

    private ConversionContext context(Path jar) throws Exception {
        Path staging = tempDir.resolve("staging");
        Files.createDirectories(staging);
        LegacyModMetadata metadata = new LegacyModMetadata(
                "fixture.jar", "test",
                List.of(new LegacyModMetadata.ModEntry("fixture", "Fixture", "1.0", "1.7.10", List.of())));
        LegacyJarAnalyzer.Analysis analysis = new LegacyJarAnalyzer.Analysis(
                "fixture.jar", 0, 0, false, false, 0, 0, 0, 0, Set.of(), Set.of(), Set.of());
        return new ConversionContext(jar, staging, tempDir.resolve("candidate.jar"),
                "sha", Files.size(jar), metadata, analysis, new DiagnosticCollector(), "generic-test");
    }
}
