package dev.yinghuang.legacyforgebridge.convert.pass;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yinghuang.legacyforgebridge.convert.LegacyJarAnalyzer;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.DiagnosticCollector;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacySnowballItemPassTest {
    @TempDir Path tempDir;
    @Test void provenInheritedSnowballReclassifiesGeneratedItemOnly()throws Exception{
        Path source=tempDir.resolve("snow.jar");try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(source))){put(out,"foo/Pure.class",pure());put(out,"foo/Bootstrap.class",bootstrap());}
        Path staging=tempDir.resolve("staging");Files.createDirectories(staging.resolve("legacyforgebridge"));Files.writeString(staging.resolve("legacyforgebridge/converted-content.json"),"{\"items\":[{\"id\":\"foreign:pure\",\"kind\":\"item\",\"legacyRegistryName\":\"pure\",\"sourceClass\":\"foo/Pure\"}],\"blocks\":[]}\n",StandardCharsets.UTF_8);
        LegacyModMetadata metadata=new LegacyModMetadata("snow.jar","test",List.of(new LegacyModMetadata.ModEntry("foreign","Foreign","1.0","1.7.10",List.of())));LegacyJarAnalyzer.Analysis jarAnalysis=new LegacyJarAnalyzer.Analysis("snow.jar",0,0,false,false,0,0,0,0,Set.of(),Set.of(),Set.of());ConversionContext context=new ConversionContext(source,staging,tempDir.resolve("candidate.jar"),"sha",Files.size(source),metadata,jarAnalysis,new DiagnosticCollector(),"generic-test");new LegacySnowballItemPass().apply(context);
        JsonObject content=JsonParser.parseString(Files.readString(staging.resolve("legacyforgebridge/converted-content.json"))).getAsJsonObject();assertEquals("snowball",content.getAsJsonArray("items").get(0).getAsJsonObject().get("kind").getAsString());JsonObject rules=JsonParser.parseString(Files.readString(staging.resolve(LegacySnowballItemPass.OUTPUT))).getAsJsonObject();assertEquals(1,rules.get("runtimeCompleteRules").getAsInt());assertTrue(rules.getAsJsonArray("rules").get(0).getAsJsonObject().get("vanillaUseSemanticsProven").getAsBoolean());
    }
    private static byte[] pure(){ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"foo/Pure",null,"net/minecraft/item/ItemSnowball",null);MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/item/ItemSnowball","<init>","()V",false);c.visitInsn(Opcodes.RETURN);c.visitMaxs(0,0);c.visitEnd();w.visitEnd();return w.toByteArray();}
    private static byte[] bootstrap(){ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"foo/Bootstrap",null,"java/lang/Object",null);MethodVisitor m=w.visitMethod(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);m.visitCode();m.visitTypeInsn(Opcodes.NEW,"foo/Pure");m.visitInsn(Opcodes.DUP);m.visitMethodInsn(Opcodes.INVOKESPECIAL,"foo/Pure","<init>","()V",false);m.visitLdcInsn("pure");m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/GameRegistry","registerItem","(Lnet/minecraft/item/Item;Ljava/lang/String;)V",false);m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();}
    private static void put(JarOutputStream out,String name,byte[] bytes)throws Exception{out.putNextEntry(new JarEntry(name));out.write(bytes);out.closeEntry();}
}
