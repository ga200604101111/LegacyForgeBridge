package dev.yinghuang.legacyforgebridge.convert;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModMetadata;
import dev.yinghuang.legacyforgebridge.convert.pass.CopyLegacyJarPass;
import dev.yinghuang.legacyforgebridge.convert.pass.GenericContentPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyEntityDataWatcherPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyVariantSnowballLaunchPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyVariantSnowballPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyVariantSnowballRegistrationStripPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyVariantSnowballRetirementReadiness;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyVariantSnowballRuntimeCandidatePass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyVariantSnowballRuntimePass;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("exact-corpus")
class BambooVariantSnowballRuntimeClosureExactTest {
    private static final String BAMBOO_SHA256 =
            "bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402";

    @TempDir Path tempDir;

    @Test
    void exactBambooSnowballReachesCompleteRuntimeAndProjectileRegistrationStrip() throws Exception {
        String input = System.getProperty("lfb.exactCorpus.jar");
        assertNotNull(input, "exact Bamboo corpus is required");
        Path source = Path.of(input);
        assertTrue(Files.isRegularFile(source));
        assertEquals(BAMBOO_SHA256, Hashing.sha256(source));

        Path staging = tempDir.resolve("staging");
        Files.createDirectories(staging);
        LegacyModMetadata metadata = LegacyModMetadata.read(source);
        LegacyJarAnalyzer.Analysis analysis = new LegacyJarAnalyzer().analyze(source);
        ConversionContext context = new ConversionContext(
                source,
                staging,
                tempDir.resolve("candidate.jar"),
                BAMBOO_SHA256,
                Files.size(source),
                metadata,
                analysis,
                new DiagnosticCollector(),
                "generic-exact-test");

        new CopyLegacyJarPass().apply(context);
        new GenericContentPass().apply(context);
        new LegacyVariantSnowballPass().apply(context);
        new LegacyVariantSnowballLaunchPass().apply(context);
        new LegacyVariantSnowballRuntimeCandidatePass().apply(context);
        new LegacyEntityDataWatcherPass().apply(context);
        new LegacyVariantSnowballRuntimePass().apply(context);

        JsonObject runtime = JsonParser.parseString(Files.readString(
                staging.resolve(LegacyVariantSnowballRuntimePass.OUTPUT),
                StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject rule = find(runtime, "legacyRegistryName", "snowball");

        assertEquals("ruby/bamboo/item/ItemDirtySnowball",
                rule.get("sourceItemClass").getAsString());
        assertEquals("ruby/bamboo/entity/EntityDirtySnowball",
                rule.get("sourceProjectileClass").getAsString());
        assertEquals("ruby/bamboo/entity/EnumDirtySnowball",
                rule.get("selectorClass").getAsString());
        assertEquals(10, rule.get("variantCount").getAsInt());
        assertTrue(rule.get("runtimeRuleReady").getAsBoolean());
        assertTrue(rule.get("projectileEntityTypeRegistrationWired").getAsBoolean());
        assertTrue(rule.get("projectileItemStackCarrierWired").getAsBoolean());
        assertTrue(rule.get("legacyMetadataSyncWired").getAsBoolean());
        assertTrue(rule.get("itemRuntimeWired").getAsBoolean());
        assertTrue(rule.get("projectileRuntimeWired").getAsBoolean());
        assertTrue(rule.get("projectileImpactRuntimeWired").getAsBoolean());
        assertTrue(rule.get("rendererRuntimeWired").getAsBoolean());
        assertTrue(rule.get("runtimeImplementationWired").getAsBoolean());

        new LegacyVariantSnowballRegistrationStripPass().apply(context);

        JsonObject strip = JsonParser.parseString(Files.readString(
                staging.resolve(LegacyVariantSnowballRegistrationStripPass.OUTPUT),
                StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject stripRule = find(strip, "sourceProjectileClass",
                "ruby/bamboo/entity/EntityDirtySnowball");
        assertTrue(
                stripRule.get("projectileRegistrationStripComplete").getAsBoolean(),
                stripRule.toString());
        assertEquals(1, stripRule.get("strippedProjectileRegistrationSites").getAsInt());
        assertTrue(stripRule.getAsJsonArray("blockers").isEmpty(), stripRule.toString());

        LegacyClassDependencyAnalyzer.Analysis dependencies =
                new LegacyClassDependencyAnalyzer().analyze(source, staging);
        LegacyVariantSnowballRetirementReadiness.materialize(context, dependencies);
        JsonObject readiness = JsonParser.parseString(Files.readString(
                staging.resolve(LegacyVariantSnowballRetirementReadiness.OUTPUT),
                StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject readinessRule = find(readiness, "sourceProjectileClass",
                "ruby/bamboo/entity/EntityDirtySnowball");
        assertTrue(readinessRule.get("modernRuntimeReplacementComplete").getAsBoolean());
        assertTrue(readinessRule.get("projectileRegistrationStripComplete").getAsBoolean());
        assertFalse(readinessRule.get("itemRegistrationStripComplete").getAsBoolean());
        assertFalse(readinessRule.get("retirementCohortCandidateReady").getAsBoolean());
        assertFalse(readinessRule.get("sourceClassDeletionAuthorized").getAsBoolean());

        boolean itemStripBlocker = false;
        for (JsonElement blocker : readinessRule.getAsJsonArray("blockers")) {
            if ("item-registration-strip-not-wired".equals(blocker.getAsString())) {
                itemStripBlocker = true;
                break;
            }
        }
        assertTrue(itemStripBlocker, readinessRule.toString());
    }

    private static JsonObject find(JsonObject root, String key, String expected) {
        for (JsonElement element : root.getAsJsonArray("rules")) {
            if (!element.isJsonObject()) continue;
            JsonObject rule = element.getAsJsonObject();
            if (rule.has(key) && expected.equals(rule.get(key).getAsString())) return rule;
        }
        throw new AssertionError("Missing exact Bamboo rule " + key + "=" + expected
                + " in " + root);
    }
}
