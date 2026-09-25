package dev.yinghuang.legacyforgebridge.convert;

import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LegacyFuelRuleMaterializerTest {
    @TempDir Path tempDir;

    @Test void modMetadataAndVanillaDfuDamageBecomeCurrentFuelPredicates() {
        ConversionContext context = context();
        context.recordRegistryIdentity("items", "fixture:straw", "fixture:straw");
        context.recordRegistryIdentity("items", "fixture:decor", "fixture:decor");

        var any = LegacyFuelRuleMaterializer.materialize(
                rule(modItem("straw"), null, 30), context).orElseThrow();
        assertEquals("fixture:straw", any.id());
        assertEquals(LegacyFuelRuleMaterializer.ANY, any.legacyMeta());
        assertEquals(LegacyFuelRuleMaterializer.ANY, any.damage());
        assertEquals(30, any.burnTicks());

        var exact = LegacyFuelRuleMaterializer.materialize(
                rule(modBlock("decor"), 4, 270), context).orElseThrow();
        assertEquals("fixture:decor", exact.id());
        assertEquals(4, exact.legacyMeta());
        assertEquals(LegacyFuelRuleMaterializer.ANY, exact.damage());

        var wool = LegacyFuelRuleMaterializer.materialize(
                rule(vanillaBlock("wool"), 11, 100), context).orElseThrow();
        assertEquals("minecraft:blue_wool", wool.id());
        assertEquals(LegacyFuelRuleMaterializer.ANY, wool.legacyMeta());
        assertEquals(LegacyFuelRuleMaterializer.ANY, wool.damage());

        var sword = LegacyFuelRuleMaterializer.materialize(
                rule(vanillaItem("iron_sword"), 5, 80), context).orElseThrow();
        assertEquals("minecraft:iron_sword", sword.id());
        assertEquals(5, sword.damage());

        assertFalse(LegacyFuelRuleMaterializer.materialize(
                rule(vanillaBlock("wool"), null, 100), context).isPresent());
    }

    private LegacyFuelHandlerAnalyzer.FuelRule rule(
            LegacyRecipeAnalyzer.RegistryValue registry, Integer metadata, int ticks) {
        return new LegacyFuelHandlerAnalyzer.FuelRule(registry, metadata, ticks, "fixture/Fuel", "getBurnTime");
    }

    private LegacyRecipeAnalyzer.RegistryValue modItem(String name) {
        return new LegacyRecipeAnalyzer.RegistryValue(LegacyRegistryAnalyzer.Kind.ITEM, name, "fixture", "fixture/Items", "field");
    }

    private LegacyRecipeAnalyzer.RegistryValue modBlock(String name) {
        return new LegacyRecipeAnalyzer.RegistryValue(LegacyRegistryAnalyzer.Kind.BLOCK, name, "fixture", "fixture/Blocks", "field");
    }

    private LegacyRecipeAnalyzer.RegistryValue vanillaItem(String name) {
        return new LegacyRecipeAnalyzer.RegistryValue(LegacyRegistryAnalyzer.Kind.ITEM, name, "minecraft", "net/minecraft/init/Items", "field");
    }

    private LegacyRecipeAnalyzer.RegistryValue vanillaBlock(String name) {
        return new LegacyRecipeAnalyzer.RegistryValue(LegacyRegistryAnalyzer.Kind.BLOCK, name, "minecraft", "net/minecraft/init/Blocks", "field");
    }

    private ConversionContext context() {
        LegacyModMetadata metadata = new LegacyModMetadata(
                "fixture.jar", "test",
                List.of(new LegacyModMetadata.ModEntry("fixture", "Fixture", "1.0", "1.7.10", List.of())));
        LegacyJarAnalyzer.Analysis analysis = new LegacyJarAnalyzer.Analysis(
                "fixture.jar", 0, 0, false, false, 0, 0, 0, 0, Set.of(), Set.of(), Set.of());
        return new ConversionContext(
                tempDir.resolve("fixture.jar"), tempDir.resolve("staging"), tempDir.resolve("candidate.jar"),
                "sha", 0L, metadata, analysis, new DiagnosticCollector(), "generic-test");
    }
}
