package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyFmlModAnnotationAnalyzerTest {
    @TempDir Path tempDir;

    @Test
    void preservesExactAnnotationVersionInsteadOfDescriptiveMcmodVersion() throws Exception {
        Path jar = tempDir.resolve("legacy.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry("example/LegacyMod.class"));
            output.write(modClass());
            output.closeEntry();
        }

        var analysis = new LegacyFmlModAnnotationAnalyzer().analyze(jar);
        assertTrue(analysis.diagnostics().isEmpty(), analysis.diagnostics().toString());
        assertEquals(1, analysis.mods().size());
        var mod = analysis.mods().getFirst();
        assertEquals("example/LegacyMod", mod.sourceClass());
        assertEquals("ExampleMod", mod.modId());
        assertEquals("Minecraft@MC_VERSION@ var@VERSION@", mod.version());
        assertEquals("required-after:Forge@[10.13.2.1230,)", mod.dependencies());
    }

    private static byte[] modClass() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "example/LegacyMod",
                null, "java/lang/Object", null);
        AnnotationVisitor annotation = writer.visitAnnotation(
                "Lcpw/mods/fml/common/Mod;", true);
        annotation.visit("modid", "ExampleMod");
        annotation.visit("name", "Example Mod");
        annotation.visit("version", "Minecraft@MC_VERSION@ var@VERSION@");
        annotation.visit("dependencies", "required-after:Forge@[10.13.2.1230,)");
        annotation.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
