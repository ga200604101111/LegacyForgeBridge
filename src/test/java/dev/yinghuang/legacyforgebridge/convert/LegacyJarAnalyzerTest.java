package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.*;

class LegacyJarAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    void detectsForgeMinecraftCoremodAndLegacyOpenGlReferences() throws Exception {
        Path jar = tempDir.resolve("synthetic-forge-1.7.10.jar");
        writeSyntheticLegacyJar(jar);

        LegacyJarAnalyzer.Analysis result = new LegacyJarAnalyzer().analyze(jar);

        assertEquals("synthetic-forge-1.7.10.jar", result.fileName());
        assertEquals(1, result.classCount());
        assertEquals(0, result.unreadableClasses());
        assertTrue(result.hasMcmodInfo());
        assertTrue(result.hasManifest());
        assertTrue(result.likelyForgeMod());
        assertTrue(result.requiresManualCoremodReview());
        assertTrue(result.forgeReferenceCount() > 0);
        assertTrue(result.minecraftReferenceCount() > 0);
        assertTrue(result.openglReferenceCount() > 0);
        assertTrue(result.coremodReferences().contains("cpw/mods/fml/relauncher/IFMLLoadingPlugin"));
        assertTrue(result.openglReferences().contains("org/lwjgl/opengl/GL11"));
    }

    private static void writeSyntheticLegacyJar(Path target) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(target), manifest)) {
            out.putNextEntry(new JarEntry("mcmod.info"));
            out.write("[{\"modid\":\"synthetic\",\"mcversion\":\"1.7.10\"}]".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();

            out.putNextEntry(new JarEntry("example/SyntheticForgeMod.class"));
            out.write(syntheticClass());
            out.closeEntry();
        }
    }

    private static byte[] syntheticClass() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(
                Opcodes.V1_7,
                Opcodes.ACC_PUBLIC,
                "example/SyntheticForgeMod",
                null,
                "java/lang/Object",
                new String[]{"cpw/mods/fml/relauncher/IFMLLoadingPlugin"}
        );

        AnnotationVisitor mod = writer.visitAnnotation("Lcpw/mods/fml/common/Mod;", true);
        mod.visit("modid", "synthetic");
        mod.visitEnd();

        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();

        MethodVisitor touch = writer.visitMethod(Opcodes.ACC_PUBLIC, "touch", "(Lnet/minecraft/item/Item;)V", null, null);
        touch.visitCode();
        touch.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/GL11", "glPushMatrix", "()V", false);
        touch.visitVarInsn(Opcodes.ALOAD, 1);
        touch.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "cpw/mods/fml/common/registry/GameRegistry",
                "findUniqueIdentifierFor",
                "(Lnet/minecraft/item/Item;)Ljava/lang/Object;",
                false
        );
        touch.visitInsn(Opcodes.POP);
        touch.visitInsn(Opcodes.RETURN);
        touch.visitMaxs(1, 2);
        touch.visitEnd();

        writer.visitEnd();
        return writer.toByteArray();
    }
}
