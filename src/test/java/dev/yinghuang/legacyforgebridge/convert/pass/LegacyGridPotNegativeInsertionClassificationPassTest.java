package dev.yinghuang.legacyforgebridge.convert.pass;

import dev.yinghuang.legacyforgebridge.convert.LegacyRegisteredBlockRenderTypeAnalyzer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyGridPotNegativeInsertionClassificationPassTest {
    @Test
    void onlyKnownNonMatchingRenderIdentitiesEnterNegativeSet() {
        var analysis = new LegacyRegisteredBlockRenderTypeAnalyzer.Analysis(List.of(
                new LegacyRegisteredBlockRenderTypeAnalyzer.Rule("constantPositive", "fixture", "foreign/block/Positive",
                        LegacyRegisteredBlockRenderTypeAnalyzer.RenderIdentity.constant(1)),
                new LegacyRegisteredBlockRenderTypeAnalyzer.Rule("constantNegative", "fixture", "foreign/block/Negative",
                        LegacyRegisteredBlockRenderTypeAnalyzer.RenderIdentity.constant(6)),
                new LegacyRegisteredBlockRenderTypeAnalyzer.Rule("symbolicPositive", "fixture", "foreign/block/SymbolicPositive",
                        LegacyRegisteredBlockRenderTypeAnalyzer.RenderIdentity.field("foreign/render/Ids", "cross")),
                new LegacyRegisteredBlockRenderTypeAnalyzer.Rule("symbolicNegative", "fixture", "foreign/block/SymbolicNegative",
                        LegacyRegisteredBlockRenderTypeAnalyzer.RenderIdentity.field("foreign/render/Ids", "other"))
        ), List.of());
        Map<String,String> ids = Map.of(
                "foreign/block/Positive", "fixture:positive",
                "foreign/block/Negative", "fixture:negative",
                "foreign/block/SymbolicPositive", "fixture:symbolic_positive",
                "foreign/block/SymbolicNegative", "fixture:symbolic_negative",
                "foreign/block/Unknown", "fixture:unknown"
        );
        Set<String> predicateSymbols = Set.of("foreign/render/Ids#cross");

        assertEquals(List.of("fixture:positive", "fixture:symbolic_positive"),
                LegacyGridPotBlockPass.eligibleGeneratedIds(analysis, ids, predicateSymbols));
        assertEquals(List.of("fixture:negative", "fixture:symbolic_negative"),
                LegacyGridPotBlockPass.negativeGeneratedIds(analysis, ids, predicateSymbols));
    }

    @Test
    void constantPositiveRenderTypesNeverLeakIntoNegativeSet() {
        var analysis = new LegacyRegisteredBlockRenderTypeAnalyzer.Analysis(List.of(
                new LegacyRegisteredBlockRenderTypeAnalyzer.Rule("one", "fixture", "A",
                        LegacyRegisteredBlockRenderTypeAnalyzer.RenderIdentity.constant(1)),
                new LegacyRegisteredBlockRenderTypeAnalyzer.Rule("thirteen", "fixture", "B",
                        LegacyRegisteredBlockRenderTypeAnalyzer.RenderIdentity.constant(13)),
                new LegacyRegisteredBlockRenderTypeAnalyzer.Rule("forty", "fixture", "C",
                        LegacyRegisteredBlockRenderTypeAnalyzer.RenderIdentity.constant(40))
        ), List.of());
        assertEquals(List.of(), LegacyGridPotBlockPass.negativeGeneratedIds(analysis,
                Map.of("A", "fixture:a", "B", "fixture:b", "C", "fixture:c"), Set.of()));
    }
}
