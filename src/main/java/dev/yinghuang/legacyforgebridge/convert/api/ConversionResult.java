package dev.longyu.legacyforgebridge.convert.api;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public record ConversionResult(
        ConversionStatus status,
        boolean installable,
        Optional<Path> candidateJar,
        Path manifestFile,
        LegacyModMetadata metadata,
        String profileId,
        List<String> appliedPasses,
        List<ConversionDiagnostic> diagnostics
) {
    public ConversionResult {
        candidateJar = candidateJar == null ? Optional.empty() : candidateJar;
        appliedPasses = List.copyOf(appliedPasses);
        diagnostics = List.copyOf(diagnostics);
    }
}
