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

class LegacyPlantLifecyclePassTest {
    @TempDir Path tempDir;

    @Test void sidecarEmitsForgePlantableAndIndependentLifecycleGatesWhileKeepingRuntimeClosed() throws Exception {
        Path source=tempDir.resolve("crop.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(source))){put(out,"x/Crop.class",crop());put(out,"x/Bootstrap.class",bootstrap());}
        Path staging=tempDir.resolve("staging");Files.createDirectories(staging.resolve("legacyforgebridge"));
        Files.writeString(staging.resolve("legacyforgebridge/converted-content.json"),"{\"items\":[],\"blocks\":[{\"id\":\"foreign:crop\",\"legacyRegistryName\":\"crop\",\"sourceClass\":\"x/Crop\"}]}\n",StandardCharsets.UTF_8);
        LegacyModMetadata metadata=new LegacyModMetadata("crop.jar","test",List.of(new LegacyModMetadata.ModEntry("foreign","Foreign","1.0","1.7.10",List.of())));
        LegacyJarAnalyzer.Analysis jarAnalysis=new LegacyJarAnalyzer.Analysis("crop.jar",0,0,false,false,0,0,0,0,Set.of(),Set.of(),Set.of());
        ConversionContext context=new ConversionContext(source,staging,tempDir.resolve("candidate.jar"),"sha",Files.size(source),metadata,jarAnalysis,new DiagnosticCollector(),"generic-test");
        new LegacyPlantLifecyclePass().apply(context);
        JsonObject root=JsonParser.parseString(Files.readString(staging.resolve(LegacyPlantLifecyclePass.OUTPUT),StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(2,root.get("schemaVersion").getAsInt());
        assertEquals(1,root.get("classifiedBlocks").getAsInt());
        assertEquals(1,root.get("forgePlantableContractProofCompleteBlocks").getAsInt());
        assertEquals(1,root.get("survivalProofCompleteBlocks").getAsInt());
        assertEquals(1,root.get("growthProofCompleteBlocks").getAsInt());
        assertEquals(1,root.get("bonemealProofCompleteBlocks").getAsInt());
        assertEquals(1,root.get("dropProofCompleteBlocks").getAsInt());
        assertEquals(1,root.get("ageModelProofCompleteBlocks").getAsInt());
        assertEquals(0,root.get("runtimeCompleteBlocks").getAsInt());
        JsonObject proof=root.getAsJsonArray("proofs").get(0).getAsJsonObject();
        assertEquals("foreign:crop",proof.get("modernId").getAsString());
        assertEquals("legacy_meta_0_7",proof.get("ageModel").getAsString());
        assertEquals("forge_crops_plains",proof.get("survivalModel").getAsString());
        assertTrue(proof.get("forgePlantableContractInherited").getAsBoolean());
        assertEquals(0,proof.getAsJsonArray("plantableHooks").size());
        assertTrue(proof.get("survivalInheritedVanilla").getAsBoolean());
        assertTrue(proof.get("growthInheritedVanilla").getAsBoolean());
        assertFalse(proof.get("runtimeComplete").getAsBoolean());
    }
    private static byte[] crop(){ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"x/Crop",null,"net/minecraft/block/BlockCrops",null);MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/block/BlockCrops","<init>","()V",false);c.visitInsn(Opcodes.RETURN);c.visitMaxs(0,0);c.visitEnd();w.visitEnd();return w.toByteArray();}
    private static byte[] bootstrap(){ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"x/Bootstrap",null,"java/lang/Object",null);MethodVisitor m=w.visitMethod(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);m.visitCode();m.visitTypeInsn(Opcodes.NEW,"x/Crop");m.visitInsn(Opcodes.DUP);m.visitMethodInsn(Opcodes.INVOKESPECIAL,"x/Crop","<init>","()V",false);m.visitLdcInsn("crop");m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/GameRegistry","registerBlock","(Lnet/minecraft/block/Block;Ljava/lang/String;)V",false);m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();}
    private static void put(JarOutputStream out,String name,byte[] bytes)throws Exception{out.putNextEntry(new JarEntry(name));out.write(bytes);out.closeEntry();}
}
