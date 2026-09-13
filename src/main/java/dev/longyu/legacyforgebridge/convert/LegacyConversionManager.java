package dev.longyu.legacyforgebridge.convert;

import dev.longyu.legacyforgebridge.BuildInfo;
import dev.longyu.legacyforgebridge.LegacyForgeBridge;
import dev.longyu.legacyforgebridge.convert.api.ConversionResult;
import dev.longyu.legacyforgebridge.convert.api.ConversionStatus;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Properties;

public final class LegacyConversionManager {
    private static final String CACHE_FILE = "index.properties";

    private final LegacyPaths paths = LegacyPaths.resolve();
    private final OldModScanner scanner = new OldModScanner();
    private final LegacyJarAnalyzer analyzer = new LegacyJarAnalyzer();
    private final LegacyConversionEngine conversionEngine = new LegacyConversionEngine(analyzer);

    public void initialize() throws IOException {
        Files.createDirectories(paths.oldModsDir());
        Files.createDirectories(paths.cacheDir());
        Files.createDirectories(paths.reportsDir());
        Files.createDirectories(paths.modsDir());
        Path convertedDir = paths.cacheDir().resolve("converted");
        Path manifestsDir = paths.cacheDir().resolve("manifests");
        Files.createDirectories(convertedDir);
        Files.createDirectories(manifestsDir);

        Properties cache = loadCache();
        List<Path> jars = scanner.scan(paths.oldModsDir());

        if (jars.isEmpty()) {
            LegacyForgeBridge.LOGGER.info("No legacy mod JARs found in {}", paths.oldModsDir());
            return;
        }

        int analyzed = 0;
        int unchanged = 0;
        int converted = 0;
        int partial = 0;
        int blocked = 0;
        int failed = 0;

        for (Path jar : jars) {
            String hash = Hashing.sha256(jar);
            String cacheKey = jar.getFileName().toString();
            String cacheFingerprint = BuildInfo.VERSION + ":" + hash;
            if (cacheFingerprint.equals(cache.getProperty(cacheKey))) {
                unchanged++;
                LegacyForgeBridge.LOGGER.debug("Legacy mod unchanged for converter {}, skipping: {}", BuildInfo.VERSION, jar.getFileName());
                continue;
            }

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

            LegacyForgeBridge.LOGGER.info(
                    "Conversion result for {}: status={}, profile={}, installable={}, candidate={}, manifest={}",
                    jar.getFileName(),
                    result.status(),
                    result.profileId(),
                    result.installable(),
                    result.candidateJar().map(path -> path.getFileName().toString()).orElse("none"),
                    result.manifestFile().getFileName()
            );

            // A failed engine run is retried next launch. Every other result is deterministic for
            // this source hash + converter version, including explicit BLOCKED/PARTIAL outcomes.
            if (result.status() != ConversionStatus.FAILED) {
                cache.setProperty(cacheKey, cacheFingerprint);
            }
        }

        storeCache(cache);
        LegacyForgeBridge.LOGGER.info(
                "Legacy scan/conversion complete: discovered={}, analyzed={}, unchanged={}, converted={}, partial={}, blocked={}, failed={}",
                jars.size(), analyzed, unchanged, converted, partial, blocked, failed
        );
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
}
