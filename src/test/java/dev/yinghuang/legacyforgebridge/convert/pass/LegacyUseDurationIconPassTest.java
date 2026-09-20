package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class LegacyUseDurationIconPassTest {
    @Test void nativeDefinitionKeepsZeroTickBaseAndExactIntegerThresholds(){
        JsonObject root=LegacyUseDurationIconPass.definitionForTest("demo:item/bow",List.of(
                Map.entry(1,"demo:item/bow_pull_0"),Map.entry(26,"demo:item/bow_pull_1"),Map.entry(40,"demo:item/bow_pull_2")));
        JsonObject condition=root.getAsJsonObject("model");assertEquals("minecraft:condition",condition.get("type").getAsString());assertEquals("minecraft:using_item",condition.get("property").getAsString());
        JsonObject range=condition.getAsJsonObject("on_true");assertEquals("minecraft:range_dispatch",range.get("type").getAsString());assertEquals("minecraft:use_duration",range.get("property").getAsString());
        assertEquals("demo:item/bow",range.getAsJsonObject("fallback").get("model").getAsString());assertEquals(3,range.getAsJsonArray("entries").size());
        assertEquals(1,range.getAsJsonArray("entries").get(0).getAsJsonObject().get("threshold").getAsInt());assertEquals(26,range.getAsJsonArray("entries").get(1).getAsJsonObject().get("threshold").getAsInt());assertEquals(40,range.getAsJsonArray("entries").get(2).getAsJsonObject().get("threshold").getAsInt());
    }
}
