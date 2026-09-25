package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyClientContentBaselinePassTest {
    @TempDir Path tempDir;

    @Test
    void createsTextureBackedModelsAndAllLegacyMetadataVariants() throws Exception {
        Path staging = tempDir.resolve("staging");
        Files.createDirectories(staging);
        writeContent(staging);
        texture(staging, "legacy", "blocks/exampleblock.png");
        texture(staging, "legacy", "items/exampleitem.png");

        new LegacyClientContentBaselinePass().apply(context(staging));

        JsonObject blockModel = read(staging.resolve("assets/example/models/block/exampleblock.json"));
        assertEquals("legacy:blocks/exampleblock",
                blockModel.getAsJsonObject("textures").get("all").getAsString());
        JsonObject itemModel = read(staging.resolve("assets/example/models/item/exampleitem.json"));
        assertEquals("legacy:items/exampleitem",
                itemModel.getAsJsonObject("textures").get("layer0").getAsString());

        JsonObject blockstate = read(staging.resolve("assets/example/blockstates/exampleblock.json"));
        assertEquals(16, blockstate.getAsJsonObject("variants").size());
        for (int metadata = 0; metadata < 16; metadata++) {
            assertTrue(blockstate.getAsJsonObject("variants").has("legacy_meta=" + metadata));
        }

        JsonObject content = read(staging.resolve(LegacyClientContentBaselinePass.CONTENT));
        assertEquals(1, content.getAsJsonArray("creativeTabs").size());
        assertEquals(2, content.getAsJsonArray("creativeTabs").get(0).getAsJsonObject()
                .getAsJsonArray("items").size());

        JsonObject evidence = read(staging.resolve(LegacyClientContentBaselinePass.OUTPUT));
        assertEquals(2, evidence.get("textureBackedModels").getAsInt());
        assertEquals(0, evidence.get("unresolvedModelCount").getAsInt());
        assertEquals(1, evidence.get("metadataBlockstatesCreatedOrExpanded").getAsInt());
    }

    @Test
    void expandsAnExistingDefaultVariantWithoutChangingItsModel() throws Exception {
        Path staging = tempDir.resolve("staging-existing");
        Files.createDirectories(staging);
        writeContent(staging);
        texture(staging, "legacy", "blocks/exampleblock.png");
        texture(staging, "legacy", "items/exampleitem.png");

        Path statePath = staging.resolve("assets/example/blockstates/exampleblock.json");
        Files.createDirectories(statePath.getParent());
        Files.writeString(statePath,
                "{\"variants\":{\"\":{\"model\":\"example:block/special\",\"y\":90}}}",
                StandardCharsets.UTF_8);

        new LegacyClientContentBaselinePass().apply(context(staging));
        JsonObject variants = read(statePath).getAsJsonObject("variants");
        assertEquals(16, variants.size());
        assertEquals("example:block/special",
                variants.getAsJsonObject("legacy_meta=15").get("model").getAsString());
        assertEquals(90, variants.getAsJsonObject("legacy_meta=15").get("y").getAsInt());
    }

    private ConversionContext context(Path staging) throws Exception {
        Path source = tempDir.resolve("source.jar");
        if (!Files.exists(source)) Files.write(source, new byte[0]);
        LegacyModMetadata metadata = new LegacyModMetadata(
                "source.jar", "test",
                List.of(new LegacyModMetadata.ModEntry(
                        "Example", "Example", "1.0", "1.7.10", List.of())));
        LegacyJarAnalyzer.Analysis analysis = new LegacyJarAnalyzer.Analysis(
                "source.jar", 0, 0, false, false,
                0, 0, 0, 0, Set.of(), Set.of(), Set.of());
        return new ConversionContext(
                source, staging, tempDir.resolve("candidate.jar"), "sha", 0,
                metadata, analysis, new DiagnosticCollector(), "test");
    }

    private static void writeContent(Path staging) throws Exception {
        JsonObject root = new JsonObject();
        root.addProperty("namespace", "example");
        JsonObject block = new JsonObject();
        block.addProperty("id", "example:exampleblock");
        block.addProperty("legacyRegistryName", "exampleBlock");
        block.addProperty("sourceClass", "legacy/BlockExampleBlock");
        JsonArray blocks = new JsonArray();
        blocks.add(block);
        root.add("blocks", blocks);

        JsonObject item = new JsonObject();
        item.addProperty("id", "example:exampleitem");
        item.addProperty("legacyRegistryName", "exampleItem");
        item.addProperty("sourceClass", "legacy/ItemExampleItem");
        JsonArray items = new JsonArray();
        items.add(item);
        root.add("items", items);
        root.add("creativeTabs", new JsonArray());

        Path content = staging.resolve(LegacyClientContentBaselinePass.CONTENT);
        Files.createDirectories(content.getParent());
        Files.writeString(content, root.toString(), StandardCharsets.UTF_8);
    }

    private static void texture(Path staging, String namespace, String relative) throws Exception {
        Path texture = staging.resolve("assets/" + namespace + "/textures/" + relative);
        Files.createDirectories(texture.getParent());
        Files.write(texture, new byte[]{0});
    }

    private static JsonObject read(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
