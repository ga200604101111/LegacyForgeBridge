package dev.longyu.legacyforgebridge.convert.pass;

import dev.longyu.legacyforgebridge.convert.api.ConversionContext;
import dev.longyu.legacyforgebridge.convert.api.ConversionPass;
import dev.longyu.legacyforgebridge.convert.api.SupportLevel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/** Safety gate evaluated after semantic/profile conversion passes. */
public final class LegacyBytecodeAuditPass implements ConversionPass {
    @Override
    public String id() {
        return "legacy-bytecode-audit";
    }

    @Override
    public void apply(ConversionContext context) throws IOException {
        long remainingClasses;
        try (Stream<Path> stream = Files.walk(context.stagingDir())) {
            remainingClasses = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".class"))
                    .count();
        }

        // Profile-specific semantic conversion may deliberately remove obsolete Forge classes
        // after translating their content/identity. In that case analyzer references describe the
        // input artifact, not bytecode that will be loaded by Fabric.
        if (remainingClasses == 0) {
            context.diagnostics().info(
                    "LFB-CONVERT-BYTECODE-0002",
                    SupportLevel.ADAPTED,
                    "No legacy class files remain after semantic conversion; obsolete Forge/FML/OpenGL bytecode will not enter the modern Fabric class path."
            );
            return;
        }

        var analysis = context.analysis();
        if (analysis.coremodReferenceCount() > 0) {
            context.diagnostics().error(
                    "LFB-CONVERT-COREMOD-0001",
                    SupportLevel.UNSUPPORTED,
                    "Legacy CoreMod/IClassTransformer markers are present. This candidate is blocked until transformation intent is migrated explicitly."
            );
        }

        if (analysis.openglReferenceCount() > 0) {
            context.diagnostics().warning(
                    "LFB-CONVERT-RENDER-0001",
                    SupportLevel.MANUAL_REQUIRED,
                    "Direct legacy OpenGL references remain in staged class files and require semantic rendering migration."
            );
        }

        if (analysis.forgeReferenceCount() > 0) {
            context.diagnostics().warning(
                    "LFB-CONVERT-FORGE-0001",
                    SupportLevel.RUNTIME_BRIDGE,
                    "Forge/FML bytecode references remain in staged class files. A conversion pass or runtime adapter must replace them before installation."
            );
        }

        context.diagnostics().warning(
                "LFB-CONVERT-BYTECODE-0001",
                SupportLevel.MANUAL_REQUIRED,
                "Legacy class files remain in the candidate (" + remainingClasses + "). The generated JAR is not yet a completed Fabric port."
        );
    }
}
