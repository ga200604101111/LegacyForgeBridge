package dev.yinghuang.legacyforgebridge.convert.pass;

import dev.yinghuang.legacyforgebridge.convert.LegacyFixedModelProjectileAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyFixedModelProjectilePreflight;
import dev.yinghuang.legacyforgebridge.convert.LegacyProjectileLauncherAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyProjectileFullbright1710Analyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionStatus;
import dev.yinghuang.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Negative executable boundary for source-only fixed cuboid renderer evidence. */
class LegacyProjectileFixedModelPreflightManifestTest {
    @Test void fixedCuboidGeometryRemainsSeparateFromExecutableProjectileRules() {
        var shape=new LegacyFixedModelProjectileAnalyzer.Proof(
                "foreign/client/StaticRenderer", "foreign/client/StaticModel", "foreign:textures/model/shot.png",
                32, 32, .075F, 90F, 1F, 0F, 1F,
                List.of(new LegacyFixedModelProjectileAnalyzer.Cuboid(
                        "piece", 0, 4, -1F, -1F, -1F, 4, 2, 2, -1F, 7F, 3F)));
        var proof=new LegacyFixedModelProjectilePreflight.Analysis(
                List.of(new LegacyFixedModelProjectilePreflight.Candidate(
                        "shot", "foreign/shot/Entity", "foreign/client/StaticRenderer", shape)),
                List.of(new LegacyFixedModelProjectilePreflight.Skipped(
                        "other", "foreign/shot/Other", "not a supported fixed model")));
        var manifest=LegacyProjectilePresentationPass.fixedModelPreflightManifest("sha256fixture", "foreign", proof);
        assertEquals(1, manifest.get("schemaVersion").getAsInt());
        assertEquals("sha256fixture", manifest.get("sourceSha256").getAsString());
        assertEquals("foreign", manifest.get("legacyModId").getAsString());
        assertFalse(manifest.get("runtimeWired").getAsBoolean());
        assertFalse(manifest.get("launcherDataflowProven").getAsBoolean());
        assertFalse(manifest.get("fmlSpawnRuntimeProven").getAsBoolean());
        assertFalse(manifest.get("clientFullbrightProven").getAsBoolean());
        assertFalse(manifest.has("rules"), "Do not publish preflight candidates as active rules");
        assertFalse(manifest.has("runtimeCompleteRules"));
        var candidates=manifest.getAsJsonArray("candidates");
        assertEquals(1, candidates.size());
        assertEquals(1,manifest.get("provenRendererCandidates").getAsInt());
        var candidate=candidates.get(0).getAsJsonObject();
        assertEquals("shot", candidate.get("registryName").getAsString());
        assertEquals("foreign/client/StaticModel",candidate.get("modelClass").getAsString());
        assertTrue(candidate.get("legacyModelBoxFaceProof").getAsBoolean());
        assertEquals(6,candidate.get("modelBoxFaceCount").getAsInt());
        assertEquals(24,candidate.get("modelBoxVertexCount").getAsInt());
        assertEquals(12,candidate.get("modelBoxTriangleCount").getAsInt());
        assertTrue(candidate.get("sourceAxisAngleBakedIntoMesh").getAsBoolean());
        assertFalse(candidate.get("runtimeReady").getAsBoolean());
        assertEquals(4, candidate.getAsJsonArray("cuboids").get(0)
                .getAsJsonObject().get("width").getAsInt());
        assertEquals(1, manifest.getAsJsonArray("skipped").size());
    }

    @Test void provenSourceLauncherIsRecordedButCannotCreateExecutableRules() {
        var shape=new LegacyFixedModelProjectileAnalyzer.Proof(
                "arbitrary/client/Renderer", "arbitrary/client/Model",
                "arbitrary:textures/model/throwable.png", 32,32,.075F,90F,1F,0F,1F,
                List.of(new LegacyFixedModelProjectileAnalyzer.Cuboid(
                        "part",0,4,-1F,-1F,-1F,2,2,2,1F,7F,0F)));
        var launcher=new LegacyProjectileLauncherAnalyzer.Proof(
                "thrower", "arbitrary/items/ChargedCaster", "arbitrary/items/ChargedCaster",
                "onPlayerStoppedUsing",
                "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;I)V",
                LegacyProjectileLauncherAnalyzer.Callback.RELEASE_USE,
                "arbitrary/projectile/Shot");
        var evidence=new LegacyFixedModelProjectilePreflight.Analysis(
                List.of(new LegacyFixedModelProjectilePreflight.Candidate(
                        "shot", "arbitrary/projectile/Shot", "arbitrary/client/Renderer",
                        shape, java.util.Optional.of(launcher))), List.of());
        var manifest=LegacyProjectilePresentationPass.fixedModelPreflightManifest("sha","arbitrary",evidence);
        assertTrue(manifest.get("launcherDataflowProven").getAsBoolean());
        assertEquals(1,manifest.get("sourceLauncherProofCandidateCount").getAsInt());
        var item=manifest.getAsJsonArray("candidates").get(0).getAsJsonObject();
        assertTrue(item.get("sourceLauncherDataflowProven").getAsBoolean());
        assertEquals("thrower",item.get("launcherRegistryName").getAsString());
        assertEquals("RELEASE_USE",item.get("launcherCallbackKind").getAsString());
        assertFalse(item.get("runtimeReady").getAsBoolean());
        assertFalse(manifest.get("runtimeWired").getAsBoolean());
        assertFalse(manifest.has("rules"));
    }

    @Test void sourceProvenFullbrightIsRecordedWithoutBecomingExecutableRenderer() {
        var sourceGeometry=new LegacyFixedModelProjectileAnalyzer.Proof(
                "renamed/client/Renderer","renamed/client/Model",
                "renamed:textures/model/shot.png",32,32,.075F,90F,1F,0F,1F,
                List.of(new LegacyFixedModelProjectileAnalyzer.Cuboid(
                        "segment",0,4,-1,-1,-1,4,2,2,-1,7,3)));
        var lighting=new LegacyProjectileFullbright1710Analyzer.Proof(
                "renamed/Orb",1.0F,0x00F000F0,"getBrightness","getBrightnessForRender");
        var evidence=new LegacyFixedModelProjectilePreflight.Analysis(
                List.of(new LegacyFixedModelProjectilePreflight.Candidate(
                        "orb","renamed/Orb","renamed/client/Renderer",sourceGeometry,
                        java.util.Optional.empty(),java.util.Optional.of(lighting))),List.of());
        var result=LegacyProjectilePresentationPass.fixedModelPreflightManifest("hash","renamed",evidence);
        assertEquals(1,result.get("sourceConstantFullbrightCandidateCount").getAsInt());
        var row=result.getAsJsonArray("candidates").get(0).getAsJsonObject();
        assertTrue(row.get("sourceConstantFullbright1710Proven").getAsBoolean());
        assertEquals(0x00F000F0,row.get("legacyPackedLight").getAsInt());
        assertEquals(1.0F,row.get("legacyBrightness").getAsFloat());
        assertFalse(row.get("runtimeReady").getAsBoolean());
        assertFalse(result.get("runtimeWired").getAsBoolean());
        assertFalse(result.has("rules"));
    }

    @Test void optionalEvidenceDiagnosticsMustNotChangeInstallableConversionStatus() {
        var diagnostics=new DiagnosticCollector();
        diagnostics.info("LFB-CONVERT-PROJECTILE-0004", SupportLevel.AUTO,
                "Preflight geometry observed; runtime not connected");
        diagnostics.warning("LFB-CONVERT-PROJECTILE-0003", SupportLevel.AUTO,
                "Optional preflight could not be completed");
        assertEquals(ConversionStatus.CONVERTED, diagnostics.status());
    }

    @Test void emptyPreflightDoesNotDeclareRuntimeReadiness() {
        var manifest=LegacyProjectilePresentationPass.fixedModelPreflightManifest(
                "empty-sha", "foreign", new LegacyFixedModelProjectilePreflight.Analysis(List.of(),List.of()));
        assertEquals(0,manifest.getAsJsonArray("candidates").size());
        assertFalse(manifest.get("runtimeWired").getAsBoolean());
        assertFalse(manifest.has("rules"));
    }
}
