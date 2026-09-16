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

class LegacyBlockDropRuntimeReadinessPassTest {
    @TempDir Path tempDir;

    @Test
    void admitsOnlyMetadataIndependentNormalAndSilkSelfDropsAndKeepsExplosionMappingGated() throws Exception {
        Path sourceJar = tempDir.resolve("fixture.jar");
        try (JarOutputStream ignored = new JarOutputStream(Files.newOutputStream(sourceJar))) { }
        ConversionContext context = context(sourceJar);

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 6);
        root.addProperty("sourceSha256", "sha");
        JsonArray plans = new JsonArray();
        plans.add(plan("ready", true, true, zeros(), true, true));
        plans.add(plan("metadata", true, true, metadataDamage(), true, true));
        plans.add(plan("harvest", false, true, zeros(), true, true));
        plans.add(plan("silk_mismatch", true, true, zeros(), true, false));
        root.add("plans", plans);

        Path input = tempDir.resolve("staging/" + LegacyBlockDropAnalysisPass.PLAN_PATH);
        Files.createDirectories(input.getParent());
        Files.writeString(input, root.toString(), StandardCharsets.UTF_8);

        LegacyBlockDropRuntimeReadinessPass pass = new LegacyBlockDropRuntimeReadinessPass();
        assertEquals("legacy-block-drop-runtime-readiness", pass.id());
        pass.apply(context);

        JsonObject output = JsonParser.parseString(Files.readString(
                tempDir.resolve("staging/" + LegacyBlockDropRuntimeReadinessPass.OUTPUT_PATH),
                StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(1, output.get("schemaVersion").getAsInt());
        assertEquals(6, output.get("sourcePlanSchemaVersion").getAsInt());
        assertFalse(output.get("lootRuntimeGenerated").getAsBoolean());
        assertFalse(output.get("explosionRuntimeMappingReady").getAsBoolean());
        assertEquals(1, output.get("normalSilkStaticSelfDropReadyPlans").getAsInt());
        assertEquals(3, output.get("blockedPlans").getAsInt());

        JsonObject ready = output.getAsJsonArray("ready").get(0).getAsJsonObject();
        assertEquals("ready", ready.get("legacyRegistryName").getAsString());
        assertEquals("fixture:ready", ready.get("id").getAsString());
        assertTrue(ready.get("normalSilkStaticSelfDropReady").getAsBoolean());
        assertTrue(ready.get("explosionSourceProofComplete").getAsBoolean());
        assertFalse(ready.get("explosionRuntimeMappingReady").getAsBoolean());
        assertFalse(ready.get("lootRuntimeGenerated").getAsBoolean());
        assertEquals("SELF_BLOCK_ITEM", ready.get("dropKind").getAsString());
        assertEquals(1, ready.get("quantity").getAsInt());
        assertEquals(0, ready.get("legacyDamage").getAsInt());
        assertTrue(ready.get("metadataIndependent").getAsBoolean());
        assertEquals(0, ready.getAsJsonArray("reasons").size());

        JsonArray blocked = output.getAsJsonArray("blocked");
        assertTrue(hasReason(blocked, "metadata", "depends on legacy block metadata"));
        assertTrue(hasReason(blocked, "harvest", "harvest eligibility proof incomplete"));
        assertTrue(hasReason(blocked, "silk_mismatch", "silk-touch stack differs"));
    }

    private static JsonObject plan(String name, boolean harvestComplete, boolean explosionComplete,
                                   JsonArray damages, boolean silkComplete, boolean silkEquivalent) {
        JsonObject plan = new JsonObject();
        plan.addProperty("legacyRegistryName", name);
        plan.addProperty("legacyNamespace", "fixture");
        plan.addProperty("sourceClass", "foreign/" + name);
        plan.addProperty("id", "fixture:" + name);
        plan.addProperty("modernIdentityComplete", true);
        plan.addProperty("sourceDropPathOverrideFree", true);
        plan.addProperty("normalDropProofComplete", true);
        plan.addProperty("harvestEligibilityProofComplete", harvestComplete);
        plan.addProperty("explosionDropProofComplete", explosionComplete);
        plan.addProperty("quantity", 1);
        JsonObject item = new JsonObject();
        item.addProperty("kind", "SELF_BLOCK_ITEM");
        item.addProperty("modernId", "fixture:" + name);
        plan.add("item", item);
        plan.add("itemDamageByBlockMeta", damages);
        plan.addProperty("silkTouchProofComplete", silkComplete);
        plan.addProperty("silkTouchEligible", true);
        JsonObject silk = new JsonObject();
        silk.addProperty("kind", "SELF_BLOCK_ITEM");
        silk.addProperty("quantity", 1);
        silk.addProperty("legacyDamage", silkEquivalent ? 0 : 1);
        silk.addProperty("modernId", "fixture:" + name);
        plan.add("silkTouchStack", silk);
        return plan;
    }

    private static JsonArray zeros() {
        JsonArray values = new JsonArray();
        for (int index = 0; index < 16; index++) values.add(0);
        return values;
    }

    private static JsonArray metadataDamage() {
        JsonArray values = zeros();
        values.set(7, new com.google.gson.JsonPrimitive(7));
        return values;
    }

    private static boolean hasReason(JsonArray blocked, String name, String fragment) {
        for (var element : blocked) {
            JsonObject value = element.getAsJsonObject();
            if (!name.equals(value.get("legacyRegistryName").getAsString())) continue;
            for (var reason : value.getAsJsonArray("reasons")) {
                if (reason.getAsString().contains(fragment)) return true;
            }
        }
        return false;
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
