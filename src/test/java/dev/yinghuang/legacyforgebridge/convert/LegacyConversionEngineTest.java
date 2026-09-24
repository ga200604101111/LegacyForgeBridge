package dev.yinghuang.legacyforgebridge.convert;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionResult;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
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
    void iterableDerivedNameItemsBecomeLoaderSafeThroughTheGenericPipeline() throws Exception {
        Path source=createIterableLegacyJar(tempDir.resolve("IterableGeneric.jar"));
        ConversionResult result=new LegacyConversionEngine().convert(
                source,tempDir.resolve("converted-iterable"),tempDir.resolve("manifests-iterable"));
        assertEquals("generic-forge-1.7.10",result.profileId());
        Path candidate=result.candidateJar().orElseThrow();
        assertTrue(new ManagedCandidateInstaller(tempDir.resolve("mods-iterable"),tempDir.resolve("cache-iterable"))
                .isLoaderSafeCandidate(candidate));

        try(JarFile jar=new JarFile(candidate.toFile())){
            JsonObject content=readJson(jar,"legacyforgebridge/converted-content.json");
            assertEquals(2,content.getAsJsonArray("items").size());
            var ids=content.getAsJsonArray("items").asList().stream()
                    .map(value->value.getAsJsonObject().get("id").getAsString())
                    .collect(java.util.stream.Collectors.toSet());
            assertEquals(java.util.Set.of("iterablegeneric:alpha","iterablegeneric:beta"),ids);
            assertNull(jar.getJarEntry("other/iterable/BaseItem.class"));
            assertNull(jar.getJarEntry("other/iterable/ChildItem.class"));
            assertNull(jar.getJarEntry("other/iterable/Content.class"));
            assertNull(jar.getJarEntry("other/iterable/Bootstrap.class"));
            JsonObject strip=readJson(jar,"legacyforgebridge/client-only-source-strip.json");
            assertEquals(4,strip.get("sourceClassCount").getAsInt());
            assertEquals(0,strip.get("remainingSourceClasses").getAsInt());
            assertTrue(strip.get("loaderClassClosureComplete").getAsBoolean());
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

    private static Path createIterableLegacyJar(Path jar) throws IOException {
        String mcmod="[{\"modid\":\"iterablegeneric\",\"name\":\"Iterable Generic\",\"version\":\"1.0\",\"mcversion\":\"1.7.10\",\"dependencies\":[]}]";
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(jar))){
            writeEntry(out,"mcmod.info",mcmod.getBytes(StandardCharsets.UTF_8));
            writeEntry(out,"other/iterable/BaseItem.class",iterableBaseItem());
            writeEntry(out,"other/iterable/ChildItem.class",iterableChildItem());
            writeEntry(out,"other/iterable/Content.class",iterableContent());
            writeEntry(out,"other/iterable/Bootstrap.class",iterableBootstrap());
        }
        return jar;
    }

    private static byte[] iterableBaseItem(){
        String name="other/iterable/BaseItem";
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,name,null,"net/minecraft/item/Item",null);
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","(Ljava/lang/String;)V",null,null);m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD,0);m.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/item/Item","<init>","()V",false);
        m.visitVarInsn(Opcodes.ALOAD,0);m.visitVarInsn(Opcodes.ALOAD,1);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,name,"setUnlocalizedName","(Ljava/lang/String;)Lnet/minecraft/item/Item;",false);m.visitInsn(Opcodes.POP);
        m.visitFieldInsn(Opcodes.GETSTATIC,"other/iterable/Content","ALL","Ljava/util/List;");m.visitVarInsn(Opcodes.ALOAD,0);
        m.visitMethodInsn(Opcodes.INVOKEINTERFACE,"java/util/List","add","(Ljava/lang/Object;)Z",true);m.visitInsn(Opcodes.POP);
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] iterableChildItem(){
        String name="other/iterable/ChildItem";
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,name,null,"other/iterable/BaseItem",null);
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","(Ljava/lang/String;)V",null,null);m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD,0);m.visitVarInsn(Opcodes.ALOAD,1);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,"other/iterable/BaseItem","<init>","(Ljava/lang/String;)V",false);
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] iterableContent(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"other/iterable/Content",null,"java/lang/Object",null);
        w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"ALL","Ljava/util/List;",null,null).visitEnd();
        w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"FIRST","Lnet/minecraft/item/Item;",null,null).visitEnd();
        w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"SECOND","Lnet/minecraft/item/Item;",null,null).visitEnd();
        MethodVisitor m=w.visitMethod(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);m.visitCode();
        m.visitTypeInsn(Opcodes.NEW,"java/util/ArrayList");m.visitInsn(Opcodes.DUP);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/util/ArrayList","<init>","()V",false);
        m.visitFieldInsn(Opcodes.PUTSTATIC,"other/iterable/Content","ALL","Ljava/util/List;");
        m.visitTypeInsn(Opcodes.NEW,"other/iterable/BaseItem");m.visitInsn(Opcodes.DUP);m.visitLdcInsn("alpha");
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,"other/iterable/BaseItem","<init>","(Ljava/lang/String;)V",false);
        m.visitFieldInsn(Opcodes.PUTSTATIC,"other/iterable/Content","FIRST","Lnet/minecraft/item/Item;");
        m.visitTypeInsn(Opcodes.NEW,"other/iterable/ChildItem");m.visitInsn(Opcodes.DUP);m.visitLdcInsn("beta");
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,"other/iterable/ChildItem","<init>","(Ljava/lang/String;)V",false);
        m.visitFieldInsn(Opcodes.PUTSTATIC,"other/iterable/Content","SECOND","Lnet/minecraft/item/Item;");
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] iterableBootstrap(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"other/iterable/Bootstrap",null,"java/lang/Object",null);
        MethodVisitor h=w.visitMethod(Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC,"registerAll","()V",null,null);h.visitCode();
        h.visitFieldInsn(Opcodes.GETSTATIC,"other/iterable/Content","ALL","Ljava/util/List;");
        h.visitMethodInsn(Opcodes.INVOKEINTERFACE,"java/util/List","iterator","()Ljava/util/Iterator;",true);
        h.visitVarInsn(Opcodes.ASTORE,0);
        org.objectweb.asm.Label loop=new org.objectweb.asm.Label(),end=new org.objectweb.asm.Label();
        h.visitLabel(loop);h.visitVarInsn(Opcodes.ALOAD,0);
        h.visitMethodInsn(Opcodes.INVOKEINTERFACE,"java/util/Iterator","hasNext","()Z",true);h.visitJumpInsn(Opcodes.IFEQ,end);
        h.visitVarInsn(Opcodes.ALOAD,0);h.visitMethodInsn(Opcodes.INVOKEINTERFACE,"java/util/Iterator","next","()Ljava/lang/Object;",true);
        h.visitTypeInsn(Opcodes.CHECKCAST,"net/minecraft/item/Item");h.visitVarInsn(Opcodes.ASTORE,1);
        h.visitVarInsn(Opcodes.ALOAD,1);h.visitVarInsn(Opcodes.ALOAD,1);
        h.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/item/Item","getUnlocalizedName","()Ljava/lang/String;",false);
        h.visitInsn(Opcodes.ICONST_5);h.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/String","substring","(I)Ljava/lang/String;",false);
        h.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/GameRegistry","registerItem",
                "(Lnet/minecraft/item/Item;Ljava/lang/String;)V",false);
        h.visitJumpInsn(Opcodes.GOTO,loop);h.visitLabel(end);h.visitInsn(Opcodes.RETURN);h.visitMaxs(0,0);h.visitEnd();

        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"preInit","(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V",null,null);
        AnnotationVisitor av=m.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;",true);av.visitEnd();m.visitCode();
        m.visitMethodInsn(Opcodes.INVOKESTATIC,"other/iterable/Bootstrap","registerAll","()V",false);
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
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
