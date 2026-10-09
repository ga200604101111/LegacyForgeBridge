package dev.yinghuang.legacyforgebridge.convert.shared;

import dev.yinghuang.legacyforgebridge.convert.LegacyConversionEngine;
import dev.yinghuang.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionResult;
import java.io.IOException;
import java.nio.file.Path;

/** Wraps the existing complete converter; its passes and output ownership stay sequential. */
public final class ScopedConversionRunner {
    private ScopedConversionRunner() { }
    public interface Access {
        ConversionResult lfb$rev312ConvertUncached(Path source, String sha, LegacyJarAnalyzer.Analysis analysis,
                                                   Path converted, Path manifests) throws IOException;
    }
    public static ConversionResult convert(LegacyConversionEngine engine, Path source, String sha,
                                           LegacyJarAnalyzer.Analysis analysis, Path converted, Path manifests) throws IOException {
        try (SharedSourceSession.Scope scope = SharedSourceSession.open(source, sha)) {
            boolean returned = false;
            try {
                ConversionResult result = ((Access)(Object) engine)
                        .lfb$rev312ConvertUncached(source, sha, analysis, converted, manifests);
                returned = true;
                return result;
            } finally {
                try {
                    Path parent = converted.toAbsolutePath().normalize().getParent();
                    if (parent != null) scope.writeReport(parent.resolve("reports").resolve("source-analysis-rev312-" + scope.sourceSha256() + ".json"), returned);
                } catch (IOException | RuntimeException reportFailure) {
                    // Diagnostic IO must not discard or mask the converter's original result/error.
                    System.err.println("[LFB shared-analysis] Unable to write metrics: " + reportFailure);
                }
            }
        }
    }
}
