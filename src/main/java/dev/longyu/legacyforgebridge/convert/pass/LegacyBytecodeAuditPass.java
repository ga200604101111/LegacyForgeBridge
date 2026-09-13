package dev.longyu.legacyforgebridge.convert.pass;

import dev.longyu.legacyforgebridge.convert.api.ConversionContext;
import dev.longyu.legacyforgebridge.convert.api.ConversionPass;
import dev.longyu.legacyforgebridge.convert.api.SupportLevel;

/**
 * Safety gate for the first conversion slice. It deliberately refuses to claim that untouched
 * Forge bytecode is runnable on Fabric just because resources and metadata were converted.
 */
public final class LegacyBytecodeAuditPass implements ConversionPass {
    @Override
    public String id() {
        return "legacy-bytecode-audit";
    }

    @Override
    public void apply(ConversionContext context) {
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
                    "Direct legacy OpenGL references are present and require semantic rendering migration."
            );
        }

        if (analysis.forgeReferenceCount() > 0) {
            context.diagnostics().warning(
                    "LFB-CONVERT-FORGE-0001",
                    SupportLevel.RUNTIME_BRIDGE,
                    "Forge/FML bytecode references remain. A later conversion pass or LegacyForgeBridge runtime adapter must replace them before installation."
            );
        }

        if (analysis.classCount() > 0) {
            context.diagnostics().warning(
                    "LFB-CONVERT-BYTECODE-0001",
                    SupportLevel.MANUAL_REQUIRED,
                    "Legacy class files are retained unchanged in this first conversion slice. The generated JAR is a non-installable conversion candidate, not a completed Fabric port."
            );
        }
    }
}
