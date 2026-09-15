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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyItemBlockBindingPassTest {
    @TempDir Path tempDir;

    @Test void sidecarMapsSourceProvenSeedBlocksButKeepsRuntimeGated() throws Exception {
        Path source=tempDir.resolve("foreign-seed.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(source))){put(out,"pkg/Crop.class",block("pkg/Crop"));put(out,"pkg/Soil.class",block("pkg/Soil"));put(out,"pkg/Seed.class",seed());put(out,"pkg/Bootstrap.class",bootstrap());}
        Path staging=tempDir.resolve("staging");Files.createDirectories(staging.resolve("legacyforgebridge"));
        Files.writeString(staging.resolve("legacyforgebridge/converted-content.json"),"{\"items\":[{\"id\":\"foreign:seed\",\"legacyRegistryName\":\"seed\",\"sourceClass\":\"pkg/Seed\"}],\"blocks\":[{\"id\":\"foreign:crop\",\"legacyRegistryName\":\"crop\",\"sourceClass\":\"pkg/Crop\"},{\"id\":\"foreign:soil\",\"legacyRegistryName\":\"soil\",\"sourceClass\":\"pkg/Soil\"}]}\n",StandardCharsets.UTF_8);
        LegacyModMetadata metadata=new LegacyModMetadata("foreign-seed.jar","test",List.of(new LegacyModMetadata.ModEntry("foreign","Foreign","1.0","1.7.10",List.of())));
        LegacyJarAnalyzer.Analysis jarAnalysis=new LegacyJarAnalyzer.Analysis("foreign-seed.jar",0,0,false,false,0,0,0,0,Set.of(),Set.of(),Set.of());
        ConversionContext context=new ConversionContext(source,staging,tempDir.resolve("candidate.jar"),"sha",Files.size(source),metadata,jarAnalysis,new DiagnosticCollector(),"generic-test");
        new LegacyItemBlockBindingPass().apply(context);
        JsonObject root=JsonParser.parseString(Files.readString(staging.resolve(LegacyItemBlockBindingPass.OUTPUT),StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(1,root.get("topologyProofCompleteBindings").getAsInt());assertEquals(1,root.get("modernIdentityCompleteBindings").getAsInt());assertEquals(0,root.get("runtimeCompleteBindings").getAsInt());
        JsonObject binding=root.getAsJsonArray("bindings").get(0).getAsJsonObject();assertEquals("foreign:seed",binding.get("id").getAsString());assertEquals("seeds",binding.get("family").getAsString());assertTrue(binding.get("topologyProofComplete").getAsBoolean());assertTrue(binding.get("modernIdentityComplete").getAsBoolean());assertFalse(binding.get("runtimeComplete").getAsBoolean());
        assertEquals("foreign:crop",binding.getAsJsonObject("targetBlock").get("modernId").getAsString());assertEquals("foreign:soil",binding.getAsJsonObject("soilBlock").get("modernId").getAsString());
    }
    private static byte[] block(String name){ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,name,null,"net/minecraft/block/Block",null);MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitInsn(Opcodes.ACONST_NULL);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/block/Block","<init>","(Lnet/minecraft/block/material/Material;)V",false);c.visitInsn(Opcodes.RETURN);c.visitMaxs(0,0);c.visitEnd();w.visitEnd();return w.toByteArray();}
    private static byte[] seed(){ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"pkg/Seed",null,"net/minecraft/item/ItemSeeds",null);MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitFieldInsn(Opcodes.GETSTATIC,"pkg/Bootstrap","CROP","Lnet/minecraft/block/Block;");c.visitFieldInsn(Opcodes.GETSTATIC,"pkg/Bootstrap","SOIL","Lnet/minecraft/block/Block;");c.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/item/ItemSeeds","<init>","(Lnet/minecraft/block/Block;Lnet/minecraft/block/Block;)V",false);c.visitInsn(Opcodes.RETURN);c.visitMaxs(0,0);c.visitEnd();w.visitEnd();return w.toByteArray();}
    private static byte[] bootstrap(){ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"pkg/Bootstrap",null,"java/lang/Object",null);w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"CROP","Lnet/minecraft/block/Block;",null,null).visitEnd();w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"SOIL","Lnet/minecraft/block/Block;",null,null).visitEnd();MethodVisitor b=w.visitMethod(Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC,"bindBlock","(Lnet/minecraft/block/Block;Ljava/lang/String;)Lnet/minecraft/block/Block;",null,null);b.visitCode();b.visitVarInsn(Opcodes.ALOAD,0);b.visitVarInsn(Opcodes.ALOAD,1);b.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/GameRegistry","registerBlock","(Lnet/minecraft/block/Block;Ljava/lang/String;)V",false);b.visitVarInsn(Opcodes.ALOAD,0);b.visitInsn(Opcodes.ARETURN);b.visitMaxs(0,0);b.visitEnd();MethodVisitor m=w.visitMethod(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);m.visitCode();bind(m,"pkg/Crop","crop","CROP");bind(m,"pkg/Soil","soil","SOIL");m.visitTypeInsn(Opcodes.NEW,"pkg/Seed");m.visitInsn(Opcodes.DUP);m.visitMethodInsn(Opcodes.INVOKESPECIAL,"pkg/Seed","<init>","()V",false);m.visitLdcInsn("seed");m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/GameRegistry","registerItem","(Lnet/minecraft/item/Item;Ljava/lang/String;)V",false);m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();}
    private static void bind(MethodVisitor m,String type,String id,String field){m.visitTypeInsn(Opcodes.NEW,type);m.visitInsn(Opcodes.DUP);m.visitMethodInsn(Opcodes.INVOKESPECIAL,type,"<init>","()V",false);m.visitLdcInsn(id);m.visitMethodInsn(Opcodes.INVOKESTATIC,"pkg/Bootstrap","bindBlock","(Lnet/minecraft/block/Block;Ljava/lang/String;)Lnet/minecraft/block/Block;",false);m.visitFieldInsn(Opcodes.PUTSTATIC,"pkg/Bootstrap",field,"Lnet/minecraft/block/Block;");}
    private static void put(JarOutputStream out,String name,byte[] bytes)throws Exception{out.putNextEntry(new JarEntry(name));out.write(bytes);out.closeEntry();}
}
