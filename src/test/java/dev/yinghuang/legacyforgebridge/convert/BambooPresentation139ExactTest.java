package dev.yinghuang.legacyforgebridge.convert;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.convert.api.*;
import dev.yinghuang.legacyforgebridge.convert.pass.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarFile;
import static org.junit.jupiter.api.Assertions.*;

/** Original external corpus only, never a public source substitute or a converted candidate. */
@Tag("exact-corpus")
class BambooPresentation139ExactTest {
    @TempDir Path temp;
    @Test void provesNamesEnumIconsAndCandidateOwnedTexturesFromOriginal() throws Exception {
        Path source=Path.of(System.getProperty("lfb.exactCorpus.jar"));
        assertEquals("bcceb588950f911398cfc94856a45527b4aa17b6e130927b2e0516fdf059b402",Hashing.sha256(source));
        var registry=new LegacyRegistryAnalyzer().analyze(source);
        var names=new LegacyIconTableAnalyzer().analyzeNames(source,registry.registrations());
        var tatami=names.stream().filter(n->n.registryName().equals("dirSquare")).findFirst().orElseThrow();
        assertEquals("tile.dirSquare.0.name",tatami.keys().get(0));
        assertEquals("tile.dirSquare.4.name",tatami.keys().get(4));
        var icons=new LegacyIconTableAnalyzer().analyze(source,registry.registrations());
        var food=icons.stream().filter(i->i.registryName().equals("bambooFood")).findFirst().orElseThrow();
        assertEquals("bamboo:mugimesi",food.variants().get(0).faceIcons().getFirst());
        assertEquals("bamboo:gyumesi",food.variants().get(1).faceIcons().getFirst());
        var ice=icons.stream().filter(i->i.registryName().equals("shavedice")).findFirst().orElseThrow();
        assertTrue(ice.variants().get(1).faceIcons().size()>1);
        var special=icons.stream().filter(i->i.registryName().equals("singleTexDeco")).findFirst().orElseThrow();
        assertTrue(special.variants().isEmpty(),"A custom renderer must not be converted to a guessed cube");

        Path staging=temp.resolve("staging");Files.createDirectories(staging);
        var context=new ConversionContext(source,staging,temp.resolve("candidate.jar"),Hashing.sha256(source),Files.size(source),LegacyModMetadata.read(source),new LegacyJarAnalyzer().analyze(source),new DiagnosticCollector(),"exact-presentation");
        new CopyLegacyJarPass().apply(context);new LegacyLanguagePass().apply(context);
        new GenericContentPass().apply(context);new LegacyClientContentBaselinePass().apply(context);
        new LegacyIconPresentationPass().apply(context);new LegacyItemNamePass().apply(context);new LegacyTextureAtlasPass().apply(context);
        JsonObject report=read(staging.resolve(LegacyItemNamePass.OUTPUT));
        assertEquals("lfb.converted.bamboomod.tile.dirSquare.4.name",report.getAsJsonObject("items").getAsJsonObject("bamboomod:dirsquare").get("4").getAsString());
        assertFalse(report.get("allDynamicNamesConverted").getAsBoolean());
        assertTrue(Files.isRegularFile(staging.resolve("assets/minecraft/atlases/items.json")));
        assertTrue(Files.isRegularFile(staging.resolve("assets/minecraft/atlases/blocks.json")));
        try(JarFile jar=new JarFile(source.toFile())) {
            var entries=jar.entries();while(entries.hasMoreElements()){
                var entry=entries.nextElement();if(entry.isDirectory()||!entry.getName().startsWith("assets/")||!(entry.getName().endsWith(".png")||entry.getName().endsWith(".mcmeta")))continue;
                assertArrayEquals(jar.getInputStream(entry).readAllBytes(),Files.readAllBytes(staging.resolve(entry.getName())),entry.getName());
            }
        }
    }
    private static JsonObject read(Path file)throws Exception{return JsonParser.parseString(Files.readString(file,StandardCharsets.UTF_8)).getAsJsonObject();}
}
