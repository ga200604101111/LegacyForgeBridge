package dev.yinghuang.legacyforgebridge.convert;

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
    void customItemSubclassThatEndsAtExternalItemSwordStillKeepsDirectCreativeTabMembership() throws Exception {
        Path jarPath = tempDir.resolve("direct.jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath))) {
            write(jar, "example/WeaponTab", tabClass());
            write(jar, "example/RpgSword", bareCustomSwordClass());
            write(jar, "example/Content", directContentClass());
        }

        LegacyCreativeTabAnalyzer.Analysis analysis = new LegacyCreativeTabAnalyzer().analyze(jarPath);
        assertEquals(1, analysis.tabs().size());
        LegacyCreativeTabAnalyzer.Tab tab = analysis.tabs().getFirst();
        assertEquals("rpgtool1_weapon", tab.label());
        assertEquals(1, tab.itemNames().size());
        assertEquals("dark_sword", tab.itemNames().getFirst());
    }

    @Test
    void itemConstructorOwnedCreativeTabIsInheritedByAllocationSite() throws Exception {
        Path jarPath = tempDir.resolve("constructor-owned.jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath))) {
            write(jar, "example/WeaponTab", tabClass());
            write(jar, "example/RpgSword", constructorOwnedCustomSwordClass());
            write(jar, "example/Content", constructorOwnedContentClass());
        }

        LegacyCreativeTabAnalyzer.Analysis analysis = new LegacyCreativeTabAnalyzer().analyze(jarPath);
        assertEquals(1, analysis.tabs().size());
        LegacyCreativeTabAnalyzer.Tab tab = analysis.tabs().getFirst();
        assertEquals("rpgtool1_weapon", tab.label());
        assertEquals(1, tab.itemNames().size());
        assertEquals("dark_sword", tab.itemNames().getFirst());
    }

    @Test
    void registryFieldIdentityWinsOverAmbiguousMultiStringConstructorHeuristic() throws Exception {
        Path jarPath=tempDir.resolve("multi-string-musket.jar");
        try(JarOutputStream jar=new JarOutputStream(Files.newOutputStream(jarPath))){
            write(jar,"example/WeaponTab",tabClass());
            write(jar,"example/MultiStringGun",multiStringGunClass());
            write(jar,"example/MultiStringContent",multiStringContentClass());
        }

        LegacyCreativeTabAnalyzer.Analysis analysis=new LegacyCreativeTabAnalyzer().analyze(jarPath);
        assertEquals(1,analysis.tabs().size());
        assertEquals(java.util.List.of("musket"),analysis.tabs().getFirst().itemNames(),
                "Exact GameRegistry field binding must beat the last-string constructor heuristic");
    }

    private static byte[] multiStringGunClass(){
        String n="example/MultiStringGun";
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,n,null,"net/minecraft/item/Item",null);
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>",
                "(Ljava/lang/String;Ljava/lang/String;)V",null,null);m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD,0);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/item/Item","<init>","()V",false);
        m.visitVarInsn(Opcodes.ALOAD,0);m.visitVarInsn(Opcodes.ALOAD,1);
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,n,"func_77655_b","(Ljava/lang/String;)L"+ITEM+";",false);m.visitInsn(Opcodes.POP);
        m.visitVarInsn(Opcodes.ALOAD,0);
        m.visitFieldInsn(Opcodes.GETSTATIC,"example/MultiStringContent","TAB","L"+TABS+";");
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,n,"func_77637_a","(L"+TABS+";)L"+ITEM+";",false);m.visitInsn(Opcodes.POP);
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] multiStringContentClass(){
        String n="example/MultiStringContent";
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,n,null,"java/lang/Object",null);
        w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"TAB","L"+TABS+";",null,null).visitEnd();
        w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"MUSKET","Lexample/MultiStringGun;",null,null).visitEnd();
        MethodVisitor s=w.visitMethod(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);s.visitCode();
        s.visitTypeInsn(Opcodes.NEW,"example/WeaponTab");s.visitInsn(Opcodes.DUP);s.visitLdcInsn("weapons");
        s.visitMethodInsn(Opcodes.INVOKESPECIAL,"example/WeaponTab","<init>","(Ljava/lang/String;)V",false);
        s.visitFieldInsn(Opcodes.PUTSTATIC,n,"TAB","L"+TABS+";");
        s.visitTypeInsn(Opcodes.NEW,"example/MultiStringGun");s.visitInsn(Opcodes.DUP);
        s.visitLdcInsn("musket");s.visitLdcInsn("musket_texture");
        s.visitMethodInsn(Opcodes.INVOKESPECIAL,"example/MultiStringGun","<init>",
                "(Ljava/lang/String;Ljava/lang/String;)V",false);
        s.visitFieldInsn(Opcodes.PUTSTATIC,n,"MUSKET","Lexample/MultiStringGun;");
        s.visitInsn(Opcodes.RETURN);s.visitMaxs(0,0);s.visitEnd();

        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"preInit",
                "(Lcpw/mods/fml/common/event/FMLPreInitializationEvent;)V",null,null);
        AnnotationVisitor av=m.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;",true);av.visitEnd();m.visitCode();
        m.visitFieldInsn(Opcodes.GETSTATIC,n,"MUSKET","Lexample/MultiStringGun;");
        m.visitLdcInsn("musket");
        m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/GameRegistry","registerItem",
                "(Lnet/minecraft/item/Item;Ljava/lang/String;)V",false);
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();
        w.visitEnd();return w.toByteArray();
    }

    @Test
    void staleStaticItemReadCannotStealFreshAllocationCreativeTab() throws Exception {
        Path jarPath=tempDir.resolve("stale-static-read.jar");
        try(JarOutputStream jar=new JarOutputStream(Files.newOutputStream(jarPath))){
            write(jar,"example/WeaponTab",tabClass());
            write(jar,"example/RpgSword",bareCustomSwordClass());
            write(jar,"example/Content",staleReadContentClass());
        }

        LegacyCreativeTabAnalyzer.Analysis analysis=new LegacyCreativeTabAnalyzer().analyze(jarPath);
        assertEquals(1,analysis.tabs().size());
        LegacyCreativeTabAnalyzer.Tab tab=analysis.tabs().getFirst();
        assertEquals(java.util.List.of("musket"),tab.itemNames(),
                "A previous GETSTATIC item read must not receive the new allocation's setCreativeTab call");
    }

    private static byte[] staleReadContentClass(){
        ClassWriter writer=contentSkeleton();
        writer.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"old_item","Lexample/RpgSword;",null,null).visitEnd();
        writer.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"musket","Lexample/RpgSword;",null,null).visitEnd();
        MethodVisitor method=writer.visitMethod(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);
        method.visitCode();createTab(method);
        method.visitFieldInsn(Opcodes.GETSTATIC,"example/Content","old_item","Lexample/RpgSword;");
        method.visitInsn(Opcodes.POP);
        method.visitTypeInsn(Opcodes.NEW,"example/RpgSword");method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL,"example/RpgSword","<init>","()V",false);
        method.visitLdcInsn("musket");
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"example/RpgSword","func_77655_b",
                "(Ljava/lang/String;)L"+ITEM+";",false);
        method.visitFieldInsn(Opcodes.GETSTATIC,"example/Content","weaponTab","L"+TABS+";");
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"example/RpgSword","func_77637_a",
                "(L"+TABS+";)L"+ITEM+";",false);
        method.visitFieldInsn(Opcodes.PUTSTATIC,"example/Content","musket","Lexample/RpgSword;");
        method.visitInsn(Opcodes.RETURN);method.visitMaxs(4,0);method.visitEnd();
        writer.visitEnd();return writer.toByteArray();
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

    private static byte[] bareCustomSwordClass() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "example/RpgSword", null, "net/minecraft/item/ItemSword", null);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] constructorOwnedCustomSwordClass() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "example/RpgSword", null, "net/minecraft/item/ItemSword", null);
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/lang/String;)V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitInsn(Opcodes.ACONST_NULL);
        init.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "net/minecraft/item/ItemSword",
                "<init>",
                "(Lnet/minecraft/item/Item$ToolMaterial;)V",
                false
        );
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitVarInsn(Opcodes.ALOAD, 1);
        init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "example/RpgSword", "func_77655_b", "(Ljava/lang/String;)L" + ITEM + ";", false);
        init.visitInsn(Opcodes.POP);
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitFieldInsn(Opcodes.GETSTATIC, "example/Content", "weaponTab", "L" + TABS + ";");
        init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "example/RpgSword", "func_77637_a", "(L" + TABS + ";)L" + ITEM + ";", false);
        init.visitInsn(Opcodes.POP);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(2, 2);
        init.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] directContentClass() {
        ClassWriter writer = contentSkeleton();
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        method.visitCode();
        createTab(method);

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

    private static byte[] constructorOwnedContentClass() {
        ClassWriter writer = contentSkeleton();
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        method.visitCode();
        createTab(method);

        method.visitTypeInsn(Opcodes.NEW, "example/RpgSword");
        method.visitInsn(Opcodes.DUP);
        method.visitLdcInsn("dark_sword");
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "example/RpgSword", "<init>", "(Ljava/lang/String;)V", false);
        method.visitFieldInsn(Opcodes.PUTSTATIC, "example/Content", "dark_sword", "Lexample/RpgSword;");
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(3, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static ClassWriter contentSkeleton() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "example/Content", null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "weaponTab", "L" + TABS + ";", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "dark_sword", "Lexample/RpgSword;", null, null).visitEnd();
        return writer;
    }

    private static void createTab(MethodVisitor method) {
        method.visitTypeInsn(Opcodes.NEW, "example/WeaponTab");
        method.visitInsn(Opcodes.DUP);
        method.visitLdcInsn("rpgtool1_weapon");
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "example/WeaponTab", "<init>", "(Ljava/lang/String;)V", false);
        method.visitFieldInsn(Opcodes.PUTSTATIC, "example/Content", "weaponTab", "L" + TABS + ";");
    }

    private static void write(JarOutputStream jar, String name, byte[] bytes) throws Exception {
        jar.putNextEntry(new JarEntry(name + ".class"));
        jar.write(bytes);
        jar.closeEntry();
    }
}
