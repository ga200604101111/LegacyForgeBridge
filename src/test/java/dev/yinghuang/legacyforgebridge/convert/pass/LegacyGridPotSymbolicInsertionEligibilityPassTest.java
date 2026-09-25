package dev.yinghuang.legacyforgebridge.convert.pass;

import dev.yinghuang.legacyforgebridge.convert.LegacyRegisteredBlockRenderTypeAnalyzer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyGridPotSymbolicInsertionEligibilityPassTest {
    @Test
    void exactSymbolicRenderFieldEqualityExtendsThePositiveEligibilitySetWithoutResolvingItsRuntimeNumber() {
        var analysis=new LegacyRegisteredBlockRenderTypeAnalyzer.Analysis(List.of(
                new LegacyRegisteredBlockRenderTypeAnalyzer.Rule("cross","foreign","foreign/block/Cross",
                        LegacyRegisteredBlockRenderTypeAnalyzer.RenderIdentity.field("foreign/render/Ids","coordinateCross")),
                new LegacyRegisteredBlockRenderTypeAnalyzer.Rule("other","foreign","foreign/block/Other",
                        LegacyRegisteredBlockRenderTypeAnalyzer.RenderIdentity.field("foreign/render/Ids","other")),
                new LegacyRegisteredBlockRenderTypeAnalyzer.Rule("constant","foreign","foreign/block/Constant",
                        LegacyRegisteredBlockRenderTypeAnalyzer.RenderIdentity.constant(1))
        ),List.of());
        assertEquals(List.of("fixture:constant","fixture:cross"),
                LegacyGridPotBlockPass.eligibleGeneratedIds(analysis,Map.of(
                        "foreign/block/Cross","fixture:cross","foreign/block/Other","fixture:other","foreign/block/Constant","fixture:constant"),
                        Set.of("foreign/render/Ids#coordinateCross")));
    }
}
