package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyCandidateReferenceAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.VariantSnowballRuntimeFixture;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyVariantSnowballRegistrationStripPassTest {
    @TempDir Path tempDir;

    @Test
    void stripsOnlyProofMatchedProjectileRegistrationFromStagedCandidate() throws Exception {
        Path source = VariantSnowballRuntimeFixture.write(tempDir.resolve("variant.jar"));
        Path staging = tempDir.resolve("staging");
        Files.createDirectories(staging);
        ConversionContext context = context(source, staging);

        new CopyLegacyJarPass().apply(context);
        runRuntimeInputs(context);
        new LegacyVariantSnowballRuntimePass().apply(context);
        new LegacyVariantSnowballRegistrationStripPass().apply(context);

        JsonObject root = JsonParser.parseString(Files.readString(
                staging.resolve(LegacyVariantSnowballRegistrationStripPass.OUTPUT),
                StandardCharsets.UTF_8)).getAsJsonObject();

        assertTrue(root.get("projectileRegistrationStripWired").getAsBoolean());
        assertFalse(root.get("itemRegistrationStripWired").getAsBoolean());
        assertFalse(root.get("sourceClassDeletionWired").getAsBoolean());
        assertEquals(1, root.get("projectileRegistrationStripCompleteRules").getAsInt());
        assertEquals(1, root.get("strippedProjectileRegistrationSites").getAsInt());
        assertEquals(0, root.get("blockedProjectileRegistrationStripRules").getAsInt());

        JsonObject rule = root.getAsJsonArray("rules").get(0).getAsJsonObject();
        assertTrue(rule.get("projectileRegistrationStripComplete").getAsBoolean());
        assertTrue(rule.getAsJsonArray("blockers").isEmpty());
        assertEquals("foreign/Bootstrap", rule.get("sourceOwner").getAsString());

        var refs = new LegacyCandidateReferenceAnalyzer().analyze(
                staging, Set.of("foreign/entity/VariantProjectile"));
        assertFalse(refs.forTarget("foreign/entity/VariantProjectile")
                .incomingClassReferences().contains("foreign/Bootstrap"));
        // The launch item still legitimately constructs the legacy projectile in the copied
        // source cohort until the later retirement/registration-strip closure removes it.
        assertTrue(refs.forTarget("foreign/entity/VariantProjectile")
                .incomingClassReferences().contains("foreign/item/VariantBall"));
    }

    private static void runRuntimeInputs(ConversionContext context) throws Exception {
        new GenericContentPass().apply(context);
        new LegacyVariantSnowballPass().apply(context);
        new LegacyVariantSnowballLaunchPass().apply(context);
        new LegacyVariantSnowballRuntimeCandidatePass().apply(context);
        new LegacyEntityDataWatcherPass().apply(context);
    }

    private ConversionContext context(Path source, Path staging) throws Exception {
        LegacyModMetadata metadata = new LegacyModMetadata(
                source.getFileName().toString(),
                "test",
                List.of(new LegacyModMetadata.ModEntry(
                        "foreign", "Foreign", "1.0", "1.7.10", List.of())));
        LegacyJarAnalyzer.Analysis analysis = new LegacyJarAnalyzer.Analysis(
                source.getFileName().toString(),
                0, 0, false, false,
                0, 0, 0, 0,
                Set.of(), Set.of(), Set.of());
        return new ConversionContext(
                source,
                staging,
                tempDir.resolve("candidate.jar"),
                "sha",
                Files.size(source),
                metadata,
                analysis,
                new DiagnosticCollector(),
                "generic-test");
    }
}
