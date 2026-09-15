package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacySingleInputProcessorEnergyIngressAnalyzerTest {
    @TempDir Path tempDir;

    @Test
    void sourceEquivalentReceiverContractIsAdmittedAcrossUnrelatedNamespace() throws Exception {
        var proof=new LegacySingleInputProcessorEnergyIngressAnalyzer().analyze(
                jar(true),machine(),runtimeProof());
        assertTrue(proof.complete(),proof.diagnostics().toString());
        assertTrue(proof.legacyEnergyApiPresent());
        assertTrue(proof.allSidesConnect());
        assertTrue(proof.extractionDisabled());
        assertTrue(proof.queryMethodsReturnZero());
        assertTrue(proof.receiveSimulationProven());
    }

    @Test
    void changedConnectionContractFailsClosed() throws Exception {
        var proof=new LegacySingleInputProcessorEnergyIngressAnalyzer().analyze(
                jar(false),machine(),runtimeProof());
        assertTrue(!proof.complete());
        assertTrue(!proof.allSidesConnect());
    }

    private Path jar(boolean connects)throws Exception{
        Path jar=tempDir.resolve(connects?"receiver-ok.jar":"receiver-bad.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(jar))){
            put(out,"other/power/Base.class",energyBase(connects));
            put(out,"other/power/Tile.class",tile());
        }
        return jar;
    }

    private static LegacySingleInputProcessorAnalyzer.Rule machine(){
        return new LegacySingleInputProcessorAnalyzer.Rule(
                "machine",null,"other/power/Block","other/power/Tile","tile",
                3,64,0,List.of(1,2),List.of(0),List.of(2,1),List.of(0),400,64.0,1,
                "other/power/Recipes","lookup","(Lnet/minecraft/item/ItemStack;)Lother/power/Recipe;",
                true,true,true);
    }

    private static LegacySingleInputProcessorRuntimeAnalyzer.Proof runtimeProof(){
        return new LegacySingleInputProcessorRuntimeAnalyzer.Proof(
                true,true,100,500,"innerEnergy",true,List.of());
    }

    private static byte[] energyBase(boolean connects){
        String name="other/power/Base";
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,name,null,"net/minecraft/tileentity/TileEntity",
                new String[]{"cofh/api/energy/IEnergyHandler"});
        w.visitField(Opcodes.ACC_PROTECTED,"energy","I",null,null).visitEnd();
        ctor(w,name,"net/minecraft/tileentity/TileEntity");
        intReturn(w,Opcodes.ACC_PROTECTED,"maxEnergy",500);
        intReturn(w,Opcodes.ACC_PUBLIC,"getEnergyStored",0,"(Lnet/minecraftforge/common/util/ForgeDirection;)I");
        intReturn(w,Opcodes.ACC_PUBLIC,"getMaxEnergyStored",0,"(Lnet/minecraftforge/common/util/ForgeDirection;)I");
        intReturn(w,Opcodes.ACC_PUBLIC,"extractEnergy",0,"(Lnet/minecraftforge/common/util/ForgeDirection;IZ)I");
        intReturn(w,Opcodes.ACC_PUBLIC,"canConnectEnergy",connects?1:0,"(Lnet/minecraftforge/common/util/ForgeDirection;)Z");

        MethodVisitor r=w.visitMethod(Opcodes.ACC_PUBLIC,"receiveEnergy",
                "(Lnet/minecraftforge/common/util/ForgeDirection;IZ)I",null,null);
        r.visitCode();
        r.visitInsn(Opcodes.ICONST_0);r.visitVarInsn(Opcodes.ISTORE,4);
        r.visitVarInsn(Opcodes.ALOAD,0);r.visitFieldInsn(Opcodes.GETFIELD,name,"energy","I");
        r.visitVarInsn(Opcodes.ALOAD,0);r.visitMethodInsn(Opcodes.INVOKEVIRTUAL,name,"maxEnergy","()I",false);
        Label end=new Label(),useRequest=new Label(),afterChoice=new Label();
        r.visitJumpInsn(Opcodes.IF_ICMPGE,end);
        r.visitVarInsn(Opcodes.ALOAD,0);r.visitMethodInsn(Opcodes.INVOKEVIRTUAL,name,"maxEnergy","()I",false);
        r.visitVarInsn(Opcodes.ALOAD,0);r.visitFieldInsn(Opcodes.GETFIELD,name,"energy","I");r.visitInsn(Opcodes.ISUB);
        r.visitVarInsn(Opcodes.ILOAD,2);r.visitJumpInsn(Opcodes.IF_ICMPGE,useRequest);
        r.visitVarInsn(Opcodes.ALOAD,0);r.visitMethodInsn(Opcodes.INVOKEVIRTUAL,name,"maxEnergy","()I",false);
        r.visitVarInsn(Opcodes.ALOAD,0);r.visitFieldInsn(Opcodes.GETFIELD,name,"energy","I");r.visitInsn(Opcodes.ISUB);r.visitVarInsn(Opcodes.ISTORE,4);
        r.visitVarInsn(Opcodes.ILOAD,3);r.visitJumpInsn(Opcodes.IFNE,afterChoice);
        r.visitVarInsn(Opcodes.ALOAD,0);r.visitVarInsn(Opcodes.ALOAD,0);r.visitMethodInsn(Opcodes.INVOKEVIRTUAL,name,"maxEnergy","()I",false);r.visitFieldInsn(Opcodes.PUTFIELD,name,"energy","I");r.visitJumpInsn(Opcodes.GOTO,afterChoice);
        r.visitLabel(useRequest);r.visitVarInsn(Opcodes.ILOAD,2);r.visitVarInsn(Opcodes.ISTORE,4);
        r.visitVarInsn(Opcodes.ILOAD,3);r.visitJumpInsn(Opcodes.IFNE,afterChoice);
        r.visitVarInsn(Opcodes.ALOAD,0);r.visitInsn(Opcodes.DUP);r.visitFieldInsn(Opcodes.GETFIELD,name,"energy","I");r.visitVarInsn(Opcodes.ILOAD,2);r.visitInsn(Opcodes.IADD);r.visitFieldInsn(Opcodes.PUTFIELD,name,"energy","I");
        r.visitLabel(afterChoice);r.visitLabel(end);r.visitVarInsn(Opcodes.ILOAD,4);r.visitInsn(Opcodes.IRETURN);r.visitMaxs(0,0);r.visitEnd();
        w.visitEnd();return w.toByteArray();
    }

    private static byte[] tile(){
        String name="other/power/Tile",parent="other/power/Base";ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,name,null,parent,null);ctor(w,name,parent);w.visitEnd();return w.toByteArray();
    }
    private static void ctor(ClassWriter w,String name,String parent){MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitMethodInsn(Opcodes.INVOKESPECIAL,parent,"<init>","()V",false);c.visitInsn(Opcodes.RETURN);c.visitMaxs(0,0);c.visitEnd();}
    private static void intReturn(ClassWriter w,int access,String name,int value){intReturn(w,access,name,value,"()I");}
    private static void intReturn(ClassWriter w,int access,String name,int value,String desc){MethodVisitor m=w.visitMethod(access,name,desc,null,null);m.visitCode();if(value>=-1&&value<=5)m.visitInsn(Opcodes.ICONST_0+value);else if(value<=Byte.MAX_VALUE)m.visitIntInsn(Opcodes.BIPUSH,value);else m.visitIntInsn(Opcodes.SIPUSH,value);m.visitInsn(Opcodes.IRETURN);m.visitMaxs(0,0);m.visitEnd();}
    private static void put(JarOutputStream out,String name,byte[] bytes)throws Exception{out.putNextEntry(new JarEntry(name));out.write(bytes);out.closeEntry();}
}
