package dev.yinghuang.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;

import java.nio.file.*;
import java.util.jar.*;

import static org.junit.jupiter.api.Assertions.*;

class LegacyEntityEggAnalyzerTest {
    @TempDir Path temp;

    @Test void renamedEggHelperIsSourceStructural() throws Exception {
        Path jar=temp.resolve("egg.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(jar))){
            put(out,"foreign/egg/Info.class",info());
            put(out,"foreign/egg/Bootstrap.class",bootstrap());
        }
        var result=new LegacyEntityEggAnalyzer().analyze(jar);
        assertTrue(result.diagnostics().isEmpty(),String.join("\n",result.diagnostics()));
        assertEquals(1,result.rules().size());
        var r=result.rules().getFirst();
        assertEquals("moss_beast",r.registryName());
        assertEquals(37,r.numericId());
        assertEquals(0x123456,r.primaryColor());
        assertEquals(0xABCDEF,r.secondaryColor());
    }

    private static byte[] info(){
        String o="foreign/egg/Info";ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,o,null,"java/lang/Object",null);
        for(String f:new String[]{"id","a","b"})w.visitField(Opcodes.ACC_PUBLIC,f,"I",null,null).visitEnd();
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","(III)V",null,null);m.visitCode();
        m.visitVarInsn(Opcodes.ALOAD,0);m.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false);
        for(int i=1;i<=3;i++){m.visitVarInsn(Opcodes.ALOAD,0);m.visitVarInsn(Opcodes.ILOAD,i);
            m.visitFieldInsn(Opcodes.PUTFIELD,o,i==1?"id":i==2?"a":"b","I");}
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] bootstrap(){
        String o="foreign/egg/Bootstrap";ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,o,null,"java/lang/Object",null);
        w.visitField(Opcodes.ACC_STATIC,"EGGS","Ljava/util/HashMap;",null,null).visitEnd();

        MethodVisitor cl=w.visitMethod(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);cl.visitCode();
        cl.visitTypeInsn(Opcodes.NEW,"java/util/HashMap");cl.visitInsn(Opcodes.DUP);
        cl.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/util/HashMap","<init>","()V",false);
        cl.visitFieldInsn(Opcodes.PUTSTATIC,o,"EGGS","Ljava/util/HashMap;");cl.visitInsn(Opcodes.RETURN);cl.visitMaxs(0,0);cl.visitEnd();

        MethodVisitor h=w.visitMethod(Opcodes.ACC_STATIC,"helper","(Ljava/lang/Class;Ljava/lang/String;IIIIIZ)V",null,null);h.visitCode();
        h.visitVarInsn(Opcodes.ALOAD,0);h.visitVarInsn(Opcodes.ALOAD,1);h.visitVarInsn(Opcodes.ILOAD,2);
        h.visitInsn(Opcodes.ACONST_NULL);h.visitVarInsn(Opcodes.ILOAD,5);h.visitVarInsn(Opcodes.ILOAD,6);h.visitVarInsn(Opcodes.ILOAD,7);
        h.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/EntityRegistry","registerModEntity",
                "(Ljava/lang/Class;Ljava/lang/String;ILjava/lang/Object;IIZ)V",false);
        h.visitFieldInsn(Opcodes.GETSTATIC,o,"EGGS","Ljava/util/HashMap;");
        h.visitVarInsn(Opcodes.ILOAD,2);h.visitMethodInsn(Opcodes.INVOKESTATIC,"java/lang/Integer","valueOf","(I)Ljava/lang/Integer;",false);
        h.visitTypeInsn(Opcodes.NEW,"foreign/egg/Info");h.visitInsn(Opcodes.DUP);
        h.visitVarInsn(Opcodes.ILOAD,2);h.visitVarInsn(Opcodes.ILOAD,3);h.visitVarInsn(Opcodes.ILOAD,4);
        h.visitMethodInsn(Opcodes.INVOKESPECIAL,"foreign/egg/Info","<init>","(III)V",false);
        h.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/util/HashMap","put","(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",false);
        h.visitInsn(Opcodes.POP);h.visitInsn(Opcodes.RETURN);h.visitMaxs(0,0);h.visitEnd();

        MethodVisitor wrap=w.visitMethod(Opcodes.ACC_STATIC,"wrapper","(Ljava/lang/Class;Ljava/lang/String;III)V",null,null);wrap.visitCode();
        wrap.visitVarInsn(Opcodes.ALOAD,0);wrap.visitVarInsn(Opcodes.ALOAD,1);wrap.visitVarInsn(Opcodes.ILOAD,2);
        wrap.visitVarInsn(Opcodes.ILOAD,3);wrap.visitVarInsn(Opcodes.ILOAD,4);
        wrap.visitIntInsn(Opcodes.BIPUSH,80);wrap.visitInsn(Opcodes.ICONST_3);wrap.visitInsn(Opcodes.ICONST_1);
        wrap.visitMethodInsn(Opcodes.INVOKESTATIC,o,"helper","(Ljava/lang/Class;Ljava/lang/String;IIIIIZ)V",false);
        wrap.visitInsn(Opcodes.RETURN);wrap.visitMaxs(0,0);wrap.visitEnd();

        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"init",
                "(Lcpw/mods/fml/common/event/FMLInitializationEvent;)V",null,null);
        m.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;",true).visitEnd();m.visitCode();
        m.visitLdcInsn(Type.getObjectType("foreign/egg/MossBeast"));m.visitLdcInsn("moss_beast");m.visitIntInsn(Opcodes.BIPUSH,37);
        m.visitLdcInsn(0x123456);m.visitLdcInsn(0xABCDEF);
        m.visitMethodInsn(Opcodes.INVOKESTATIC,o,"wrapper","(Ljava/lang/Class;Ljava/lang/String;III)V",false);
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();
        w.visitEnd();return w.toByteArray();
    }

    private static void put(JarOutputStream out,String n,byte[] b)throws Exception{
        out.putNextEntry(new JarEntry(n));out.write(b);out.closeEntry();
    }
}
