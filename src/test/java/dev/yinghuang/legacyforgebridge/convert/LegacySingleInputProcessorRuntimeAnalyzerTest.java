package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacySingleInputProcessorRuntimeAnalyzerTest {
    @TempDir Path tempDir;

    @Test
    void sidedExtractionAndLegacyEnergyContractAreProvenStructurally() throws Exception {
        Path jar=tempDir.resolve("EnergyMachine.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(jar))){
            put(out,"generic/machine/EnergyBase.class",energyBase());
            put(out,"generic/machine/Tile.class",tile());
        }
        var machine=new LegacySingleInputProcessorAnalyzer.Rule(
                "machine",null,"generic/machine/Block","generic/machine/Tile","tile",
                3,64,0,List.of(1,2),List.of(0),List.of(2,1),List.of(0),400,64.0,1,
                "generic/machine/Recipes","lookup","(Lnet/minecraft/item/ItemStack;)Lgeneric/machine/Recipe;",
                true,true,true);
        var proof=new LegacySingleInputProcessorRuntimeAnalyzer().analyze(jar,machine);
        assertTrue(proof.complete(),proof.diagnostics().toString());
        assertTrue(proof.sidedExtractionProven());
        assertTrue(proof.legacyEnergyApiPresent());
        assertEquals(100,proof.minUseEnergy());
        assertEquals(500,proof.maxUseEnergy());
        assertEquals("innerEnergy",proof.energyNbtKey());
        assertTrue(proof.energyAccelerationProven());
    }

    @Test
    void sourceEnergyStepKeepsLegacyOvershootSemantics(){
        assertEquals(1,LegacySingleInputProcessorRuntimeAnalyzer.sourceProgressStep(0,500,100));
        assertEquals(500,LegacySingleInputProcessorRuntimeAnalyzer.sourceEnergyAfterStep(0,500,100));
        assertEquals(1,LegacySingleInputProcessorRuntimeAnalyzer.sourceProgressStep(10,100,100));
        assertEquals(100,LegacySingleInputProcessorRuntimeAnalyzer.sourceEnergyAfterStep(10,100,100));
        assertEquals(6,LegacySingleInputProcessorRuntimeAnalyzer.sourceProgressStep(10,500,100));
        assertEquals(-100,LegacySingleInputProcessorRuntimeAnalyzer.sourceEnergyAfterStep(10,500,100));
    }

    private static byte[] energyBase(){
        String name="generic/machine/EnergyBase";ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,name,null,"net/minecraft/tileentity/TileEntity",new String[]{"cofh/api/energy/IEnergyHandler"});
        w.visitField(Opcodes.ACC_PROTECTED,"energy","I",null,null).visitEnd();
        MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/tileentity/TileEntity","<init>","()V",false);c.visitInsn(Opcodes.RETURN);c.visitMaxs(0,0);c.visitEnd();
        MethodVisitor max=w.visitMethod(Opcodes.ACC_PROTECTED,"maxEnergy","()I",null,null);max.visitCode();max.visitIntInsn(Opcodes.BIPUSH,100);max.visitInsn(Opcodes.IRETURN);max.visitMaxs(0,0);max.visitEnd();
        MethodVisitor receive=w.visitMethod(Opcodes.ACC_PUBLIC,"receiveEnergy","(Lnet/minecraftforge/common/util/ForgeDirection;IZ)I",null,null);receive.visitCode();receive.visitVarInsn(Opcodes.ALOAD,0);receive.visitMethodInsn(Opcodes.INVOKEVIRTUAL,name,"maxEnergy","()I",false);receive.visitInsn(Opcodes.IRETURN);receive.visitMaxs(0,0);receive.visitEnd();
        MethodVisitor read=w.visitMethod(Opcodes.ACC_PUBLIC,"func_145839_a","(Lnet/minecraft/nbt/NBTTagCompound;)V",null,null);read.visitCode();read.visitVarInsn(Opcodes.ALOAD,1);read.visitLdcInsn("innerEnergy");read.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/nbt/NBTTagCompound","func_74762_e","(Ljava/lang/String;)I",false);read.visitInsn(Opcodes.POP);read.visitInsn(Opcodes.RETURN);read.visitMaxs(0,0);read.visitEnd();
        MethodVisitor write=w.visitMethod(Opcodes.ACC_PUBLIC,"func_145841_b","(Lnet/minecraft/nbt/NBTTagCompound;)V",null,null);write.visitCode();write.visitVarInsn(Opcodes.ALOAD,1);write.visitLdcInsn("innerEnergy");write.visitVarInsn(Opcodes.ALOAD,0);write.visitFieldInsn(Opcodes.GETFIELD,name,"energy","I");write.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"net/minecraft/nbt/NBTTagCompound","func_74768_a","(Ljava/lang/String;I)V",false);write.visitInsn(Opcodes.RETURN);write.visitMaxs(0,0);write.visitEnd();
        w.visitEnd();return w.toByteArray();
    }

    private static byte[] tile(){
        String name="generic/machine/Tile",parent="generic/machine/EnergyBase";ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,name,null,parent,new String[]{"net/minecraft/inventory/ISidedInventory"});
        MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitMethodInsn(Opcodes.INVOKESPECIAL,parent,"<init>","()V",false);c.visitInsn(Opcodes.RETURN);c.visitMaxs(0,0);c.visitEnd();
        intReturn(w,Opcodes.ACC_PUBLIC,"minEnergy",100);
        intReturn(w,Opcodes.ACC_PROTECTED,"maxEnergy",500);
        MethodVisitor take=w.visitMethod(Opcodes.ACC_PUBLIC,"func_102008_b","(ILnet/minecraft/item/ItemStack;I)Z",null,null);take.visitCode();
        take.visitVarInsn(Opcodes.ILOAD,3);org.objectweb.asm.Label yes=new org.objectweb.asm.Label(),no=new org.objectweb.asm.Label(),end=new org.objectweb.asm.Label();take.visitJumpInsn(Opcodes.IFNE,yes);take.visitVarInsn(Opcodes.ILOAD,1);take.visitJumpInsn(Opcodes.IFEQ,no);take.visitLabel(yes);take.visitInsn(Opcodes.ICONST_1);take.visitJumpInsn(Opcodes.GOTO,end);take.visitLabel(no);take.visitInsn(Opcodes.ICONST_0);take.visitLabel(end);take.visitInsn(Opcodes.IRETURN);take.visitMaxs(0,0);take.visitEnd();
        MethodVisitor tick=w.visitMethod(Opcodes.ACC_PUBLIC,"func_145845_h","()V",null,null);tick.visitCode();
        tick.visitIntInsn(Opcodes.BIPUSH,500);tick.visitVarInsn(Opcodes.ALOAD,0);tick.visitMethodInsn(Opcodes.INVOKEVIRTUAL,name,"minEnergy","()I",false);tick.visitInsn(Opcodes.IDIV);tick.visitInsn(Opcodes.ICONST_1);tick.visitInsn(Opcodes.IADD);tick.visitInsn(Opcodes.I2B);tick.visitVarInsn(Opcodes.ISTORE,1);
        tick.visitVarInsn(Opcodes.ALOAD,0);tick.visitMethodInsn(Opcodes.INVOKEVIRTUAL,name,"minEnergy","()I",false);tick.visitVarInsn(Opcodes.ILOAD,1);tick.visitInsn(Opcodes.IMUL);tick.visitIntInsn(Opcodes.SIPUSH,500);tick.visitInsn(Opcodes.SWAP);tick.visitInsn(Opcodes.ISUB);tick.visitInsn(Opcodes.POP);
        tick.visitVarInsn(Opcodes.ALOAD,0);tick.visitMethodInsn(Opcodes.INVOKEVIRTUAL,name,"minEnergy","()I",false);tick.visitInsn(Opcodes.POP);tick.visitInsn(Opcodes.RETURN);tick.visitMaxs(0,0);tick.visitEnd();
        w.visitEnd();return w.toByteArray();
    }
    private static void intReturn(ClassWriter w,int access,String name,int value){MethodVisitor m=w.visitMethod(access,name,"()I",null,null);m.visitCode();if(value<=Byte.MAX_VALUE)m.visitIntInsn(Opcodes.BIPUSH,value);else m.visitIntInsn(Opcodes.SIPUSH,value);m.visitInsn(Opcodes.IRETURN);m.visitMaxs(0,0);m.visitEnd();}
    private static void put(JarOutputStream out,String name,byte[] bytes)throws Exception{out.putNextEntry(new JarEntry(name));out.write(bytes);out.closeEntry();}
}
