package dev.yinghuang.legacyforgebridge.convert;

import com.google.gson.JsonObject;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPlan;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionResult;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionStatus;
import dev.yinghuang.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModMetadata;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModProfile;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;
import dev.yinghuang.legacyforgebridge.convert.manifest.ConversionManifestWriter;
import dev.yinghuang.legacyforgebridge.convert.manifest.FabricMetadataWriter;
import dev.yinghuang.legacyforgebridge.convert.pass.*;
import dev.yinghuang.legacyforgebridge.convert.profile.GenericLegacyModProfile;
import dev.yinghuang.legacyforgebridge.convert.profile.RpgTool1Profile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/** Conversion engine: extract semantics, compile modern mod-owned code, then audit remaining legacy bytecode. */
public final class LegacyConversionEngine {
    private final LegacyJarAnalyzer analyzer;
    private final List<LegacyModProfile> profiles;

    public LegacyConversionEngine() { this(new LegacyJarAnalyzer()); }
    public LegacyConversionEngine(LegacyJarAnalyzer analyzer) { this(analyzer, List.of(new RpgTool1Profile(), new GenericLegacyModProfile())); }
    public LegacyConversionEngine(LegacyJarAnalyzer analyzer, List<LegacyModProfile> profiles) { this.analyzer = analyzer; this.profiles = List.copyOf(profiles); }

    public ConversionResult convert(Path sourceJar, Path convertedDir, Path manifestsDir) throws IOException {
        String hash = Hashing.sha256(sourceJar);
        return convertAnalyzed(sourceJar, hash, analyzer.analyze(sourceJar), convertedDir, manifestsDir);
    }

    public ConversionResult convertAnalyzed(Path sourceJar, String sourceHash, LegacyJarAnalyzer.Analysis analysis, Path convertedDir, Path manifestsDir) throws IOException {
        Files.createDirectories(convertedDir); Files.createDirectories(manifestsDir);
        LegacyModMetadata metadata = LegacyModMetadata.read(sourceJar);
        LegacyModProfile profile = selectProfile(metadata, sourceHash);
        String baseName = sourceBaseName(metadata.sourceFileName());
        Path candidateJar = convertedDir.resolve(baseName + "-lfb.jar");
        Path manifestFile = manifestsDir.resolve(baseName + ".manifest.json");
        Path stagingDir = Files.createTempDirectory(convertedDir, ".lfb-work-");
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        ConversionContext context = new ConversionContext(sourceJar, stagingDir, candidateJar, sourceHash, Files.size(sourceJar), metadata, analysis, diagnostics, profile.id());

        if (analysis.hasMcmodInfo() && !"mcmod.info".equals(metadata.metadataSource())) {
            diagnostics.warning("LFB-CONVERT-METADATA-0001", SupportLevel.MANUAL_REQUIRED, "mcmod.info exists but could not provide a usable logical mod identity; filename fallback metadata was used.");
        } else if (!analysis.hasMcmodInfo()) {
            diagnostics.warning("LFB-CONVERT-METADATA-0002", SupportLevel.MANUAL_REQUIRED, "No mcmod.info metadata was found; filename fallback identity requires validation before installation.");
        }
        if (metadata.hasMultipleLogicalMods()) {
            diagnostics.warning("LFB-CONVERT-MULTIMOD-0001", SupportLevel.MANUAL_REQUIRED, "The source JAR declares multiple logical mod IDs. They are preserved in the manifest, but the current Fabric wrapper exposes only the primary ID.");
        }
        if (metadata.mods().stream().anyMatch(mod -> !mod.dependencies().isEmpty())) {
            diagnostics.warning("LFB-CONVERT-DEPENDENCY-0001", SupportLevel.RUNTIME_BRIDGE, "Legacy dependency declarations are preserved in the conversion manifest but are not yet translated into the dependency graph/Fabric dependency metadata.");
        }

        try {
            profile.inspect(context);
            ConversionPlan.Builder builder = ConversionPlan.builder(profile.id())
                    .add(new CopyLegacyJarPass())
                    .add(new LegacyLanguagePass());
            profile.configure(builder);
            builder.add(new LegacyObjPresentationPass());
            builder.add(new LegacyItemRenderPass());
            builder.add(new NativeItemTagsPass());
            builder.add(new LegacyFoodItemPass());
            builder.add(new LegacyItemBlockBindingPass());
            builder.add(new LegacyPlantBlockPass());
            builder.add(new LegacyPlantLifecyclePass());
            builder.add(new LegacyPlantPresentationPass());
            builder.add(new LegacyPlantDropProofPass());
            builder.add(new LegacyPlantRuntimeProofPass());
            builder.add(new LegacyPlantSoilExtensionPass());
            builder.add(new LegacyPlantSoilProofPass());
            builder.add(new LegacyPlantPlacementProofPass());
            builder.add(new LegacySnowballItemPass());
            builder.add(new LegacyLifecycleAnalysisPass());
            builder.add(new LegacyBlockBehaviorAnalysisPass());
            builder.add(new LegacyBlockMaterialProvenancePass());
            builder.add(new LegacyBlockDropAnalysisPass());
            builder.add(new LegacyBlockHarvestMaterialProofPass());
            builder.add(new LegacyBlockDropRuntimeReadinessPass());
            builder.add(new LegacyBlockDropRuntimeRulePass());
            builder.add(new LegacyBlockPlacementPass());
            builder.add(new LegacyBlockActivationPass());
            builder.add(new LegacyGridPotBlockPass());
            builder.add(new LegacyGridPotPresentationRuntimePass());
            builder.add(new LegacySeatBedPass());
            builder.add(new LegacyInertModelBlockPass());
            builder.add(new LegacyOscillatingModelBlockPass());
            builder.add(new LegacyStorageBlockPass());
            builder.add(new LegacySingleInputProcessorPass());
            builder.add(new LegacySingleInputProcessorPresentationPass());
            builder.add(new LegacySingleInputProcessorBlockConstructionPass());
            builder.add(new LegacySingleInputProcessorTileRegistrationStripPass());
            builder.add(new LegacySingleInputProcessorBlockRegistrationStripPass());
            builder.add(new LegacySingleInputProcessorBlockAllocationStripPass());
            builder.add(new LegacySingleInputProcessorTileConstructionPass());
            builder.add(new LegacySingleInputProcessorGuiHandlerProofPass());
            builder.add(new LegacySingleInputProcessorGuiHandlerStripPass());
            builder.add(new LegacyRecipeAnalysisPass());
            builder.add(new LegacyRecipeMaterializationPass());
            builder.add(new LegacyFuelHandlerPass());
            builder.add(new LegacyEventAnalysisPass());
            builder.add(new LegacyLanguageCleanupPass());
            builder.add(new LegacyEquipmentRenderPass());
            builder.add(new LegacyBehaviorPass());
            builder.add(new LegacyClientContentBaselinePass());
            builder.add(new LegacyIconPresentationPass());
            builder.add(new LegacyItemNamePass());
            builder.add(new LegacyCreativeVariantsPass());
            builder.add(new LegacyBlockGeometryPass());
            builder.add(new LegacyTextureAtlasPass());
            builder.add(new GeneratedSemanticCodePass());
            builder.add(new GeneratedModEntrypointPass());
            builder.add(new LegacyClassDependencyAnalysisPass());
            builder.add(new LegacyClientOnlySourceStripPass());
            builder.add(new LegacyBytecodeAuditPass());
            ConversionPlan plan = builder.build();
            for (var pass : plan.passes()) { pass.apply(context); context.markPassApplied(pass.id()); }

            ConversionStatus status = diagnostics.status();
            boolean installable = status == ConversionStatus.CONVERTED;
            JsonObject manifest = ConversionManifestWriter.create(context, status, installable);
            if (status == ConversionStatus.BLOCKED) {
                Files.deleteIfExists(candidateJar); ConversionManifestWriter.writeSidecar(manifestFile, manifest);
                return result(context, status, false, Optional.empty(), manifestFile);
            }
            FabricMetadataWriter.write(context, status, installable);
            ConversionManifestWriter.writeEmbedded(context, manifest);
            LegacyJarWriter.writeDeterministic(stagingDir, candidateJar);
            ConversionManifestWriter.writeSidecar(manifestFile, manifest);
            return result(context, status, installable, Optional.of(candidateJar), manifestFile);
        } catch (Exception exception) {
            diagnostics.error("LFB-CONVERT-ENGINE-0001", SupportLevel.UNSUPPORTED, "Conversion pass failed: " + exception.getClass().getSimpleName() + ": " + String.valueOf(exception.getMessage()));
            Files.deleteIfExists(candidateJar);
            JsonObject manifest = ConversionManifestWriter.create(context, ConversionStatus.FAILED, false);
            try { ConversionManifestWriter.writeSidecar(manifestFile, manifest); } catch (IOException manifestFailure) { exception.addSuppressed(manifestFailure); }
            return result(context, ConversionStatus.FAILED, false, Optional.empty(), manifestFile);
        } finally { deleteRecursively(stagingDir); }
    }

    private ConversionResult result(ConversionContext context, ConversionStatus status, boolean installable, Optional<Path> candidateJar, Path manifestFile) {
        return new ConversionResult(status, installable, candidateJar, manifestFile, context.metadata(), context.profileId(), context.appliedPasses(), context.diagnostics().snapshot());
    }
    private LegacyModProfile selectProfile(LegacyModMetadata metadata, String sourceHash) {
        return profiles.stream().filter(profile -> profile.matches(metadata, sourceHash)).findFirst().orElseThrow(() -> new IllegalStateException("No conversion profile accepted " + metadata.sourceFileName()));
    }
    static String sourceBaseName(String fileName) {
        String name = Path.of(fileName).getFileName().toString();
        String base = name.toLowerCase(Locale.ROOT).endsWith(".jar") ? name.substring(0, name.length() - 4) : name;
        return base.isBlank() ? "legacy-mod" : base;
    }
    private static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (Stream<Path> stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (IOException ignored) { } });
        } catch (IOException ignored) { }
    }
}
