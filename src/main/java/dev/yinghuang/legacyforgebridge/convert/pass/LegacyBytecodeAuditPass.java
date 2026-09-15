package dev.yinghuang.legacyforgebridge.convert.pass;

import dev.yinghuang.legacyforgebridge.convert.LegacyCoremodActivationAnalyzer;
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

        // Generated wrapper classes are modern bytecode produced by LFB itself and are expected to
        // remain. The safety question is whether any original Forge 1.7.10 class survived.
        if (remainingLegacyClasses == 0) {
            context.diagnostics().info(
                    "LFB-CONVERT-BYTECODE-0002",
                    SupportLevel.ADAPTED,
                    "No original legacy class files remain after semantic conversion; generated modern Fabric wrapper classes="
                            + generatedClasses + "."
            );
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
