package dev.longyu.legacyforgebridge.convert.api;

import java.util.Objects;

public record ConversionDiagnostic(
        String ruleId,
        DiagnosticSeverity severity,
        SupportLevel supportLevel,
        String message
) {
    public ConversionDiagnostic {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(supportLevel, "supportLevel");
        Objects.requireNonNull(message, "message");
    }
}
