package dev.yinghuang.legacyforgebridge.convert.api;

import dev.yinghuang.legacyforgebridge.convert.LegacyJarAnalyzer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ConversionContext {
    private final Path sourceJar;
    private final Path stagingDir;
    private final Path candidateJar;
    private final String sourceHash;
    private final long sourceSize;
    private final LegacyModMetadata metadata;
    private final LegacyJarAnalyzer.Analysis analysis;
    private final DiagnosticCollector diagnostics;
    private final String profileId;
    private final List<String> appliedPasses = new ArrayList<>();
    private final Map<String, Map<String, String>> registryIdentities = new LinkedHashMap<>();

    public ConversionContext(
            Path sourceJar,
            Path stagingDir,
            Path candidateJar,
            String sourceHash,
            long sourceSize,
            LegacyModMetadata metadata,
            LegacyJarAnalyzer.Analysis analysis,
            DiagnosticCollector diagnostics,
            String profileId
    ) {
        this.sourceJar = sourceJar;
        this.stagingDir = stagingDir;
        this.candidateJar = candidateJar;
        this.sourceHash = sourceHash;
        this.sourceSize = sourceSize;
        this.metadata = metadata;
        this.analysis = analysis;
        this.diagnostics = diagnostics;
        this.profileId = profileId;
        registryIdentities.put("items", new LinkedHashMap<>());
        registryIdentities.put("blocks", new LinkedHashMap<>());
        registryIdentities.put("entities", new LinkedHashMap<>());
        registryIdentities.put("guis", new LinkedHashMap<>());
        registryIdentities.put("translations", new LinkedHashMap<>());
    }

    public Path sourceJar() {
        return sourceJar;
    }

    public Path stagingDir() {
        return stagingDir;
    }

    public Path candidateJar() {
        return candidateJar;
    }

    public String sourceHash() {
        return sourceHash;
    }

    public long sourceSize() {
        return sourceSize;
    }

    public LegacyModMetadata metadata() {
        return metadata;
    }

    public LegacyJarAnalyzer.Analysis analysis() {
        return analysis;
    }

    public DiagnosticCollector diagnostics() {
        return diagnostics;
    }

    public String profileId() {
        return profileId;
    }

    public void markPassApplied(String passId) {
        appliedPasses.add(passId);
    }

    public List<String> appliedPasses() {
        return List.copyOf(appliedPasses);
    }

    public void recordRegistryIdentity(String category, String legacyIdentity, String modernIdentity) {
        registryIdentities.computeIfAbsent(category, ignored -> new LinkedHashMap<>())
                .put(legacyIdentity, modernIdentity);
    }

    public Map<String, Map<String, String>> registryIdentities() {
        Map<String, Map<String, String>> copy = new LinkedHashMap<>();
        registryIdentities.forEach((category, identities) -> copy.put(
                category,
                Collections.unmodifiableMap(new LinkedHashMap<>(identities))
        ));
        return Collections.unmodifiableMap(copy);
    }
}
