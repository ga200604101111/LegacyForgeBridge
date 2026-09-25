package dev.yinghuang.legacyforgebridge.convert;

import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("exact-corpus")
final class MimicShapeExactCorpusTest {
    @TempDir Path temporary;
    @Test void suppliedMimicAndThinPanelCorpusRetainsPresentation() throws Exception {
        String source=System.getProperty("lfb.exactCorpus.jar");
        if(source==null||source.isBlank())source=System.getenv("LFB_EXACT_CORPUS_JAR");
        if(source==null||source.isBlank())throw new IllegalStateException("Set -PlfbExactCorpusJar or LFB_EXACT_CORPUS_JAR");
        MimicShapeExactCorpusChecks.run(Path.of(source),temporary.resolve("presentation"));
    }
}
