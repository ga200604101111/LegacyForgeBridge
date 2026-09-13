package dev.longyu.legacyforgebridge.convert.pass;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.longyu.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.longyu.legacyforgebridge.convert.api.ConversionContext;
import dev.longyu.legacyforgebridge.convert.api.ConversionStatus;
import dev.longyu.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.longyu.legacyforgebridge.convert.api.LegacyModMetadata;
import dev.longyu.legacyforgebridge.convert.profile.RpgTool1Profile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RpgTool1ContentPassTest {
    @TempDir
    Path tempDir;

    @Test
    void exactCorpusBecomesLoaderSafeContentWithSeventyOneItemsAndObjWeaponModels() throws Exception {
        Path staging = tempDir.resolve("staging");
        Files.createDirectories(staging.resolve("mhzd/net/rpgtool1"));
        Files.write(staging.resolve("mhzd/net/rpgtool1/Main.class"), new byte[]{1, 2, 3});
        Files.writeString(staging.resolve("mcmod.info"), "[]", StandardCharsets.UTF_8);

        Path lang = staging.resolve("assets/rpgtool1/lang/zh_CN.lang");
        Files.createDirectories(lang.getParent());
        Files.writeString(
                lang,
                "item.dark_sword.name=暗黑之劍\n"
                        + "item.wing01.name=一階羽翼\n"
                        + "item.buff1_1.name=一階光環\n"
                        + "item.attack1.name=一級攻擊寶石\n",
                StandardCharsets.UTF_8
        );

        ConversionContext context = context(staging);
        RpgTool1ContentPass pass = new RpgTool1ContentPass();
        pass.apply(context);
        context.markPassApplied(pass.id());

        assertFalse(Files.exists(staging.resolve("mhzd/net/rpgtool1/Main.class")));
        assertFalse(Files.exists(staging.resolve("mcmod.info")));

        JsonObject content = readJson(staging.resolve("legacyforgebridge/converted-content.json"));
        assertEquals(71, content.getAsJsonArray("items").size());
        assertEquals("rpgtool1", content.get("namespace").getAsString());
        assertEquals("rpgtool1", content.getAsJsonArray("legacyMods").get(0).getAsJsonObject().get("modid").getAsString());

        JsonObject swordItem = readJson(staging.resolve("assets/rpgtool1/items/dark_sword.json"));
        JsonObject swordModel = swordItem.getAsJsonObject("model");
        assertEquals("minecraft:special", swordModel.get("type").getAsString());
        assertEquals(
                "legacyforgebridge:obj",
                swordModel.getAsJsonObject("model").get("type").getAsString()
        );
        assertEquals(
                "rpgtool1:textures/items3D/dark_sword.obj",
                swordModel.getAsJsonObject("model").get("model").getAsString()
        );

        JsonObject zhTw = readJson(staging.resolve("assets/rpgtool1/lang/zh_tw.json"));
        assertEquals("暗黑之劍", zhTw.get("item.rpgtool1.dark_sword").getAsString());
        assertEquals("一階羽翼", zhTw.get("item.rpgtool1.wing01").getAsString());

        assertEquals(
                "rpgtool1:dark_sword",
                context.registryIdentities().get("items").get("rpgtool1:dark_sword")
        );
        assertEquals(
                "item.rpgtool1.dark_sword",
                context.registryIdentities().get("translations").get("item.dark_sword.name")
        );
        assertTrue(context.diagnostics().snapshot().stream()
                .anyMatch(diagnostic -> diagnostic.ruleId().equals("LFB-RPGTOOL-BEHAVIOR-0001")));

        new LegacyBytecodeAuditPass().apply(context);
        assertEquals(ConversionStatus.PARTIAL, context.diagnostics().status());
        assertTrue(context.diagnostics().snapshot().stream()
                .anyMatch(diagnostic -> diagnostic.ruleId().equals("LFB-CONVERT-BYTECODE-0002")));
    }

    private ConversionContext context(Path staging) {
        LegacyModMetadata metadata = new LegacyModMetadata(
                "RPGTool1-1.1-1.7.10.jar",
                "mcmod.info",
                List.of(new LegacyModMetadata.ModEntry("rpgtool1", "RPGTool1", "1.0", "1.7.10", List.of()))
        );
        LegacyJarAnalyzer.Analysis analysis = new LegacyJarAnalyzer.Analysis(
                "RPGTool1-1.1-1.7.10.jar",
                53,
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
        return new ConversionContext(
                tempDir.resolve("RPGTool1-1.1-1.7.10.jar"),
                staging,
                tempDir.resolve("candidate.jar"),
                RpgTool1Profile.CORPUS_SHA256,
                14_556_748L,
                metadata,
                analysis,
                new DiagnosticCollector(),
                "rpgtool1-1.7.10"
        );
    }

    private static JsonObject readJson(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
