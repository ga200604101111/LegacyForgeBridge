package dev.yinghuang.legacyforgebridge.convert;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModMetadata;
import dev.yinghuang.legacyforgebridge.convert.pass.CopyLegacyJarPass;
import dev.yinghuang.legacyforgebridge.convert.pass.GenericContentPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyBehaviorPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyEntityDataWatcherPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyVariantSnowballLaunchPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyVariantSnowballConstructionReplacementReadiness;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyVariantSnowballItemRegistrationStripPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyVariantSnowballPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyVariantSnowballRegistrationStripPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyVariantSnowballRetirementPass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyVariantSnowballRetirementReadiness;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyVariantSnowballRuntimeCandidatePass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyVariantSnowballRuntimePass;
import dev.yinghuang.legacyforgebridge.convert.pass.LegacyVariantSnowballSourceAllocationStripPass;
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

        new LegacyVariantSnowballItemRegistrationStripPass().apply(context);
        JsonObject itemStrip = JsonParser.parseString(Files.readString(
                staging.resolve(LegacyVariantSnowballItemRegistrationStripPass.OUTPUT),
                StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject itemStripRule = find(itemStrip, "sourceItemClass",
                "ruby/bamboo/item/ItemDirtySnowball");
        assertTrue(itemStrip.get("itemRegistrationStripWired").getAsBoolean());
        boolean itemStripComplete =
                itemStripRule.get("itemRegistrationStripComplete").getAsBoolean();
        if (!itemStripComplete) {
            assertFalse(itemStripRule.getAsJsonArray("blockers").isEmpty(),
                    itemStripRule.toString());
        }

        new LegacyBehaviorPass().apply(context);
        LegacyVariantSnowballConstructionReplacementReadiness.materialize(context);
        JsonObject construction = JsonParser.parseString(Files.readString(
                staging.resolve(LegacyVariantSnowballConstructionReplacementReadiness.OUTPUT),
                StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject constructionRule = find(construction, "sourceItemClass",
                "ruby/bamboo/item/ItemDirtySnowball");
        new LegacyVariantSnowballSourceAllocationStripPass().apply(context);
        JsonObject allocationStrip = JsonParser.parseString(Files.readString(
                staging.resolve(LegacyVariantSnowballSourceAllocationStripPass.OUTPUT),
                StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject allocationStripRule = find(allocationStrip, "sourceItemClass",
                "ruby/bamboo/item/ItemDirtySnowball");

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
        assertEquals(itemStripComplete,
                readinessRule.get("itemRegistrationStripComplete").getAsBoolean());
        boolean constructorReplacement =
                constructionRule.get("constructorReplacementProven").getAsBoolean();
        assertEquals(constructorReplacement,
                readinessRule.get("constructorReplacementProven").getAsBoolean());
        boolean allocationStripComplete =
                allocationStripRule.get("sourceAllocationStripComplete").getAsBoolean();
        assertEquals(allocationStripComplete,
                readinessRule.get("sourceAllocationStripComplete").getAsBoolean());
        assertFalse(readinessRule.get("sourceClassDeletionAuthorized").getAsBoolean(),
                readinessRule.toString());
        assertFalse(readinessRule.get("sourceClassDeletionAuthorized").getAsBoolean());

        if (!itemStripComplete) {
            boolean itemStripBlocker = false;
            for (JsonElement blocker : readinessRule.getAsJsonArray("blockers")) {
                if ("item-registration-strip-incomplete".equals(blocker.getAsString())) {
                    itemStripBlocker = true;
                    break;
                }
            }
            assertTrue(itemStripBlocker, readinessRule.toString());
        }
        if (!constructorReplacement) {
            boolean constructorBlocker = false;
            for (JsonElement blocker : readinessRule.getAsJsonArray("blockers")) {
                if ("item-constructor-replacement-incomplete".equals(blocker.getAsString())) {
                    constructorBlocker = true;
                    break;
                }
            }
            assertTrue(constructorBlocker, readinessRule.toString());
        }
        boolean allocationStripBlocker = false;
        for (JsonElement blocker : readinessRule.getAsJsonArray("blockers")) {
            if ("item-source-allocation-strip-incomplete".equals(blocker.getAsString())) {
                allocationStripBlocker = true;
                break;
            }
        }
        assertEquals(!allocationStripComplete, allocationStripBlocker,
                readinessRule.toString());
        if (!allocationStripComplete) {
            assertFalse(readinessRule.get("retirementCohortCandidateReady").getAsBoolean(),
                    readinessRule.toString());
        }

        boolean retirementReady =
                readinessRule.get("retirementCohortCandidateReady").getAsBoolean();
        new LegacyVariantSnowballRetirementPass().apply(context);
        JsonObject retirement = JsonParser.parseString(Files.readString(
                staging.resolve(LegacyVariantSnowballRetirementPass.OUTPUT),
                StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject retirementRule = find(retirement, "sourceItemClass",
                "ruby/bamboo/item/ItemDirtySnowball");
        assertEquals(retirementReady,
                retirementRule.get("retirementComplete").getAsBoolean(),
                retirementRule.toString());

        Path itemClass = staging.resolve("ruby/bamboo/item/ItemDirtySnowball.class");
        Path projectileClass = staging.resolve("ruby/bamboo/entity/EntityDirtySnowball.class");
        Path selectorClass = staging.resolve("ruby/bamboo/entity/EnumDirtySnowball.class");
        if (retirementReady) {
            assertFalse(Files.exists(itemClass));
            assertFalse(Files.exists(projectileClass));
            assertFalse(Files.exists(selectorClass));
        } else {
            assertTrue(Files.isRegularFile(itemClass));
            assertTrue(Files.isRegularFile(projectileClass));
            assertTrue(Files.isRegularFile(selectorClass));
        }
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
