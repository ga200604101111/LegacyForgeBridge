package dev.longyu.legacyforgebridge.convert.api;

import java.util.ArrayList;
import java.util.List;

public final class DiagnosticCollector {
    private final List<ConversionDiagnostic> diagnostics = new ArrayList<>();

    public void add(String ruleId, DiagnosticSeverity severity, SupportLevel supportLevel, String message) {
        diagnostics.add(new ConversionDiagnostic(ruleId, severity, supportLevel, message));
    }

    public void info(String ruleId, SupportLevel supportLevel, String message) {
        add(ruleId, DiagnosticSeverity.INFO, supportLevel, message);
    }

    public void warning(String ruleId, SupportLevel supportLevel, String message) {
        add(ruleId, DiagnosticSeverity.WARNING, supportLevel, message);
    }

    public void error(String ruleId, SupportLevel supportLevel, String message) {
        add(ruleId, DiagnosticSeverity.ERROR, supportLevel, message);
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR);
    }

    public ConversionStatus status() {
        boolean partial = false;
        for (ConversionDiagnostic diagnostic : diagnostics) {
            if (diagnostic.severity() == DiagnosticSeverity.ERROR || diagnostic.supportLevel() == SupportLevel.UNSUPPORTED) {
                return ConversionStatus.BLOCKED;
            }
            if (diagnostic.supportLevel() == SupportLevel.MANUAL_REQUIRED
                    || diagnostic.supportLevel() == SupportLevel.RUNTIME_BRIDGE) {
                partial = true;
            }
        }
        return partial ? ConversionStatus.PARTIAL : ConversionStatus.CONVERTED;
    }

    public List<ConversionDiagnostic> snapshot() {
        return List.copyOf(diagnostics);
    }
}
