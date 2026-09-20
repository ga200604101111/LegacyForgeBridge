package dev.yinghuang.legacyforgebridge.convert.pass;

import dev.yinghuang.legacyforgebridge.convert.api.ConversionContext;
import dev.yinghuang.legacyforgebridge.convert.api.ConversionPass;
import dev.yinghuang.legacyforgebridge.convert.api.LegacyModMetadata;
import dev.yinghuang.legacyforgebridge.convert.api.SupportLevel;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Emits the tiny Fabric lifecycle bridge for a converted mod. */
public final class GeneratedModEntrypointPass implements ConversionPass {
    public static final String MARKER_PATH="legacyforgebridge/generated-entrypoint.marker";
    @Override public String id(){return "generated-modern-fabric-entrypoint";}
    @Override public void apply(ConversionContext context)throws IOException{
        String binaryName=entrypointClass(context.metadata());String internalName=binaryName.replace('.','/');Path output=context.stagingDir().resolve(internalName+".class");Files.createDirectories(output.getParent());Files.write(output,generate(internalName,generatedBaseInternal(context.metadata()),context.metadata().fabricId()));Path marker=context.stagingDir().resolve(MARKER_PATH);Files.createDirectories(marker.getParent());Files.writeString(marker,binaryName+"\n",StandardCharsets.UTF_8);context.diagnostics().info("LFB-CONVERT-ENTRYPOINT-0001",SupportLevel.ADAPTED,"Generated Fabric lifecycle entrypoint "+binaryName+"; mod-specific registration is delegated to generated classes inside the converted JAR.");
    }
    public static String entrypointClass(LegacyModMetadata metadata){return generatedBaseBinary(metadata)+".ConvertedModEntrypoint";}
    public static String generatedContentClass(LegacyModMetadata metadata){return generatedBaseBinary(metadata)+".GeneratedContent";}
    public static String generatedClientClass(LegacyModMetadata metadata){return generatedBaseBinary(metadata)+".GeneratedClient";}
    private static String generatedBaseBinary(LegacyModMetadata metadata){String safe=metadata.fabricId().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]","_").replaceAll("_+","_");if(safe.isBlank())safe="legacy_mod";return "dev.yinghuang.legacyforgebridge.generated."+safe;}
    private static String generatedBaseInternal(LegacyModMetadata metadata){return generatedBaseBinary(metadata).replace('.','/');}
    private static byte[] generate(String internalName,String generatedBase,String modId){
        ClassWriter writer=new ClassWriter(0);writer.visit(Opcodes.V21,Opcodes.ACC_PUBLIC|Opcodes.ACC_FINAL|Opcodes.ACC_SUPER,internalName,null,"java/lang/Object",new String[]{"net/fabricmc/api/ModInitializer","net/fabricmc/api/ClientModInitializer"});
        MethodVisitor constructor=writer.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);constructor.visitCode();constructor.visitVarInsn(Opcodes.ALOAD,0);constructor.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false);constructor.visitInsn(Opcodes.RETURN);constructor.visitMaxs(1,1);constructor.visitEnd();
        MethodVisitor main=writer.visitMethod(Opcodes.ACC_PUBLIC,"onInitialize","()V",null,null);main.visitCode();main.visitLdcInsn(modId);main.visitMethodInsn(Opcodes.INVOKESTATIC,"dev/yinghuang/legacyforgebridge/compat/LegacyPlainEntityRegistry","loadMod","(Ljava/lang/String;)V",false);main.visitMethodInsn(Opcodes.INVOKESTATIC,generatedBase+"/GeneratedContent","initialize","()V",false);main.visitInsn(Opcodes.RETURN);main.visitMaxs(1,1);main.visitEnd();
        MethodVisitor client=writer.visitMethod(Opcodes.ACC_PUBLIC,"onInitializeClient","()V",null,null);client.visitCode();client.visitMethodInsn(Opcodes.INVOKESTATIC,generatedBase+"/GeneratedClient","initialize","()V",false);client.visitLdcInsn(modId);client.visitMethodInsn(Opcodes.INVOKESTATIC,"dev/yinghuang/legacyforgebridge/render/ConvertedGridPotPresentationRuntime","initializeMod","(Ljava/lang/String;)V",false);client.visitLdcInsn(modId);client.visitMethodInsn(Opcodes.INVOKESTATIC,"dev/yinghuang/legacyforgebridge/render/ConvertedPlainEntityPresentationRuntime","initializeMod","(Ljava/lang/String;)V",false);client.visitLdcInsn(modId);client.visitMethodInsn(Opcodes.INVOKESTATIC,"dev/yinghuang/legacyforgebridge/render/ConvertedProcessorPresentationRuntime","initializeMod","(Ljava/lang/String;)V",false);client.visitLdcInsn(modId);client.visitMethodInsn(Opcodes.INVOKESTATIC,"dev/yinghuang/legacyforgebridge/render/ConvertedInertModelPresentationRuntime","initializeMod","(Ljava/lang/String;)V",false);client.visitLdcInsn(modId);client.visitMethodInsn(Opcodes.INVOKESTATIC,"dev/yinghuang/legacyforgebridge/render/ConvertedOscillatingModelPresentationRuntime","initializeMod","(Ljava/lang/String;)V",false);client.visitLdcInsn(modId);client.visitMethodInsn(Opcodes.INVOKESTATIC,"dev/yinghuang/legacyforgebridge/render/ConvertedSeatBedPresentationRuntime","initializeMod","(Ljava/lang/String;)V",false);client.visitLdcInsn(modId);client.visitMethodInsn(Opcodes.INVOKESTATIC,"dev/yinghuang/legacyforgebridge/render/ConvertedPlantPresentationRuntime","initializeMod","(Ljava/lang/String;)V",false);client.visitLdcInsn(modId);client.visitMethodInsn(Opcodes.INVOKESTATIC,"dev/yinghuang/legacyforgebridge/render/ConvertedVariantSnowballPresentationRuntime","initializeMod","(Ljava/lang/String;)V",false);client.visitInsn(Opcodes.RETURN);client.visitMaxs(1,1);client.visitEnd();writer.visitEnd();return writer.toByteArray();
    }
}
