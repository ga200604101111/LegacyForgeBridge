package dev.yinghuang.legacyforgebridge.convert.pass;

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

import static org.junit.jupiter.api.Assertions.*;

class LegacyGridPotPresentationRuntimePassTest {
    @TempDir Path tempDir;

    @Test
    void promotesOnlyProvenCoreGridPotPresentationIntoAdaptedRuntimeRule() throws Exception {
        Path staging = tempDir.resolve("staging");
        Files.createDirectories(staging.resolve("legacyforgebridge"));
        Files.writeString(staging.resolve(LegacyGridPotPresentationProofPass.OUTPUT), """
                {"schemaVersion":1,"rules":[{
                  "registryName":"grid","sourceBlockClass":"third/block/Grid","sourceRendererClass":"third/render/GridRenderer",
                  "cellCarrierLegacyRegistryName":"flower_pot","flatInventorySourceProven":true,"inventoryTextureName":"foreign:flower_pot",
                  "storedContentPresentationProven":true,"gridOffsets":[-0.333,0.0,0.333],
                  "contentTranslateY":0.25,"crossedScale":0.75,"cactusHalfWidth":0.125
                }]}
                """, StandardCharsets.UTF_8);
        Files.writeString(staging.resolve(LegacyGridPotBlockPass.OUTPUT), """
                {"schemaVersion":1,"rules":[{
                  "id":"foreign:grid","sourceBlockClass":"third/block/Grid","coreRuntimeComplete":true,
                  "cells":9,"gridWidth":3,"cellHeight":0.375
                }]}
                """, StandardCharsets.UTF_8);

        Files.createDirectories(staging.resolve("assets/foreign/textures/items"));
        Files.write(staging.resolve("assets/foreign/textures/items/flower_pot.png"),new byte[]{1});
        new LegacyGridPotPresentationRuntimePass().apply(context(staging));

        JsonObject root = JsonParser.parseString(Files.readString(
                staging.resolve(LegacyGridPotPresentationRuntimePass.OUTPUT), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(1, root.get("runtimeRules").getAsInt());
        assertTrue(root.get("storedContentPresentationRuntimeWired").getAsBoolean());
        assertEquals("SOURCE_PROVEN_FLAT_ITEM_PLUS_BLOCK_CARRIER", root.get("adaptation").getAsString());
        JsonObject rule = root.getAsJsonArray("rules").get(0).getAsJsonObject();
        assertEquals("foreign:grid", rule.get("id").getAsString());
        assertEquals("NONE", rule.get("itemDisplayContext").getAsString());
        assertTrue(rule.get("boundingBoxCentered").getAsBoolean());
        assertTrue(rule.get("boundingBoxBottomAligned").getAsBoolean());
        assertTrue(rule.get("storedContentPresentationRuntimeWired").getAsBoolean());
        assertEquals("minecraft:flower_pot",rule.get("cellCarrierBlockId").getAsString());
        assertEquals(1.0F/3.0F,rule.get("cellBodyWidth").getAsFloat(),0.0001F);
        assertEquals(0.375F,rule.get("cellBodyHeight").getAsFloat(),0.0001F);
        assertFalse(rule.get("sourceSizedCellGeometry").getAsBoolean());
        assertFalse(rule.get("exactLegacyGeometry").getAsBoolean());
        assertTrue(rule.get("flatInventorySourceProven").getAsBoolean());
        assertTrue(rule.get("flatInventoryModelWired").getAsBoolean());
        assertFalse(rule.get("inventoryUsesSameCellModel").getAsBoolean());

        JsonObject blockModel=JsonParser.parseString(Files.readString(
                staging.resolve("assets/foreign/models/block/grid.json"),StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("minecraft:block/block",blockModel.get("parent").getAsString());
        assertEquals("minecraft:block/flower_pot",blockModel.getAsJsonObject("textures").get("particle").getAsString());
        assertFalse(LegacyPresentationOwnership.owns(staging,staging.resolve("assets/foreign/models/block/grid.json")),
                "GridPot final custom-renderer host must not remain provisional-owned");

        JsonObject itemModel=JsonParser.parseString(Files.readString(
                staging.resolve("assets/foreign/models/item/grid.json"),StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("minecraft:item/generated",itemModel.get("parent").getAsString());
        assertEquals("foreign:items/flower_pot",itemModel.getAsJsonObject("textures").get("layer0").getAsString());
        assertFalse(itemModel.has("elements"));

        JsonObject itemDef=JsonParser.parseString(Files.readString(
                staging.resolve("assets/foreign/items/grid.json"),StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("foreign:item/grid",itemDef.getAsJsonObject("model").get("model").getAsString());
    }

    @Test
    void malformedOrNonCoreRulesStayOutOfRuntimeAdmission() throws Exception {
        Path staging = tempDir.resolve("blocked");
        Files.createDirectories(staging.resolve("legacyforgebridge"));
        Files.writeString(staging.resolve(LegacyGridPotPresentationProofPass.OUTPUT), """
                {"schemaVersion":1,"rules":[{
                  "sourceBlockClass":"third/block/Grid","storedContentPresentationProven":true,
                  "gridOffsets":[-0.333,0.0,0.333],"contentTranslateY":0.25,"crossedScale":0.75
                }]}
                """, StandardCharsets.UTF_8);
        Files.writeString(staging.resolve(LegacyGridPotBlockPass.OUTPUT), """
                {"schemaVersion":1,"rules":[{
                  "id":"foreign:grid","sourceBlockClass":"third/block/Grid","coreRuntimeComplete":false,
                  "cells":9,"gridWidth":3
                }]}
                """, StandardCharsets.UTF_8);
        new LegacyGridPotPresentationRuntimePass().apply(context(staging));
        JsonObject root = JsonParser.parseString(Files.readString(
                staging.resolve(LegacyGridPotPresentationRuntimePass.OUTPUT), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(0, root.get("runtimeRules").getAsInt());
    }

    private ConversionContext context(Path staging) {
        LegacyModMetadata metadata = new LegacyModMetadata("foreign.jar", "test",
                List.of(new LegacyModMetadata.ModEntry("foreign", "Foreign", "1", "1.7.10", List.of())));
        LegacyJarAnalyzer.Analysis analysis = new LegacyJarAnalyzer.Analysis("foreign.jar", 0, 0, false, false,
                0, 0, 0, 0, Set.of(), Set.of(), Set.of());
        return new ConversionContext(tempDir.resolve("foreign.jar"), staging, tempDir.resolve("candidate.jar"),
                "sha", 1L, metadata, analysis, new DiagnosticCollector(), "generic-test");
    }
}
