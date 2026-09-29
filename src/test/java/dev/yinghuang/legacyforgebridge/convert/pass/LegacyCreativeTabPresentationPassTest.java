package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.*;
import dev.yinghuang.legacyforgebridge.convert.*;
import dev.yinghuang.legacyforgebridge.convert.api.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

import static org.junit.jupiter.api.Assertions.*;

class LegacyCreativeTabPresentationPassTest {
    @TempDir Path temp;

    @Test
    void sourceTabsReplaceSyntheticFallbackWithoutKnowingModName()throws Exception{
        Path source=temp.resolve("ForeignTabs.jar");
        try(JarOutputStream jar=new JarOutputStream(Files.newOutputStream(source))){
            put(jar,"other/tab/Tab.class",tabClass());
            put(jar,"other/tab/Sword.class",swordClass());
            put(jar,"other/tab/CrateBlock.class",blockClass());
            put(jar,"other/tab/Content.class",contentClass());
        }
        Path staging=temp.resolve("staging");
        Files.createDirectories(staging.resolve("legacyforgebridge"));
        Files.writeString(staging.resolve("legacyforgebridge/converted-content.json"),"""
                {"namespace":"foreign","blocks":[
                  {"id":"foreign:crate","legacyRegistryName":"crate"}
                ],"items":[
                  {"id":"foreign:blade","legacyRegistryName":"blade","kind":"sword"},
                  {"id":"foreign:other","legacyRegistryName":"other","kind":"item"}
                ]}
                """);
        LegacyModMetadata metadata=new LegacyModMetadata("ForeignTabs.jar","fixture",
                List.of(new LegacyModMetadata.ModEntry("foreign","Foreign","1","1.7.10",List.of())));
        ConversionContext context=new ConversionContext(source,staging,temp.resolve("candidate.jar"),Hashing.sha256(source),
                Files.size(source),metadata,new LegacyJarAnalyzer().analyze(source),new DiagnosticCollector(),"generic-forge-1.7.10");

        new LegacyCreativeTabPresentationPass().apply(context);

        JsonObject root=JsonParser.parseString(Files.readString(staging.resolve("legacyforgebridge/converted-content.json"))).getAsJsonObject();
        JsonArray tabs=root.getAsJsonArray("creativeTabs");assertEquals(1,tabs.size());
        JsonObject tab=tabs.get(0).getAsJsonObject();
        assertEquals("foreign:weapons",tab.get("id").getAsString());
        assertEquals("foreign:blade",tab.get("icon").getAsString());
        assertEquals(List.of("foreign:blade","foreign:crate"),tab.getAsJsonArray("items").asList().stream().map(JsonElement::getAsString).toList());
        assertEquals("foreign:weapons",root.getAsJsonArray("items").get(0).getAsJsonObject().get("creativeTab").getAsString());
        assertEquals("foreign:weapons",root.getAsJsonArray("blocks").get(0).getAsJsonObject().get("creativeTab").getAsString());
        assertFalse(root.getAsJsonArray("items").get(1).getAsJsonObject().has("creativeTab"));
    }

    private static byte[] tabClass(){
        ClassWriter w=new ClassWriter(0);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"other/tab/Tab",null,"net/minecraft/creativetab/CreativeTabs",null);
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","(Ljava/lang/String;)V",null,null);m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD,0);m.visitVarInsn(Opcodes.ALOAD,1);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/creativetab/CreativeTabs","<init>","(Ljava/lang/String;)V",false);
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(2,2);m.visitEnd();w.visitEnd();return w.toByteArray();
    }
    private static byte[] swordClass(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"other/tab/Sword",null,"net/minecraft/item/ItemSword",null);
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD,0);m.visitInsn(Opcodes.ACONST_NULL);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/item/ItemSword","<init>","(Lnet/minecraft/item/Item$ToolMaterial;)V",false);
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }
    private static byte[] blockClass(){
        String n="other/tab/CrateBlock";
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,n,null,"net/minecraft/block/Block",null);
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD,0);m.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/block/Block","<init>","()V",false);
        m.visitVarInsn(Opcodes.ALOAD,0);m.visitLdcInsn("crate");
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,n,"setBlockName","(Ljava/lang/String;)Lnet/minecraft/block/Block;",false);m.visitInsn(Opcodes.POP);
        m.visitVarInsn(Opcodes.ALOAD,0);m.visitFieldInsn(Opcodes.GETSTATIC,"other/tab/Content","TAB","Lnet/minecraft/creativetab/CreativeTabs;");
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,n,"setCreativeTab","(Lnet/minecraft/creativetab/CreativeTabs;)Lnet/minecraft/block/Block;",false);m.visitInsn(Opcodes.POP);
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] contentClass(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"other/tab/Content",null,"java/lang/Object",null);
        w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"TAB","Lnet/minecraft/creativetab/CreativeTabs;",null,null).visitEnd();
        w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"BLADE","Lother/tab/Sword;",null,null).visitEnd();
        w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"CRATE","Lother/tab/CrateBlock;",null,null).visitEnd();
        MethodVisitor m=w.visitMethod(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);m.visitCode();
        m.visitTypeInsn(Opcodes.NEW,"other/tab/Tab");m.visitInsn(Opcodes.DUP);m.visitLdcInsn("weapons");
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,"other/tab/Tab","<init>","(Ljava/lang/String;)V",false);
        m.visitFieldInsn(Opcodes.PUTSTATIC,"other/tab/Content","TAB","Lnet/minecraft/creativetab/CreativeTabs;");
        m.visitTypeInsn(Opcodes.NEW,"other/tab/Sword");m.visitInsn(Opcodes.DUP);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,"other/tab/Sword","<init>","()V",false);
        m.visitLdcInsn("blade");m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"other/tab/Sword","setUnlocalizedName","(Ljava/lang/String;)Lnet/minecraft/item/Item;",false);
        m.visitFieldInsn(Opcodes.GETSTATIC,"other/tab/Content","TAB","Lnet/minecraft/creativetab/CreativeTabs;");
        m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"other/tab/Sword","setCreativeTab","(Lnet/minecraft/creativetab/CreativeTabs;)Lnet/minecraft/item/Item;",false);
        m.visitFieldInsn(Opcodes.PUTSTATIC,"other/tab/Content","BLADE","Lother/tab/Sword;");
        m.visitTypeInsn(Opcodes.NEW,"other/tab/CrateBlock");m.visitInsn(Opcodes.DUP);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL,"other/tab/CrateBlock","<init>","()V",false);
        m.visitFieldInsn(Opcodes.PUTSTATIC,"other/tab/Content","CRATE","Lother/tab/CrateBlock;");
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }
    private static void put(JarOutputStream jar,String name,byte[] bytes)throws Exception{
        jar.putNextEntry(new JarEntry(name));jar.write(bytes);jar.closeEntry();
    }
}
