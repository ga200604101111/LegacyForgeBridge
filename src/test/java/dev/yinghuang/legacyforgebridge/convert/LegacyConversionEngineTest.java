package dev.yinghuang.legacyforgebridge.convert;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionResult;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyConversionEngineTest {
    @TempDir
    Path tempDir;

    @Test
    void stagesDeterministicPartialCandidateWithMetadataResourcesAndCollisionFreeLanguage() throws Exception {
        Path source = createLegacyJar(tempDir.resolve("ExampleLegacy-1.0.jar"), false, true, true);
        LegacyConversionEngine engine = new LegacyConversionEngine();

        ConversionResult first = engine.convert(
                source,
                tempDir.resolve("converted-a"),
                tempDir.resolve("manifests-a")
        );

        assertEquals(ConversionStatus.PARTIAL, first.status());
        assertFalse(first.installable());
        assertTrue(first.candidateJar().isPresent());
        assertTrue(Files.isRegularFile(first.manifestFile()));

        Path candidate = first.candidateJar().orElseThrow();
        try (JarFile jar = new JarFile(candidate.toFile())) {
            assertNotNull(jar.getJarEntry("fabric.mod.json"));
            assertNotNull(jar.getJarEntry("legacyforgebridge/conversion-manifest.json"));
            assertNotNull(jar.getJarEntry("example/LegacyMod.class"));
            assertTrue(jar.getJarEntry("assets/examplelegacy/lang/en_US.lang") == null,
                    "Obsolete .lang inputs must not remain in modern candidates");
            assertNotNull(jar.getJarEntry("assets/examplelegacy/lang/en_us.json"));
            assertNotNull(jar.getJarEntry("assets/examplelegacy/textures/items/example.png"));

            JsonObject fabric = readJson(jar, "fabric.mod.json");
            assertEquals("examplelegacy", fabric.get("id").getAsString());
            assertEquals("1.0", fabric.get("version").getAsString());
            JsonObject lfb = fabric.getAsJsonObject("custom").getAsJsonObject("legacyforgebridge");
            assertEquals("partial", lfb.get("status").getAsString());
            assertFalse(lfb.get("installable").getAsBoolean());

            JsonObject lang = readJson(jar, "assets/examplelegacy/lang/en_us.json");
            assertEquals(
                    "/example <player> %1$s",
                    lang.get("lfb.converted.examplelegacy.commands.example.usage").getAsString()
            );
            assertFalse(lang.has("commands.example.usage"));
            assertFalse(lang.has("item.example.name"));

            JsonObject manifest = readJson(jar, "legacyforgebridge/conversion-manifest.json");
            assertEquals("partial", manifest.get("status").getAsString());
            assertEquals("generic-forge-1.7.10", manifest.get("profile").getAsString());
            JsonObject translations = manifest.getAsJsonObject("registries").getAsJsonObject("translations");
            assertEquals(
                    "lfb.converted.examplelegacy.commands.example.usage",
                    translations.get("commands.example.usage").getAsString()
            );
        }

        ConversionResult second = engine.convert(
                source,
                tempDir.resolve("converted-b"),
                tempDir.resolve("manifests-b")
        );
        assertEquals(
                Hashing.sha256(first.candidateJar().orElseThrow()),
                Hashing.sha256(second.candidateJar().orElseThrow()),
                "Equivalent conversion runs must produce deterministic candidate JARs"
        );
    }

    @Test
    void defaultEngineDoesNotSelectModSpecificProfileFromFileName() throws Exception {
        Path source=createLegacyJar(tempDir.resolve("RPGTool1-Fake.jar"),false,true,false);
        ConversionResult result=new LegacyConversionEngine().convert(
                source,tempDir.resolve("converted-generic-name"),tempDir.resolve("manifests-generic-name"));
        assertEquals("generic-forge-1.7.10",result.profileId(),
                "production default conversion must remain structural/generic even when a legacy filename resembles an old corpus profile");
    }

    @Test
    void genericNamedItemAndObjRendererFlowProducesLoaderSafeCandidate() throws Exception {
        Path source=LegacyRenderFixture.create(tempDir.resolve("ForeignRenderedMod.jar"),"foreignrender",false);
        ConversionResult result=new LegacyConversionEngine().convert(
                source,tempDir.resolve("converted-rendered"),tempDir.resolve("manifests-rendered"));
        assertEquals("generic-forge-1.7.10",result.profileId());
        Path candidate=result.candidateJar().orElseThrow();
        ManagedCandidateInstaller installer=new ManagedCandidateInstaller(
                tempDir.resolve("mods-rendered"),tempDir.resolve("cache-rendered"));
        assertTrue(installer.isLoaderSafeCandidate(candidate),
                "generic conversion must retire source Forge classes once registry/item presentation is source-proven");
        try(JarFile jar=new JarFile(candidate.toFile())){
            JsonObject content=readJson(jar,"legacyforgebridge/converted-content.json");
            assertEquals(1,content.getAsJsonArray("items").size());
            assertEquals("foreignrender:tool",content.getAsJsonArray("items").get(0).getAsJsonObject().get("id").getAsString());
            assertNull(jar.getJarEntry("foreignrender/Client.class"));
            assertNull(jar.getJarEntry("foreignrender/Renderer.class"));
        }
    }

    @Test
    void blocksLegacyCoremodsBeforeCandidateJarIsEmitted() throws Exception {
        Path source = createLegacyJar(tempDir.resolve("LegacyCoremod.jar"), true, true, true, true);
        LegacyConversionEngine engine = new LegacyConversionEngine();

        ConversionResult result = engine.convert(
                source,
                tempDir.resolve("converted-coremod"),
                tempDir.resolve("manifests-coremod")
        );

        assertEquals(ConversionStatus.BLOCKED, result.status());
        assertTrue(result.candidateJar().isEmpty());
        assertTrue(Files.isRegularFile(result.manifestFile()));
        String manifest = Files.readString(result.manifestFile(), StandardCharsets.UTF_8);
        assertTrue(manifest.contains("LFB-CONVERT-COREMOD-0001"));
    }


    @Test
    void dormantTransformerClassesDoNotBlockAnOrdinaryForgeMod() throws Exception {
        Path source = createLegacyJar(tempDir.resolve("DormantCoremodMarkers.jar"), true, true, true, false);
        LegacyConversionEngine engine = new LegacyConversionEngine();

        ConversionResult result = engine.convert(
                source,
                tempDir.resolve("converted-dormant-coremod"),
                tempDir.resolve("manifests-dormant-coremod")
        );

        assertEquals(ConversionStatus.PARTIAL, result.status());
        assertTrue(result.candidateJar().isPresent());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.ruleId().equals("LFB-CONVERT-COREMOD-0003")));
        assertFalse(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.ruleId().equals("LFB-CONVERT-COREMOD-0001")));
    }

    @Test
    void resourceOnlyLegacyJarWithoutLegacyTranslationKeysCanReachConvertedStatus() throws Exception {
        Path source = createLegacyJar(tempDir.resolve("ResourceOnly.jar"), false, false, false);
        LegacyConversionEngine engine = new LegacyConversionEngine();

        ConversionResult result = engine.convert(
                source,
                tempDir.resolve("converted-resource"),
                tempDir.resolve("manifests-resource")
        );

        assertEquals(ConversionStatus.CONVERTED, result.status());
        assertTrue(result.installable());
        assertTrue(result.candidateJar().isPresent());
    }

    @Test
    void legacyLanguageByItselfStillRequiresReferenceRetargeting() throws Exception {
        Path source = createLegacyJar(tempDir.resolve("LanguageOnly.jar"), false, false, true);
        LegacyConversionEngine engine = new LegacyConversionEngine();

        ConversionResult result = engine.convert(
                source,
                tempDir.resolve("converted-language"),
                tempDir.resolve("manifests-language")
        );

        assertEquals(ConversionStatus.PARTIAL, result.status());
        assertFalse(result.installable());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.ruleId().equals("LFB-CONVERT-LANG-0002")));
    }

    private static Path createLegacyJar(
            Path jar,
            boolean coremod,
            boolean includeClass,
            boolean includeLanguage
    ) throws IOException {
        return createLegacyJar(jar, coremod, includeClass, includeLanguage, false);
    }

    private static Path createLegacyJar(
            Path jar,
            boolean coremod,
            boolean includeClass,
            boolean includeLanguage,
            boolean activateCoremod
    ) throws IOException {
        String mcmod = """
                [
                  {
                    "modid": "examplelegacy",
                    "name": "Example Legacy",
                    "version": "1.0",
                    "mcversion": "1.7.10",
                    "dependencies": []
                  }
                ]
                """;
        java.util.jar.Manifest manifest = new java.util.jar.Manifest();
        manifest.getMainAttributes().put(java.util.jar.Attributes.Name.MANIFEST_VERSION, "1.0");
        if (activateCoremod) manifest.getMainAttributes().putValue("FMLCorePlugin", "example.LegacyMod");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            writeEntry(output, "mcmod.info", mcmod.getBytes(StandardCharsets.UTF_8));
            if (includeLanguage) {
                writeEntry(
                        output,
                        "assets/examplelegacy/lang/en_US.lang",
                        ("item.example.name=Example Item\n"
                                + "commands.example.usage=/example <player> %1$d\n").getBytes(StandardCharsets.UTF_8)
                );
            }
            writeEntry(output, "assets/examplelegacy/textures/items/example.png", new byte[]{1, 2, 3, 4});
            if (includeClass) {
                writeEntry(output, "example/LegacyMod.class", legacyClass(coremod));
            }
        }
        return jar;
    }

    private static byte[] legacyClass(boolean coremod) {
        ClassWriter writer = new ClassWriter(0);
        String[] interfaces = coremod
                ? new String[]{"net/minecraft/launchwrapper/IClassTransformer"}
                : null;
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "example/LegacyMod", null, "java/lang/Object", interfaces);

        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "register",
                "()V",
                null,
                null
        );
        method.visitCode();
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitLdcInsn("example");
        method.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "cpw/mods/fml/common/registry/GameRegistry",
                "registerItem",
                "(Lnet/minecraft/item/Item;Ljava/lang/String;)V",
                false
        );
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(2, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void writeEntry(JarOutputStream output, String name, byte[] data) throws IOException {
        output.putNextEntry(new JarEntry(name));
        output.write(data);
        output.closeEntry();
    }

    private static JsonObject readJson(JarFile jar, String path) throws IOException {
        JarEntry entry = jar.getJarEntry(path);
        assertNotNull(entry, "Missing " + path);
        try (InputStreamReader reader = new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
