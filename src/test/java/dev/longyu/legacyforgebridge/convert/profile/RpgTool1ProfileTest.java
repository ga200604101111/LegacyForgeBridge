package dev.longyu.legacyforgebridge.convert.profile;

import dev.longyu.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.longyu.legacyforgebridge.convert.api.ConversionContext;
import dev.longyu.legacyforgebridge.convert.api.ConversionPlan;
import dev.longyu.legacyforgebridge.convert.api.ConversionStatus;
import dev.longyu.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.longyu.legacyforgebridge.convert.api.LegacyModMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RpgTool1ProfileTest {
    @TempDir
    Path tempDir;

    @Test
    void exactCorpusBaselineIsAcceptedAndProfileAddsSemanticPasses() throws Exception {
        RpgTool1Profile profile = new RpgTool1Profile();
        LegacyModMetadata metadata = metadata();
        LegacyJarAnalyzer.Analysis analysis = baselineAnalysis(53);
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        ConversionContext context = context(metadata, analysis, diagnostics, RpgTool1Profile.CORPUS_SHA256);

        assertTrue(profile.matches(metadata, RpgTool1Profile.CORPUS_SHA256));

        ConversionPlan.Builder builder = ConversionPlan.builder(profile.id());
        profile.configure(builder);
        ConversionPlan plan = builder.build();
        assertEquals(
                3,
                plan.passes().size(),
                "RPGTool contributes corpus guard + semantic content + modern resource normalization"
        );

        // This profile test only exercises the corpus guard. The semantic/resource passes have
        // dedicated staging-tree tests because they intentionally read and rewrite resources.
        plan.passes().getFirst().apply(context);
        context.markPassApplied(plan.passes().getFirst().id());

        assertEquals(ConversionStatus.CONVERTED, diagnostics.status());
        assertTrue(diagnostics.snapshot().stream().anyMatch(diagnostic -> diagnostic.ruleId().equals("LFB-CORPUS-RPGTOOL-0003")));
    }

    @Test
    void exactCorpusShaWithChangedAnalyzerBaselineIsBlocked() throws Exception {
        RpgTool1Profile profile = new RpgTool1Profile();
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        ConversionContext context = context(metadata(), baselineAnalysis(52), diagnostics, RpgTool1Profile.CORPUS_SHA256);

        ConversionPlan.Builder builder = ConversionPlan.builder(profile.id());
        profile.configure(builder);
        builder.build().passes().getFirst().apply(context);

        assertEquals(ConversionStatus.BLOCKED, diagnostics.status());
        assertTrue(diagnostics.snapshot().stream().anyMatch(diagnostic -> diagnostic.ruleId().equals("LFB-CORPUS-RPGTOOL-0002")));
    }

    private ConversionContext context(
            LegacyModMetadata metadata,
            LegacyJarAnalyzer.Analysis analysis,
            DiagnosticCollector diagnostics,
            String hash
    ) {
        return new ConversionContext(
                tempDir.resolve("RPGTool1-1.1-1.7.10.jar"),
                tempDir.resolve("staging"),
                tempDir.resolve("candidate.jar"),
                hash,
                14_556_748L,
                metadata,
                analysis,
                diagnostics,
                "rpgtool1-1.7.10"
        );
    }

    private static LegacyModMetadata metadata() {
        return new LegacyModMetadata(
                "RPGTool1-1.1-1.7.10.jar",
                "mcmod.info",
                List.of(new LegacyModMetadata.ModEntry(
                        "rpgtool1",
                        "RPGTool1",
                        "1.0",
                        "1.7.10",
                        List.of()
                ))
        );
    }

    private static LegacyJarAnalyzer.Analysis baselineAnalysis(int classCount) {
        return new LegacyJarAnalyzer.Analysis(
                "RPGTool1-1.1-1.7.10.jar",
                classCount,
                0,
                true,
                true,
                29,
                104,
                0,
                1,
                Set.of("cpw/mods/fml/common/Mod"),
                Set.of(),
                Set.of("org/lwjgl/opengl/GL11")
        );
    }
}
