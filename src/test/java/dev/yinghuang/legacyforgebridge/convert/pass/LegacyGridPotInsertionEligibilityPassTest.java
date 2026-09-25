package dev.yinghuang.legacyforgebridge.convert.pass;

import dev.yinghuang.legacyforgebridge.convert.LegacyRegisteredBlockRenderTypeAnalyzer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyGridPotInsertionEligibilityPassTest {
    @Test
    void onlyPortablePositiveLegacyRenderConstantsBecomeModernEligibilityIds() {
        var analysis = new LegacyRegisteredBlockRenderTypeAnalyzer.Analysis(List.of(
                new LegacyRegisteredBlockRenderTypeAnalyzer.Rule("flower", "foreign", "foreign/block/Flower",
                        LegacyRegisteredBlockRenderTypeAnalyzer.RenderIdentity.constant(1)),
                new LegacyRegisteredBlockRenderTypeAnalyzer.Rule("railLike", "foreign", "foreign/block/RailLike",
                        LegacyRegisteredBlockRenderTypeAnalyzer.RenderIdentity.constant(13)),
                new LegacyRegisteredBlockRenderTypeAnalyzer.Rule("special", "foreign", "foreign/block/Special",
                        LegacyRegisteredBlockRenderTypeAnalyzer.RenderIdentity.constant(40)),
                new LegacyRegisteredBlockRenderTypeAnalyzer.Rule("crop", "foreign", "foreign/block/Crop",
                        LegacyRegisteredBlockRenderTypeAnalyzer.RenderIdentity.constant(6)),
                new LegacyRegisteredBlockRenderTypeAnalyzer.Rule("cross", "foreign", "foreign/block/Cross",
                        LegacyRegisteredBlockRenderTypeAnalyzer.RenderIdentity.field("foreign/render/Ids", "cross")),
                new LegacyRegisteredBlockRenderTypeAnalyzer.Rule("missing", "foreign", "foreign/block/Missing",
                        LegacyRegisteredBlockRenderTypeAnalyzer.RenderIdentity.constant(1))
        ), List.of());

        assertEquals(List.of("fixture:flower", "fixture:rail_like", "fixture:special"),
                LegacyGridPotBlockPass.eligibleGeneratedIds(analysis, Map.of(
                        "foreign/block/Flower", "fixture:flower",
                        "foreign/block/RailLike", "fixture:rail_like",
                        "foreign/block/Special", "fixture:special",
                        "foreign/block/Crop", "fixture:crop",
                        "foreign/block/Cross", "fixture:cross"
                )));
    }
}
