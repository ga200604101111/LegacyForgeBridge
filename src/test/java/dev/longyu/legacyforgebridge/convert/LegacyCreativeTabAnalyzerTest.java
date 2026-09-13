package dev.longyu.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyCreativeTabAnalyzerTest {
    private static final String TABS = "net/minecraft/creativetab/CreativeTabs";
    private static final String ITEM = "net/minecraft/item/Item";

    @TempDir
    Path tempDir;

    @Test
    void customItemSubclassThatEndsAtExternalItemSwordStillKeepsCreativeTabMembership() throws Exception {
        Path jarPath = tempDir.resolve("legacy.jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath))) {
            write(jar, "example/WeaponTab", tabClass());
            write(jar, "example/RpgSword", customSwordClass());
            write(jar, "example/Content", contentClass());
        }

        LegacyCreativeTabAnalyzer.Analysis analysis = new LegacyCreativeTabAnalyzer().analyze(jarPath);
        assertEquals(1, analysis.tabs().size());
        LegacyCreativeTabAnalyzer.Tab tab = analysis.tabs().getFirst();
        assertEquals("rpgtool1_weapon", tab.label());
        assertEquals(1, tab.itemNames().size());
        assertEquals("dark_sword", tab.itemNames().getFirst());
    }

    private static byte[] tabClass() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "example/WeaponTab", null, TABS, null);
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/lang/String;)V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitVarInsn(Opcodes.ALOAD, 1);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, TABS, "<init>", "(Ljava/lang/String;)V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(2, 2);
        init.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] customSwordClass() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "example/RpgSword", null, "net/minecraft/item/ItemSword", null);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] contentClass() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "example/Content", null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "weaponTab", "L" + TABS + ";", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "dark_sword", "Lexample/RpgSword;", null, null).visitEnd();

        MethodVisitor method = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        method.visitCode();
        method.visitTypeInsn(Opcodes.NEW, "example/WeaponTab");
        method.visitInsn(Opcodes.DUP);
        method.visitLdcInsn("rpgtool1_weapon");
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "example/WeaponTab", "<init>", "(Ljava/lang/String;)V", false);
        method.visitFieldInsn(Opcodes.PUTSTATIC, "example/Content", "weaponTab", "L" + TABS + ";");

        method.visitTypeInsn(Opcodes.NEW, "example/RpgSword");
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "example/RpgSword", "<init>", "()V", false);
        method.visitLdcInsn("dark_sword");
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "example/RpgSword", "func_77655_b", "(Ljava/lang/String;)L" + ITEM + ";", false);
        method.visitFieldInsn(Opcodes.GETSTATIC, "example/Content", "weaponTab", "L" + TABS + ";");
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "example/RpgSword", "func_77637_a", "(L" + TABS + ";)L" + ITEM + ";", false);
        method.visitFieldInsn(Opcodes.PUTSTATIC, "example/Content", "dark_sword", "Lexample/RpgSword;");
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(4, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void write(JarOutputStream jar, String name, byte[] bytes) throws Exception {
        jar.putNextEntry(new JarEntry(name + ".class"));
        jar.write(bytes);
        jar.closeEntry();
    }
}
