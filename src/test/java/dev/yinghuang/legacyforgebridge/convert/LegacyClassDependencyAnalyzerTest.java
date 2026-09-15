package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyClassDependencyAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    void inventoriesRootsCapabilitiesGeneratedReferencesAndCandidateStateWithoutDeletingAnything() throws Exception {
        Map<String, byte[]> classes = fixtureClasses();
        Path source = writeJar(tempDir.resolve("fixture.jar"), classes, null);
        Path staging = tempDir.resolve("staging");
        Files.createDirectories(staging);
        for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
            if (entry.getKey().equals("example/Unused")) continue;
            Path target = staging.resolve(entry.getKey() + ".class");
            Files.createDirectories(target.getParent());
            Files.write(target, entry.getValue());
        }
        Path generated = staging.resolve("modern/Generated.class");
        Files.createDirectories(generated.getParent());
        Files.write(generated, classWithField("modern/Generated", "example/Unused"));

        LegacyClassDependencyAnalyzer.Analysis result = new LegacyClassDependencyAnalyzer().analyze(source, staging);
        assertEquals(classes.size(), result.classes().size());

        var main = dependency(result, "example/ModMain");
        assertEquals(LegacyClassDependencyAnalyzer.Reachability.POTENTIALLY_REACHABLE, main.reachability());
        assertTrue(main.rootEvidence().contains("FML @Mod entrypoint"));

        var client = dependency(result, "example/ClientProxy");
        assertTrue(client.rootEvidence().stream().anyMatch(value -> value.contains("SidedProxy clientSide")));
        var gui = dependency(result, "example/GuiFactory");
        assertTrue(gui.rootEvidence().stream().anyMatch(value -> value.contains("guiFactory")));

        var tile = dependency(result, "example/Tile");
        assertEquals(LegacyClassDependencyAnalyzer.Reachability.POTENTIALLY_REACHABLE, tile.reachability());
        assertEquals(LegacyClassDependencyAnalyzer.CandidateState.ORIGINAL_BYTES_RETAINED, tile.candidateState());
        assertTrue(tile.capabilities().contains("block_entity"));
        assertTrue(tile.capabilities().contains("inventory_menu"));
        assertTrue(tile.capabilities().contains("nbt"));

        var unused = dependency(result, "example/Unused");
        assertEquals(LegacyClassDependencyAnalyzer.CandidateState.ABSENT_REPLACEMENT_UNPROVEN, unused.candidateState());
        assertTrue(unused.generatedReferences().contains("modern/Generated"));
        assertEquals(LegacyClassDependencyAnalyzer.Reachability.POTENTIALLY_REACHABLE, unused.reachability(),
                "generated modern bytecode referencing a source class is a loader/runtime dependency root");

        assertTrue(result.classes().stream().noneMatch(value -> value.action().equals("exclude")));
        assertTrue(result.limitations().stream().anyMatch(value -> value.contains("never authorizes")));
    }

    @Test
    void reflectionIsReportedAndDormantTransformerIsNotActivatedWithoutManifest() throws Exception {
        Path source = writeJar(tempDir.resolve("fixture.jar"), fixtureClasses(), null);
        LegacyClassDependencyAnalyzer.Analysis result = new LegacyClassDependencyAnalyzer().analyze(source);

        var dynamic = dependency(result, "example/Dynamic");
        assertFalse(dynamic.dynamicEvidence().isEmpty());
        assertEquals("unresolved", dynamic.action());

        var transformer = dependency(result, "example/DormantTransformer");
        assertTrue(transformer.roles().contains("transformer"));
        assertTrue(transformer.rootEvidence().isEmpty());
        assertEquals(LegacyClassDependencyAnalyzer.Reachability.UNRESOLVED_NOT_PROVEN_UNREACHABLE,
                transformer.reachability());
    }

    @Test
    void manifestCorePluginActivationBecomesExplicitRootEvidence() throws Exception {
        Path source = writeJar(tempDir.resolve("active.jar"), fixtureClasses(), "example.DormantTransformer");
        var transformer = dependency(new LegacyClassDependencyAnalyzer().analyze(source), "example/DormantTransformer");
        assertTrue(transformer.rootEvidence().contains("manifest FMLCorePlugin activation"));
        assertEquals(LegacyClassDependencyAnalyzer.Reachability.POTENTIALLY_REACHABLE, transformer.reachability());
    }

    private static LegacyClassDependencyAnalyzer.ClassDependency dependency(
            LegacyClassDependencyAnalyzer.Analysis analysis, String name) {
        return analysis.classes().stream().filter(value -> value.sourceClass().equals(name)).findFirst().orElseThrow();
    }

    private static Map<String, byte[]> fixtureClasses() {
        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put("example/ModMain", modMain());
        classes.put("example/CommonProxy", classWithField("example/CommonProxy", "example/Tile"));
        classes.put("example/ClientProxy", plainClass("example/ClientProxy", "java/lang/Object", null));
        classes.put("example/GuiFactory", plainClass("example/GuiFactory", "java/lang/Object", null));
        classes.put("example/Tile", tileClass());
        classes.put("example/Unused", plainClass("example/Unused", "java/lang/Object", null));
        classes.put("example/Dynamic", dynamicClass());
        classes.put("example/DormantTransformer", plainClass(
                "example/DormantTransformer", "java/lang/Object",
                new String[]{"net/minecraft/launchwrapper/IClassTransformer"}));
        return classes;
    }

    private static byte[] modMain() {
        ClassWriter writer = base("example/ModMain", "java/lang/Object", null);
        AnnotationVisitor mod = writer.visitAnnotation("Lcpw/mods/fml/common/Mod;", true);
        mod.visit("modid", "fixture");
        mod.visit("guiFactory", "example.GuiFactory");
        mod.visitEnd();
        FieldVisitor proxy = writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "proxy", "Ljava/lang/Object;", null, null);
        AnnotationVisitor sided = proxy.visitAnnotation("Lcpw/mods/fml/common/SidedProxy;", true);
        sided.visit("clientSide", "example.ClientProxy");
        sided.visit("serverSide", "example.CommonProxy");
        sided.visitEnd();
        proxy.visitEnd();
        defaultConstructor(writer, "java/lang/Object");
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] tileClass() {
        ClassWriter writer = base("example/Tile", "net/minecraft/tileentity/TileEntity", new String[]{"net/minecraft/inventory/IInventory"});
        writer.visitField(Opcodes.ACC_PRIVATE, "tag", "Lnet/minecraft/nbt/NBTTagCompound;", null, null).visitEnd();
        defaultConstructor(writer, "net/minecraft/tileentity/TileEntity");
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] dynamicClass() {
        ClassWriter writer = base("example/Dynamic", "java/lang/Object", null);
        defaultConstructor(writer, "java/lang/Object");
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "load", "()Ljava/lang/Class;", null, new String[]{"java/lang/ClassNotFoundException"});
        method.visitCode();
        method.visitLdcInsn("example.Unused");
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] classWithField(String name, String target) {
        ClassWriter writer = base(name, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PRIVATE, "value", "L" + target + ";", null, null).visitEnd();
        defaultConstructor(writer, "java/lang/Object");
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] plainClass(String name, String superName, String[] interfaces) {
        ClassWriter writer = base(name, superName, interfaces);
        defaultConstructor(writer, superName);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static ClassWriter base(String name, String superName, String[] interfaces) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, name, null, superName, interfaces);
        return writer;
    }

    private static void defaultConstructor(ClassWriter writer, String superName) {
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, superName, "<init>", "()V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
    }

    private static Path writeJar(Path output, Map<String, byte[]> classes, String corePlugin) throws Exception {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (corePlugin != null) manifest.getMainAttributes().putValue("FMLCorePlugin", corePlugin);
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(output), manifest)) {
            for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
                jar.putNextEntry(new JarEntry(entry.getKey() + ".class"));
                jar.write(entry.getValue());
                jar.closeEntry();
            }
        }
        return output;
    }
}
