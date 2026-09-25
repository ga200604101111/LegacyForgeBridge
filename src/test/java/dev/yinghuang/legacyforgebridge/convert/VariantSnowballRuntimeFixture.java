package dev.yinghuang.legacyforgebridge.convert;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/** Full variant-snowball fixture plus source-proven EntityRegistry.registerModEntity metadata. */
public final class VariantSnowballRuntimeFixture {
    private VariantSnowballRuntimeFixture() { }

    public static Path write(Path jar) throws Exception {
        Path base = jar.resolveSibling(jar.getFileName() + ".base");
        VariantSnowballLaunchFixture.write(base);
        try (JarFile input = new JarFile(base.toFile());
             JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            var entries = input.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if ("foreign/Bootstrap.class".equals(entry.getName())) continue;
                output.putNextEntry(new JarEntry(entry.getName()));
                try (var stream = input.getInputStream(entry)) {
                    output.write(stream.readAllBytes());
                }
                output.closeEntry();
            }
            output.putNextEntry(new JarEntry("foreign/Bootstrap.class"));
            output.write(bootstrap());
            output.closeEntry();
        } finally {
            Files.deleteIfExists(base);
        }
        return jar;
    }

    private static byte[] bootstrap() {
        String owner = "foreign/Bootstrap";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);

        MethodVisitor constructor = writer.visitMethod(
                Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(
                Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();

        MethodVisitor staticInit = writer.visitMethod(
                Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        staticInit.visitCode();
        staticInit.visitTypeInsn(Opcodes.NEW, "foreign/item/VariantBall");
        staticInit.visitInsn(Opcodes.DUP);
        staticInit.visitMethodInsn(
                Opcodes.INVOKESPECIAL, "foreign/item/VariantBall", "<init>", "()V", false);
        staticInit.visitLdcInsn("variant_ball");
        staticInit.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "cpw/mods/fml/common/registry/GameRegistry",
                "registerItem",
                "(Lnet/minecraft/item/Item;Ljava/lang/String;)V",
                false);
        staticInit.visitInsn(Opcodes.RETURN);
        staticInit.visitMaxs(0, 0);
        staticInit.visitEnd();

        MethodVisitor preInit = writer.visitMethod(
                Opcodes.ACC_PUBLIC,
                "preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V",
                null,
                null);
        AnnotationVisitor annotation = preInit.visitAnnotation(
                "Lcpw/mods/fml/common/Mod$EventHandler;", true);
        annotation.visitEnd();
        preInit.visitCode();
        preInit.visitLdcInsn(Type.getObjectType("foreign/entity/VariantProjectile"));
        preInit.visitLdcInsn("variant_projectile");
        preInit.visitIntInsn(Opcodes.BIPUSH, 41);
        preInit.visitVarInsn(Opcodes.ALOAD, 0);
        preInit.visitIntInsn(Opcodes.BIPUSH, 64);
        preInit.visitIntInsn(Opcodes.BIPUSH, 10);
        preInit.visitInsn(Opcodes.ICONST_1);
        preInit.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "cpw/mods/fml/common/registry/EntityRegistry",
                "registerModEntity",
                "(Ljava/lang/Class;Ljava/lang/String;ILjava/lang/Object;IIZ)V",
                false);
        preInit.visitInsn(Opcodes.RETURN);
        preInit.visitMaxs(0, 0);
        preInit.visitEnd();

        writer.visitEnd();
        return writer.toByteArray();
    }
}
