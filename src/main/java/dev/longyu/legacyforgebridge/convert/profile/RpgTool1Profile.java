package dev.longyu.legacyforgebridge.convert.profile;

import dev.longyu.legacyforgebridge.convert.api.ConversionContext;
import dev.longyu.legacyforgebridge.convert.api.ConversionPass;
import dev.longyu.legacyforgebridge.convert.api.ConversionPlan;
import dev.longyu.legacyforgebridge.convert.api.DiagnosticSeverity;
import dev.longyu.legacyforgebridge.convert.api.LegacyModMetadata;
import dev.longyu.legacyforgebridge.convert.api.LegacyModProfile;
import dev.longyu.legacyforgebridge.convert.api.SupportLevel;
import dev.longyu.legacyforgebridge.convert.pass.RpgTool1ContentPass;

/** Corpus-backed RPGTool1 profile layered on the common conversion engine. */
public final class RpgTool1Profile implements LegacyModProfile {
    public static final String CORPUS_SHA256 = "b82cd54d2d2db576e82ba02ea4e55b4db92174c5814b45aa3ebbe1b44d98961d";

    @Override
    public String id() {
        return "rpgtool1-1.7.10";
    }

    @Override
    public boolean matches(LegacyModMetadata metadata, String sourceHash) {
        return CORPUS_SHA256.equalsIgnoreCase(sourceHash)
                || metadata.containsModId("rpgtool1")
                || metadata.sourceFileName().toLowerCase().startsWith("rpgtool1");
    }

    @Override
    public void configure(ConversionPlan.Builder plan) {
        plan.add(new CorpusGuardPass());
        plan.add(new RpgTool1ContentPass());
    }

    private static final class CorpusGuardPass implements ConversionPass {
        @Override
        public String id() {
            return "profile:rpgtool1-corpus-guard";
        }

        @Override
        public void apply(ConversionContext context) {
            if (!CORPUS_SHA256.equalsIgnoreCase(context.sourceHash())) {
                context.diagnostics().info(
                        "LFB-PROFILE-RPGTOOL-0001",
                        SupportLevel.AUTO,
                        "RPGTool1 profile selected for a non-corpus variant; strict SHA baseline checks were skipped."
                );
                return;
            }

            var analysis = context.analysis();
            boolean matches = analysis.classCount() == 53
                    && analysis.unreadableClasses() == 0
                    && analysis.hasMcmodInfo()
                    && analysis.hasManifest()
                    && analysis.forgeReferenceCount() == 29
                    && analysis.minecraftReferenceCount() == 104
                    && analysis.coremodReferenceCount() == 0
                    && analysis.openglReferenceCount() == 1;

            if (!matches) {
                context.diagnostics().add(
                        "LFB-CORPUS-RPGTOOL-0002",
                        DiagnosticSeverity.ERROR,
                        SupportLevel.UNSUPPORTED,
                        "The exact RPGTool1 corpus SHA no longer matches its recorded analyzer baseline; treat this as an analyzer/conversion regression until reviewed."
                );
                return;
            }

            context.diagnostics().info(
                    "LFB-CORPUS-RPGTOOL-0003",
                    SupportLevel.AUTO,
                    "Exact RPGTool1 corpus SHA and analyzer baseline matched."
            );
        }
    }
}
