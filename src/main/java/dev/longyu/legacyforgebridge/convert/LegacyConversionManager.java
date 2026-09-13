package dev.longyu.legacyforgebridge.convert;

import dev.longyu.legacyforgebridge.BuildInfo;
import dev.longyu.legacyforgebridge.LegacyForgeBridge;
import dev.longyu.legacyforgebridge.convert.api.ConversionResult;
import dev.longyu.legacyforgebridge.convert.api.ConversionStatus;
import dev.longyu.legacyforgebridge.convert.runtime.ConvertedModCatalog;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public final class LegacyConversionManager {
    private static final String CACHE_FILE = "index.properties";
    private static final String RESTART_MARKER = "RESTART_REQUIRED.txt";

    private final LegacyPaths paths = LegacyPaths.resolve();
    private final OldModScanner scanner = new OldModScanner();
    private final LegacyJarAnalyzer analyzer = new LegacyJarAnalyzer();
    private final LegacyConversionEngine conversionEngine = new LegacyConversionEngine(analyzer);
    private final ManagedCandidateInstaller installer = new ManagedCandidateInstaller(paths.modsDir(), paths.cacheDir());

    public void initialize() throws IOException {
        Files.createDirectories(paths.oldModsDir());
        Files.createDirectories(paths.cacheDir());
        Files.createDirectories(paths.reportsDir());
        Files.createDirectories(paths.modsDir());
        Path convertedDir = paths.cacheDir().resolve("converted");
        Path manifestsDir = paths.cacheDir().resolve("manifests");
        Files.createDirectories(convertedDir);
        Files.createDirectories(manifestsDir);

        ConversionStateStore state = ConversionStateStore.open(paths.cacheDir());
        Map<String, ConversionStateStore.Snapshot> priorSnapshots = new LinkedHashMap<>();
        for (String source : state.sourceFiles()) {
            ConversionStateStore.Snapshot snapshot = state.snapshot(source);
            if (snapshot != null) {
                priorSnapshots.put(source, snapshot);
            }
        }
        state.beginLaunch(BuildInfo.VERSION);
        state.flush();
        LegacyForgeBridge.LOGGER.info("Legacy conversion state: {}", state.file());

        Properties cache = loadCache();
        List<Path> jars = scanner.scan(paths.oldModsDir());
        Set<String> currentSources = new LinkedHashSet<>();
        jars.forEach(path -> currentSources.add(path.getFileName().toString()));

        int analyzed = 0;
        int unchanged = 0;
        int converted = 0;
        int partial = 0;
        int blocked = 0;
        int failed = 0;
        int staged = 0;
        int loaded = 0;
        boolean restartRequired = false;
        List<String> restartReasons = new ArrayList<>();
        Set<Path> activeManagedJars = new LinkedHashSet<>();

        for (int jarIndex = 0; jarIndex < jars.size(); jarIndex++) {
            Path jar = jars.get(jarIndex);
            String sourceName = jar.getFileName().toString();
            ConversionStateStore.Snapshot prior = priorSnapshots.get(sourceName);
            long sourceSize = Files.size(jar);
            FileTime modifiedTime = Files.getLastModifiedTime(jar);

            progress(state, jarIndex + 1, jars.size(), sourceName, 0, "DISCOVERED", "Legacy JAR discovered in old-mods.");
            progress(state, jarIndex + 1, jars.size(), sourceName, 10, "HASHING", "Calculating source SHA-256 for update detection.");
            String hash = Hashing.sha256(jar);
            String cacheFingerprint = BuildInfo.VERSION + ":" + hash;

            if (prior != null && !prior.sourceSha256().isBlank() && !prior.sourceSha256().equalsIgnoreCase(hash)) {
                LegacyForgeBridge.LOGGER.info(
                        "Legacy source update detected for {}: {} -> {}",
                        sourceName,
                        shortHash(prior.sourceSha256()),
                        shortHash(hash)
                );
            } else if (prior != null && prior.cacheFingerprint() != null
                    && !prior.cacheFingerprint().isBlank()
                    && !prior.cacheFingerprint().equals(cacheFingerprint)
                    && prior.sourceSha256().equalsIgnoreCase(hash)) {
                LegacyForgeBridge.LOGGER.info(
                        "Converter update invalidated cached conversion for {}: converter={}",
                        sourceName,
                        BuildInfo.VERSION
                );
            }

            boolean fingerprintMatches = cacheFingerprint.equals(cache.getProperty(sourceName))
                    && prior != null
                    && cacheFingerprint.equals(prior.cacheFingerprint());

            if (fingerprintMatches) {
                ReuseResult reuse = tryReuseCached(
                        state,
                        prior,
                        jarIndex + 1,
                        jars.size(),
                        sourceName,
                        hash,
                        cacheFingerprint,
                        sourceSize,
                        modifiedTime.toMillis(),
                        activeManagedJars,
                        restartReasons
                );
                if (reuse.handled()) {
                    unchanged++;
                    staged += reuse.staged() ? 1 : 0;
                    loaded += reuse.loaded() ? 1 : 0;
                    restartRequired |= reuse.restartRequired();
                    continue;
                }
            }

            progress(state, jarIndex + 1, jars.size(), sourceName, 35, "ANALYZING", "Inspecting legacy Forge bytecode and resources.");
            LegacyJarAnalyzer.Analysis analysis = analyzer.analyze(jar);
            writeReport(analysis, hash);
            analyzed++;

            LegacyForgeBridge.LOGGER.info(
                    "Analyzed legacy mod {}: classes={}, Forge refs={}, Minecraft refs={}, coremod refs={}, OpenGL refs={}",
                    analysis.fileName(),
                    analysis.classCount(),
                    analysis.forgeReferenceCount(),
                    analysis.minecraftReferenceCount(),
                    analysis.coremodReferenceCount(),
                    analysis.openglReferenceCount()
            );

            progress(state, jarIndex + 1, jars.size(), sourceName, 60, "CONVERTING", "Running deterministic conversion plan.");
            ConversionResult result = conversionEngine.convertAnalyzed(
                    jar,
                    hash,
                    analysis,
                    convertedDir,
                    manifestsDir
            );

            switch (result.status()) {
                case CONVERTED -> converted++;
                case PARTIAL -> partial++;
                case BLOCKED -> blocked++;
                case FAILED -> failed++;
            }

            String fabricId = result.metadata().fabricId();
            String candidatePath = "";
            String candidateSha = "";
            String managedPath = "";
            String managedSha = "";
            boolean loaderSafe = false;
            boolean loadedThisLaunch = ConvertedModCatalog.isConvertedCandidateLoaded(fabricId);
            boolean pendingSwap = false;
            boolean entryRestartRequired = false;
            String phase = result.status().name();
            String message = "Conversion completed with status " + result.status() + ".";
            Path keepManaged = null;

            if (result.candidateJar().isPresent()) {
                Path candidate = result.candidateJar().orElseThrow();
                candidatePath = relative(candidate);
                candidateSha = Hashing.sha256(candidate);
                loaderSafe = installer.isLoaderSafeCandidate(candidate);

                if (loaderSafe) {
                    if (ConvertedModCatalog.isAnyModLoaded(fabricId) && !loadedThisLaunch) {
                        phase = "CONFLICT";
                        message = "A non-LFB Fabric mod already owns id " + fabricId + "; managed candidate was not staged.";
                        LegacyForgeBridge.LOGGER.error(message);
                    } else {
                        progress(state, jarIndex + 1, jars.size(), sourceName, 85, "STAGING", "Staging loader-safe converted candidate for the next launch.");
                        ManagedCandidateInstaller.StageResult stage = installer.stage(candidate, fabricId, loadedThisLaunch);
                        keepManaged = stage.managedJar().toAbsolutePath().normalize();
                        activeManagedJars.add(keepManaged);
                        managedPath = relative(stage.managedJar());
                        managedSha = stage.desiredSha256();
                        pendingSwap = stage.pendingSwap();
                        entryRestartRequired = stage.restartRequired();
                        if (stage.changed()) {
                            staged++;
                        }

                        if (loadedThisLaunch && stage.changed()) {
                            ConvertedModCatalog.markStale(fabricId);
                        }

                        String loadedSourceHash = ConvertedModCatalog.loadedSourceSha256(fabricId).orElse("");
                        boolean exactLoaded = loadedThisLaunch
                                && loadedSourceHash.equalsIgnoreCase(hash)
                                && !stage.changed();
                        if (exactLoaded) {
                            phase = "LOADED";
                            message = "Converted candidate is already loaded and matches the current source.";
                            loaded++;
                            entryRestartRequired = false;
                        } else {
                            phase = pendingSwap ? "UPDATE_STAGED" : "STAGED";
                            message = stage.message();
                            entryRestartRequired = true;
                            restartReasons.add(sourceName + ": " + message);
                        }
                    }
                }
            }

            if (prior != null) {
                boolean retiredNeedsRestart = retirePreviousManagedIfNeeded(
                        prior,
                        keepManaged,
                        activeManagedJars,
                        restartReasons
                );
                entryRestartRequired |= retiredNeedsRestart;
            }

            restartRequired |= entryRestartRequired;
            state.complete(
                    sourceName,
                    new ConversionStateStore.Completion(
                            cacheFingerprint,
                            hash,
                            sourceSize,
                            modifiedTime.toMillis(),
                            result.profileId(),
                            result.status().name(),
                            fabricId,
                            candidatePath,
                            candidateSha,
                            managedPath,
                            managedSha,
                            loaderSafe,
                            loadedThisLaunch && !ConvertedModCatalog.isMarkedStale(fabricId),
                            pendingSwap,
                            entryRestartRequired,
                            phase,
                            message
                    )
            );

            LegacyForgeBridge.LOGGER.info(
                    "Conversion result for {}: status={}, profile={}, loaderSafe={}, candidate={}, managed={}, phase={}",
                    sourceName,
                    result.status(),
                    result.profileId(),
                    loaderSafe,
                    result.candidateJar().map(path -> path.getFileName().toString()).orElse("none"),
                    managedPath.isBlank() ? "none" : managedPath,
                    phase
            );

            // FAILED is retried next launch. All other results are deterministic for
            // source SHA + converter version, including explicit BLOCKED/PARTIAL outcomes.
            if (result.status() != ConversionStatus.FAILED) {
                cache.setProperty(sourceName, cacheFingerprint);
            }
        }

        for (Map.Entry<String, ConversionStateStore.Snapshot> previousEntry : priorSnapshots.entrySet()) {
            String sourceName = previousEntry.getKey();
            if (currentSources.contains(sourceName)) {
                continue;
            }

            ConversionStateStore.Snapshot previous = previousEntry.getValue();
            if (!previous.managedJarPath().isBlank()) {
                Path managed = resolveStatePath(previous.managedJarPath()).toAbsolutePath().normalize();
                if (!activeManagedJars.contains(managed)) {
                    boolean loadedCandidate = !previous.fabricId().isBlank()
                            && ConvertedModCatalog.isConvertedCandidateLoaded(previous.fabricId());
                    if (loadedCandidate) {
                        ConvertedModCatalog.markStale(previous.fabricId());
                    }
                    ManagedCandidateInstaller.RemovalResult removal = installer.remove(managed, loadedCandidate);
                    if (removal.restartRequired()) {
                        restartRequired = true;
                        restartReasons.add(sourceName + ": source was removed; " + removal.message());
                    }
                    LegacyForgeBridge.LOGGER.info(
                            "Removed stale managed conversion for missing old-mod source {}: {}",
                            sourceName,
                            removal.message()
                    );
                }
            }
            state.remove(sourceName);
        }

        for (String cachedSource : new ArrayList<>(cache.stringPropertyNames())) {
            if (!currentSources.contains(cachedSource)) {
                cache.remove(cachedSource);
            }
        }

        storeCache(cache);
        state.setRestartRequired(restartRequired);
        writeRestartMarker(restartRequired, restartReasons);

        if (jars.isEmpty()) {
            LegacyForgeBridge.LOGGER.info("No legacy mod JARs found in {}", paths.oldModsDir());
        }
        LegacyForgeBridge.LOGGER.info(
                "Legacy scan/conversion complete: discovered={}, analyzed={}, unchanged={}, converted={}, partial={}, blocked={}, failed={}, staged={}, loaded={}, restartRequired={}",
                jars.size(), analyzed, unchanged, converted, partial, blocked, failed, staged, loaded, restartRequired
        );

        if (restartRequired) {
            LegacyForgeBridge.LOGGER.warn(
                    "Converted legacy mod set changed. Restart Minecraft once to activate the staged candidate(s). Details: {}",
                    paths.cacheDir().resolve(RESTART_MARKER)
            );
        }
    }

    private ReuseResult tryReuseCached(
            ConversionStateStore state,
            ConversionStateStore.Snapshot prior,
            int index,
            int total,
            String sourceName,
            String sourceHash,
            String cacheFingerprint,
            long sourceSize,
            long sourceModifiedMillis,
            Set<Path> activeManagedJars,
            List<String> restartReasons
    ) throws IOException {
        progress(state, index, total, sourceName, 20, "CACHE_CHECK", "Source and converter fingerprint matched; validating managed output.");

        if (!prior.loaderSafe()) {
            state.complete(
                    sourceName,
                    new ConversionStateStore.Completion(
                            cacheFingerprint,
                            sourceHash,
                            sourceSize,
                            sourceModifiedMillis,
                            prior.profile(),
                            prior.status(),
                            prior.fabricId(),
                            prior.candidatePath(),
                            prior.candidateSha256(),
                            prior.managedJarPath(),
                            prior.managedSha256(),
                            false,
                            false,
                            false,
                            false,
                            "SKIPPED",
                            "Source and converter are unchanged; previous non-loader-safe result was reused without re-analysis."
                    )
            );
            LegacyForgeBridge.LOGGER.info(
                    "Legacy conversion [{}/{}] 100% SKIPPED {} - source SHA and converter version unchanged",
                    index, total, sourceName
            );
            return new ReuseResult(true, false, false, false);
        }

        if (prior.fabricId().isBlank()) {
            return ReuseResult.NOT_HANDLED;
        }

        Path managed = prior.managedJarPath().isBlank()
                ? installer.managedJar(prior.fabricId())
                : resolveStatePath(prior.managedJarPath());
        managed = managed.toAbsolutePath().normalize();
        activeManagedJars.add(managed);

        boolean loadedCandidate = ConvertedModCatalog.isConvertedCandidateLoaded(prior.fabricId());
        String loadedSourceHash = ConvertedModCatalog.loadedSourceSha256(prior.fabricId()).orElse("");
        boolean managedMatches = Files.isRegularFile(managed)
                && !prior.managedSha256().isBlank()
                && prior.managedSha256().equalsIgnoreCase(Hashing.sha256(managed));

        if (loadedCandidate && managedMatches && loadedSourceHash.equalsIgnoreCase(sourceHash)) {
            state.complete(
                    sourceName,
                    new ConversionStateStore.Completion(
                            cacheFingerprint,
                            sourceHash,
                            sourceSize,
                            sourceModifiedMillis,
                            prior.profile(),
                            prior.status(),
                            prior.fabricId(),
                            prior.candidatePath(),
                            prior.candidateSha256(),
                            relative(managed),
                            prior.managedSha256(),
                            true,
                            true,
                            false,
                            false,
                            "LOADED",
                            "Source SHA and converter version are unchanged; loaded managed candidate was reused."
                    )
            );
            LegacyForgeBridge.LOGGER.info(
                    "Legacy conversion [{}/{}] 100% LOADED {} - unchanged; skipping analysis and conversion",
                    index, total, sourceName
            );
            return new ReuseResult(true, false, true, false);
        }

        Path cachedCandidate = prior.candidatePath().isBlank() ? null : resolveStatePath(prior.candidatePath());
        if (cachedCandidate != null
                && Files.isRegularFile(cachedCandidate)
                && installer.isLoaderSafeCandidate(cachedCandidate)) {
            progress(state, index, total, sourceName, 85, "STAGING", "Reusing cached candidate; repairing or activating managed mods/ staging.");
            ManagedCandidateInstaller.StageResult stage = installer.stage(
                    cachedCandidate,
                    prior.fabricId(),
                    loadedCandidate
            );
            if (loadedCandidate && stage.changed()) {
                ConvertedModCatalog.markStale(prior.fabricId());
            }

            boolean restart = stage.restartRequired() || !loadedCandidate || !loadedSourceHash.equalsIgnoreCase(sourceHash);
            if (restart) {
                restartReasons.add(sourceName + ": " + stage.message());
            }
            state.complete(
                    sourceName,
                    new ConversionStateStore.Completion(
                            cacheFingerprint,
                            sourceHash,
                            sourceSize,
                            sourceModifiedMillis,
                            prior.profile(),
                            prior.status(),
                            prior.fabricId(),
                            relative(cachedCandidate),
                            stage.desiredSha256(),
                            relative(stage.managedJar()),
                            stage.desiredSha256(),
                            true,
                            loadedCandidate && !stage.changed() && loadedSourceHash.equalsIgnoreCase(sourceHash),
                            stage.pendingSwap(),
                            restart,
                            restart ? (stage.pendingSwap() ? "UPDATE_STAGED" : "STAGED") : "LOADED",
                            restart ? stage.message() : "Cached managed candidate is already active."
                    )
            );
            LegacyForgeBridge.LOGGER.info(
                    "Legacy conversion [{}/{}] 100% {} {} - {}",
                    index,
                    total,
                    restart ? "STAGED" : "LOADED",
                    sourceName,
                    restart ? stage.message() : "unchanged; skipping analysis and conversion"
            );
            return new ReuseResult(true, stage.changed(), !restart, restart);
        }

        LegacyForgeBridge.LOGGER.info(
                "Cached fingerprint matched for {}, but cached/staged output was missing or invalid; rebuilding deterministically.",
                sourceName
        );
        return ReuseResult.NOT_HANDLED;
    }

    private boolean retirePreviousManagedIfNeeded(
            ConversionStateStore.Snapshot previous,
            Path keepManaged,
            Set<Path> activeManagedJars,
            List<String> restartReasons
    ) throws IOException {
        if (previous.managedJarPath().isBlank()) {
            return false;
        }
        Path oldManaged = resolveStatePath(previous.managedJarPath()).toAbsolutePath().normalize();
        if (keepManaged != null && oldManaged.equals(keepManaged.toAbsolutePath().normalize())) {
            return false;
        }
        if (activeManagedJars.contains(oldManaged)) {
            return false;
        }

        boolean loadedCandidate = !previous.fabricId().isBlank()
                && ConvertedModCatalog.isConvertedCandidateLoaded(previous.fabricId());
        if (loadedCandidate) {
            ConvertedModCatalog.markStale(previous.fabricId());
        }
        ManagedCandidateInstaller.RemovalResult removal = installer.remove(oldManaged, loadedCandidate);
        if (removal.restartRequired()) {
            restartReasons.add(previous.fabricId() + ": previous managed candidate retired; " + removal.message());
        }
        return removal.restartRequired();
    }

    private void progress(
            ConversionStateStore state,
            int index,
            int total,
            String sourceName,
            int percent,
            String phase,
            String message
    ) throws IOException {
        LegacyForgeBridge.LOGGER.info(
                "Legacy conversion [{}/{}] {}% {} {} - {}",
                index,
                total,
                percent,
                phase,
                sourceName,
                message
        );
        state.progress(sourceName, percent, phase, message);
    }

    private Properties loadCache() throws IOException {
        Properties properties = new Properties();
        Path file = paths.cacheDir().resolve(CACHE_FILE);
        if (!Files.isRegularFile(file)) {
            return properties;
        }

        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        }
        return properties;
    }

    private void storeCache(Properties properties) throws IOException {
        Path file = paths.cacheDir().resolve(CACHE_FILE);
        try (OutputStream output = Files.newOutputStream(
                file,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        )) {
            properties.store(output, "LegacyForgeBridge source SHA-256 + converter-version cache");
        }
    }

    private void writeRestartMarker(boolean restartRequired, List<String> reasons) throws IOException {
        Path marker = paths.cacheDir().resolve(RESTART_MARKER);
        if (!restartRequired) {
            Files.deleteIfExists(marker);
            return;
        }

        StringBuilder text = new StringBuilder();
        text.append("LegacyForgeBridge ").append(BuildInfo.VERSION).append('\n');
        text.append("Converted legacy mods changed during this launch. Restart Minecraft once to activate them.\n");
        for (String reason : reasons) {
            text.append(" - ").append(reason).append('\n');
        }
        Files.writeString(
                marker,
                text.toString(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        );
    }

    private void writeReport(LegacyJarAnalyzer.Analysis result, String hash) throws IOException {
        String safeName = result.fileName().replaceAll("[^A-Za-z0-9._-]", "_");
        Path report = paths.reportsDir().resolve(safeName + ".txt");

        StringBuilder text = new StringBuilder();
        text.append("LegacyForgeBridge compatibility analysis\n");
        text.append("file=").append(result.fileName()).append('\n');
        text.append("sha256=").append(hash).append('\n');
        text.append("classes=").append(result.classCount()).append('\n');
        text.append("unreadableClasses=").append(result.unreadableClasses()).append('\n');
        text.append("hasMcmodInfo=").append(result.hasMcmodInfo()).append('\n');
        text.append("likelyForgeMod=").append(result.likelyForgeMod()).append('\n');
        text.append("forgeReferences=").append(result.forgeReferenceCount()).append('\n');
        text.append("minecraftReferences=").append(result.minecraftReferenceCount()).append('\n');
        text.append("coremodReferences=").append(result.coremodReferenceCount()).append('\n');
        text.append("openglReferences=").append(result.openglReferenceCount()).append('\n');
        text.append("manualCoremodReview=").append(result.requiresManualCoremodReview()).append('\n');

        if (!result.coremodReferences().isEmpty()) {
            text.append("\nCoreMod/transformer markers:\n");
            result.coremodReferences().stream().sorted().forEach(value -> text.append(" - ").append(value).append('\n'));
        }
        if (!result.openglReferences().isEmpty()) {
            text.append("\nLegacy OpenGL markers (to be translated semantically, not preserved directly):\n");
            result.openglReferences().stream().sorted().forEach(value -> text.append(" - ").append(value).append('\n'));
        }

        Files.writeString(
                report,
                text.toString(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        );
    }

    private String relative(Path path) {
        if (path == null) {
            return "";
        }
        try {
            return paths.gameDir().toAbsolutePath().normalize()
                    .relativize(path.toAbsolutePath().normalize())
                    .toString()
                    .replace('\\', '/');
        } catch (IllegalArgumentException outsideGameDir) {
            return path.toAbsolutePath().normalize().toString();
        }
    }

    private Path resolveStatePath(String value) {
        Path path = Path.of(value);
        return path.isAbsolute() ? path : paths.gameDir().resolve(path).normalize();
    }

    private static String shortHash(String hash) {
        return hash == null || hash.length() <= 12 ? String.valueOf(hash) : hash.substring(0, 12);
    }

    private record ReuseResult(boolean handled, boolean staged, boolean loaded, boolean restartRequired) {
        private static final ReuseResult NOT_HANDLED = new ReuseResult(false, false, false, false);
    }
}
