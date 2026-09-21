package dev.yinghuang.legacyforgebridge.convert.pass;

import dev.yinghuang.legacyforgebridge.convert.LegacyCoremodActivationAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.LegacyGeneratedBytecodeLinkageAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/** Safety gate evaluated after semantic/profile conversion passes. */
public final class LegacyBytecodeAuditPass implements ConversionPass {
    private static final String GENERATED_PREFIX = "dev/yinghuang/legacyforgebridge/generated/";

    @Override
    public String id() {
        return "legacy-bytecode-audit";
    }

    @Override
    public void apply(ConversionContext context) throws IOException {
        long generatedClasses = 0;
        long remainingLegacyClasses = 0;
        try (Stream<Path> stream = Files.walk(context.stagingDir())) {
            for (Path path : stream.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".class"))
                    .toList()) {
                String relative = context.stagingDir().relativize(path).toString().replace('\\', '/');
                if (relative.startsWith(GENERATED_PREFIX)) {
                    generatedClasses++;
                } else {
                    remainingLegacyClasses++;
                }
            }
        }

        var generatedAudit = new LegacyGeneratedBytecodeLinkageAnalyzer().analyze(context.stagingDir());
        if (!generatedAudit.complete()) {
            context.diagnostics().error(
                    "LFB-CONVERT-BYTECODE-0004",
                    SupportLevel.UNSUPPORTED,
                    "Generated wrapper bytecode linkage audit was incomplete: " + generatedAudit.diagnostics()
            );
        }
        if (!generatedAudit.findings().isEmpty()) {
            var details = generatedAudit.findings().stream().limit(12)
                    .map(value -> value.generatedClass() + " -> " + value.legacyReferences()).toList();
            String suffix = generatedAudit.findings().size() > details.size()
                    ? " (+" + (generatedAudit.findings().size() - details.size()) + " more)" : "";
            context.diagnostics().error(
                    "LFB-CONVERT-BYTECODE-0003",
                    SupportLevel.UNSUPPORTED,
                    "Generated modern wrappers still link legacy Forge/FML/LaunchWrapper APIs: "
                            + details + suffix + "."
            );
        }

        // Generated wrapper classes are expected to remain, but a source-clean candidate is only
        // loader-safe when those wrappers are also free of direct legacy loader/API linkage.
        if (remainingLegacyClasses == 0) {
            if (generatedAudit.clean()) {
                context.diagnostics().info(
                        "LFB-CONVERT-BYTECODE-0002",
                        SupportLevel.ADAPTED,
                        "No original legacy class files remain after semantic conversion; generated modern Fabric wrapper classes="
                                + generatedClasses + "; legacyApiLinkage=false."
                );
            }
            return;
        }

        var analysis = context.analysis();
        if (analysis.coremodReferenceCount() > 0) {
            var coremod = new LegacyCoremodActivationAnalyzer().analyze(context.sourceJar(), analysis);
            for (String detail : coremod.diagnostics()) {
                context.diagnostics().info(
                        "LFB-CONVERT-COREMOD-0002",
                        SupportLevel.AUTO,
                        detail
                );
            }
            if (coremod.activated()) {
                String transformers = coremod.transformerListProven()
                        ? String.join(", ", coremod.transformerClasses())
                        : "<dynamic/unproven>";
                context.diagnostics().error(
                        "LFB-CONVERT-COREMOD-0001",
                        SupportLevel.UNSUPPORTED,
                        "An active legacy FMLCorePlugin is declared (" + coremod.pluginClass()
                                + "; transformers=" + transformers
                                + "). This candidate is blocked until each active transformation intent is migrated explicitly."
                );
            } else {
                context.diagnostics().info(
                        "LFB-CONVERT-COREMOD-0003",
                        SupportLevel.AUTO,
                        "Coremod marker classes are dormant because the source manifest does not activate an FMLCorePlugin; marker presence alone does not block conversion."
                );
            }
        }

        if (analysis.openglReferenceCount() > 0) {
            context.diagnostics().warning(
                    "LFB-CONVERT-RENDER-0001",
                    SupportLevel.MANUAL_REQUIRED,
                    "Direct legacy OpenGL references remain in staged source class files and require semantic rendering migration."
            );
        }

        if (analysis.forgeReferenceCount() > 0) {
            context.diagnostics().warning(
                    "LFB-CONVERT-FORGE-0001",
                    SupportLevel.RUNTIME_BRIDGE,
                    "Forge/FML bytecode references remain in staged source class files. A conversion pass or runtime adapter must replace them before installation."
            );
        }

        context.diagnostics().warning(
                "LFB-CONVERT-BYTECODE-0001",
                SupportLevel.MANUAL_REQUIRED,
                "Original legacy class files remain in the candidate (" + remainingLegacyClasses
                        + "; generated modern wrappers=" + generatedClasses + "). The generated JAR is not yet a completed Fabric port."
        );
    }
}
