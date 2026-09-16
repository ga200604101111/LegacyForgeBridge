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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyBlockDropMetadataMappedRuntimePassTest {
    @TempDir Path tempDir;

    @Test
    void silkDisabledSelfDropCarriesProvenLegacyDamageTableIntoRuntimeRule() throws Exception {
        Path sourceJar = tempDir.resolve("fixture.jar");
        try (JarOutputStream ignored = new JarOutputStream(Files.newOutputStream(sourceJar))) { }
        ConversionContext context = context(sourceJar);

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 6);
        root.addProperty("sourceSha256", "sha");
        root.addProperty("legacyExplosionChanceMode", "inverse_explosion_size_1_7_10");
        JsonArray plans = new JsonArray();
        plans.add(metadataPlan("mapped"));
        root.add("plans", plans);

        Path input = tempDir.resolve("staging/" + LegacyBlockDropAnalysisPass.PLAN_PATH);
        Files.createDirectories(input.getParent());
        Files.writeString(input, root.toString(), StandardCharsets.UTF_8);

        new LegacyBlockDropRuntimeReadinessPass().apply(context);
        JsonObject readiness = JsonParser.parseString(Files.readString(
                tempDir.resolve("staging/" + LegacyBlockDropRuntimeReadinessPass.OUTPUT_PATH),
                StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(2, readiness.get("schemaVersion").getAsInt());
        assertEquals(1, readiness.get("normalSilkSelfDropReadyPlans").getAsInt());
        assertEquals(0, readiness.get("normalSilkStaticSelfDropReadyPlans").getAsInt());
        assertEquals(1, readiness.get("normalSilkMetadataMappedSelfDropReadyPlans").getAsInt());
        assertEquals(0, readiness.get("blockedPlans").getAsInt());

        JsonObject ready = readiness.getAsJsonArray("ready").get(0).getAsJsonObject();
        assertTrue(ready.get("normalSilkSelfDropReady").getAsBoolean());
        assertFalse(ready.get("normalSilkStaticSelfDropReady").getAsBoolean());
        assertTrue(ready.get("normalSilkMetadataMappedSelfDropReady").getAsBoolean());
        assertFalse(ready.get("metadataIndependent").getAsBoolean());
        assertIdentityTable(ready.getAsJsonArray("legacyDamageByBlockMeta"));

        new LegacyBlockDropRuntimeRulePass().apply(context);
        JsonObject rules = JsonParser.parseString(Files.readString(
                tempDir.resolve("staging/" + LegacyBlockDropRuntimeRulePass.OUTPUT_PATH),
                StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(1, rules.get("schemaVersion").getAsInt());
        assertEquals(1, rules.get("runtimeRuleCount").getAsInt());
        JsonObject rule = rules.getAsJsonArray("rules").get(0).getAsJsonObject();
        assertEquals("fixture:mapped", rule.get("id").getAsString());
        assertEquals(LegacyBlockDropRuntimeRulePass.METADATA_MODE, rule.get("mode").getAsString());
        assertTrue(rule.get("normalSilkSelfDropProofComplete").getAsBoolean());
        assertFalse(rule.get("normalSilkStaticSelfDropProofComplete").getAsBoolean());
        assertFalse(rule.get("metadataIndependent").getAsBoolean());
        assertIdentityTable(rule.getAsJsonArray("legacyDamageByBlockMeta"));
    }

    private static JsonObject metadataPlan(String name) {
        JsonObject plan = new JsonObject();
        plan.addProperty("legacyRegistryName", name);
        plan.addProperty("legacyNamespace", "fixture");
        plan.addProperty("sourceClass", "foreign/" + name);
        plan.addProperty("id", "fixture:" + name);
        plan.addProperty("modernIdentityComplete", true);
        plan.addProperty("sourceDropPathOverrideFree", true);
        plan.addProperty("normalDropProofComplete", true);
        plan.addProperty("harvestEligibilityProofComplete", true);
        plan.addProperty("explosionDropProofComplete", true);
        plan.addProperty("sourceExplosionDestructionOverrideFree", true);
        plan.addProperty("quantity", 1);
        JsonObject item = new JsonObject();
        item.addProperty("kind", "SELF_BLOCK_ITEM");
        item.addProperty("modernId", "fixture:" + name);
        plan.add("item", item);
        plan.add("itemDamageByBlockMeta", identityTable());
        plan.addProperty("silkTouchProofComplete", true);
        plan.addProperty("silkTouchEligible", false);
        return plan;
    }

    private static JsonArray identityTable() {
        JsonArray values = new JsonArray();
        for (int meta = 0; meta < 16; meta++) values.add(meta);
        return values;
    }

    private static void assertIdentityTable(JsonArray values) {
        assertEquals(16, values.size());
        for (int meta = 0; meta < 16; meta++) assertEquals(meta, values.get(meta).getAsInt());
    }

    private ConversionContext context(Path sourceJar) throws Exception {
        Path staging = tempDir.resolve("staging");
        Files.createDirectories(staging);
        LegacyModMetadata metadata = new LegacyModMetadata(
                "fixture.jar", "test",
                List.of(new LegacyModMetadata.ModEntry("fixture", "Fixture", "1.0", "1.7.10", List.of())));
        LegacyJarAnalyzer.Analysis analysis = new LegacyJarAnalyzer.Analysis(
                "fixture.jar", 0, 0, false, false, 0, 0, 0, 0, Set.of(), Set.of(), Set.of());
        return new ConversionContext(sourceJar, staging, tempDir.resolve("candidate.jar"),
                "sha", Files.size(sourceJar), metadata, analysis, new DiagnosticCollector(), "generic-test");
    }
}
