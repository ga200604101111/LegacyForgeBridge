package dev.longyu.legacyforgebridge.convert.pass;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.longyu.legacyforgebridge.convert.Hashing;
import dev.longyu.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.longyu.legacyforgebridge.convert.LegacyRenderFixture;
import dev.longyu.legacyforgebridge.convert.api.ConversionContext;
import dev.longyu.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.longyu.legacyforgebridge.convert.api.LegacyModMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LegacyItemRenderPassTest {
    @TempDir Path temp;
    @Test void emitsNativeContextSelectAndUsesTheNativeSpriteWhenOriginalRejectsInventory() throws Exception {
        var context = prepare(false, true);
        new LegacyItemRenderPass().apply(context);
        JsonObject model = readItem(context).getAsJsonObject("model");
        assertEquals("minecraft:select", model.get("type").getAsString());
        assertEquals("minecraft:display_context", model.get("property").getAsString());
        var cases = model.getAsJsonArray("cases");
        var gui = cases.get(0).getAsJsonObject().getAsJsonObject("model");
        assertEquals("minecraft:model", gui.get("type").getAsString());
        assertEquals("alchemy:item/tool_base", gui.get("model").getAsString());
        var firstPerson = cases.get(2).getAsJsonObject().getAsJsonObject("model").getAsJsonObject("model");
        assertTrue(firstPerson.get("centered").getAsBoolean());
        assertEquals(1F, firstPerson.get("scale").getAsFloat());
        var transforms = firstPerson.getAsJsonArray("transforms");
        assertEquals(8, transforms.size());
        assertEquals(1F, transforms.get(5).getAsJsonObject().getAsJsonArray("values").get(0).getAsFloat());
        assertEquals("scale", transforms.get(6).getAsJsonObject().get("op").getAsString());
        assertTrue(Files.isRegularFile(context.stagingDir().resolve("legacyforgebridge/item-render-analysis.json")));
    }
    @Test void dynamicSourceAndMissingTextureRetainExistingModelWithDiagnostics() throws Exception {
        for (boolean dynamic : List.of(true, false)) {
            var context = prepare(dynamic, dynamic);
            new LegacyItemRenderPass().apply(context);
            assertEquals("minecraft:special", readItem(context).getAsJsonObject("model").get("type").getAsString());
            assertTrue(context.diagnostics().snapshot().stream().anyMatch(d -> d.ruleId().equals(dynamic
                    ? "LFB-CONVERT-ITEM-RENDER-0002" : "LFB-CONVERT-ITEM-RENDER-0005")));
        }
    }
    private ConversionContext prepare(boolean dynamic, boolean texturePresent) throws Exception {
        Path work = Files.createTempDirectory(temp, "fixture-");
        Path source = LegacyRenderFixture.create(work.resolve("Alchemy.jar"), "alchemy", dynamic);
        Path staging = work.resolve("staging");
        Path item = staging.resolve("assets/alchemy/items/tool.json");
        Files.createDirectories(item.getParent());
        Files.writeString(item, """
                {"model":{"type":"minecraft:special","base":"alchemy:item/tool_base","model":{
                  "type":"legacyforgebridge:obj","model":"alchemy:models/tool.obj","texture":"alchemy:textures/tool.png","scale":0.4}}}
                """);
        Path mesh = staging.resolve("assets/alchemy/models/tool.obj");
        Files.createDirectories(mesh.getParent()); Files.writeString(mesh, "v 0 0 0");
        if (texturePresent) {
            Path texture = staging.resolve("assets/alchemy/textures/tool.png");
            Files.createDirectories(texture.getParent()); Files.write(texture, new byte[]{1});
        }
        return new ConversionContext(source, staging, work.resolve("Alchemy-lfb.jar"), Hashing.sha256(source),
                Files.size(source), LegacyModMetadata.read(source), new LegacyJarAnalyzer().analyze(source),
                new DiagnosticCollector(), "synthetic-renderer");
    }
    private JsonObject readItem(ConversionContext context) throws Exception {
        return JsonParser.parseString(Files.readString(context.stagingDir().resolve("assets/alchemy/items/tool.json"))).getAsJsonObject();
    }
}
