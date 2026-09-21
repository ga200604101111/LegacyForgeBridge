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

import static org.junit.jupiter.api.Assertions.*;

class LegacyRadialConditionalAnalyzerTest {
    @TempDir Path tempDir;

    @Test void unrelatedMetadataSelectedModelGroupIsProvenWithoutNames()throws Exception{
        Path jar=tempDir.resolve("foreign-radial.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(jar))){
            put(out,"foreign/radial/Tile.class",tile());
            put(out,"foreign/radial/Renderer.class",renderer());
            put(out,"foreign/radial/Model.class",model());
        }
        var base=new LegacyRadialTesrAnalyzer.Rule(
                "altar","foreign/radial/Block","foreign/radial/Tile","ForeignTile",
                "foreign/radial/Renderer","foreign/radial/Model","foreign:textures/entity/altar.png",64,32,
                new LegacyRadialTesrAnalyzer.Cuboid(0,0,-1F,0F,-1F,2,2,2,0F,0F,0F),
                List.of(new LegacyRadialTesrAnalyzer.Pose(0F,0F,0F),new LegacyRadialTesrAnalyzer.Pose(0F,1F,0F)),
                .0625F,.5F,0F,.5F,3,90F,.5F,7,0F,1F,1
        );
        var analysis=new LegacyRadialConditionalAnalyzer().analyze(jar,List.of(base));
        assertTrue(analysis.skipped().isEmpty(),analysis.skipped().toString());
        var rule=analysis.rules().stream().findFirst().orElseThrow();
        assertEquals(2,rule.metadataShift());assertEquals(3,rule.selectorMask());assertEquals(1,rule.coveredModelCalls());
        assertEquals(1,rule.groups().size());var group=rule.groups().getFirst();assertEquals(1,group.selectorValue());
        assertEquals(1,group.parts().size());var part=group.parts().getFirst();
        assertNull(part.animation());assertEquals(4,part.cuboid().u());assertEquals(6,part.cuboid().v());
        assertEquals(3,part.cuboid().width());assertEquals(5,part.cuboid().height());assertEquals(1,part.cuboid().depth());
        assertEquals(8F,part.cuboid().pivotY(),0.0001F);
    }

    private static byte[] tile(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"foreign/radial/Tile",null,"net/minecraft/tileentity/TileEntity",null);
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"func_145832_p","()I",null,null);m.visitCode();m.visitInsn(Opcodes.ICONST_0);m.visitInsn(Opcodes.IRETURN);m.visitMaxs(0,0);m.visitEnd();
        w.visitEnd();return w.toByteArray();
    }

    private static byte[] renderer(){
        String owner="foreign/radial/Renderer",model="foreign/radial/Model",tile="foreign/radial/Tile";
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,owner,null,"java/lang/Object",null);
        w.visitField(Opcodes.ACC_PRIVATE,"model","L"+model+";",null,null).visitEnd();
        MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false);
        c.visitVarInsn(Opcodes.ALOAD,0);c.visitTypeInsn(Opcodes.NEW,model);c.visitInsn(Opcodes.DUP);c.visitMethodInsn(Opcodes.INVOKESPECIAL,model,"<init>","()V",false);c.visitFieldInsn(Opcodes.PUTFIELD,owner,"model","L"+model+";");c.visitInsn(Opcodes.RETURN);c.visitMaxs(0,0);c.visitEnd();

        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"draw","(L"+tile+";)V",null,null);m.visitCode();
        m.visitMethodInsn(Opcodes.INVOKESTATIC,"org/lwjgl/opengl/GL11","glPushMatrix","()V",false);
        m.visitVarInsn(Opcodes.ALOAD,1);m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,tile,"func_145832_p","()I",false);m.visitVarInsn(Opcodes.ISTORE,2);
        m.visitVarInsn(Opcodes.ALOAD,0);m.visitFieldInsn(Opcodes.GETFIELD,owner,"model","L"+model+";");m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,model,"base","()V",false);
        org.objectweb.asm.Label end=new org.objectweb.asm.Label();
        m.visitVarInsn(Opcodes.ILOAD,2);m.visitInsn(Opcodes.ICONST_2);m.visitInsn(Opcodes.ISHR);m.visitInsn(Opcodes.ICONST_1);m.visitJumpInsn(Opcodes.IF_ICMPNE,end);
        m.visitVarInsn(Opcodes.ALOAD,0);m.visitFieldInsn(Opcodes.GETFIELD,owner,"model","L"+model+";");m.visitMethodInsn(Opcodes.INVOKEVIRTUAL,model,"ornament","()V",false);
        m.visitLabel(end);m.visitMethodInsn(Opcodes.INVOKESTATIC,"org/lwjgl/opengl/GL11","glPopMatrix","()V",false);m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();
        w.visitEnd();return w.toByteArray();
    }

    private static byte[] model(){
        String owner="foreign/radial/Model",mr="net/minecraft/client/model/ModelRenderer";
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,owner,null,"net/minecraft/client/model/ModelBase",null);
        w.visitField(Opcodes.ACC_PUBLIC,"ornamentPart","L"+mr+";",null,null).visitEnd();
        MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/client/model/ModelBase","<init>","()V",false);
        c.visitVarInsn(Opcodes.ALOAD,0);c.visitTypeInsn(Opcodes.NEW,mr);c.visitInsn(Opcodes.DUP);c.visitVarInsn(Opcodes.ALOAD,0);pushInt(c,4);pushInt(c,6);
        c.visitMethodInsn(Opcodes.INVOKESPECIAL,mr,"<init>","(Lnet/minecraft/client/model/ModelBase;II)V",false);c.visitFieldInsn(Opcodes.PUTFIELD,owner,"ornamentPart","L"+mr+";");
        c.visitVarInsn(Opcodes.ALOAD,0);c.visitFieldInsn(Opcodes.GETFIELD,owner,"ornamentPart","L"+mr+";");pushFloat(c,-1F);pushFloat(c,-2F);pushFloat(c,0F);pushInt(c,3);pushInt(c,5);pushInt(c,1);
        c.visitMethodInsn(Opcodes.INVOKEVIRTUAL,mr,"func_78789_a","(FFFIII)L"+mr+";",false);c.visitInsn(Opcodes.POP);
        c.visitVarInsn(Opcodes.ALOAD,0);c.visitFieldInsn(Opcodes.GETFIELD,owner,"ornamentPart","L"+mr+";");pushFloat(c,0F);pushFloat(c,8F);pushFloat(c,0F);
        c.visitMethodInsn(Opcodes.INVOKEVIRTUAL,mr,"func_78793_a","(FFF)V",false);c.visitInsn(Opcodes.RETURN);c.visitMaxs(0,0);c.visitEnd();

        MethodVisitor base=w.visitMethod(Opcodes.ACC_PUBLIC,"base","()V",null,null);base.visitCode();base.visitInsn(Opcodes.RETURN);base.visitMaxs(0,0);base.visitEnd();
        MethodVisitor draw=w.visitMethod(Opcodes.ACC_PUBLIC,"ornament","()V",null,null);draw.visitCode();draw.visitVarInsn(Opcodes.ALOAD,0);draw.visitFieldInsn(Opcodes.GETFIELD,owner,"ornamentPart","L"+mr+";");draw.visitLdcInsn(.0625F);
        draw.visitMethodInsn(Opcodes.INVOKEVIRTUAL,mr,"func_78785_a","(F)V",false);draw.visitInsn(Opcodes.RETURN);draw.visitMaxs(0,0);draw.visitEnd();
        w.visitEnd();return w.toByteArray();
    }

    private static void pushInt(MethodVisitor m,int value){
        if(value>=0&&value<=5)m.visitInsn(Opcodes.ICONST_0+value);else m.visitIntInsn(Opcodes.BIPUSH,value);
    }
    private static void pushFloat(MethodVisitor m,float value){
        if(value==0F)m.visitInsn(Opcodes.FCONST_0);else if(value==1F)m.visitInsn(Opcodes.FCONST_1);else if(value==2F)m.visitInsn(Opcodes.FCONST_2);else m.visitLdcInsn(value);
    }
    private static void put(JarOutputStream out,String name,byte[] bytes)throws Exception{
        out.putNextEntry(new JarEntry(name));out.write(bytes);out.closeEntry();
    }
}
