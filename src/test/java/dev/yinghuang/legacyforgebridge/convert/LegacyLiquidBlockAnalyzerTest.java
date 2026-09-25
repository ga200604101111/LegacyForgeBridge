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

import static org.junit.jupiter.api.Assertions.*;

class LegacyLiquidBlockAnalyzerTest {
    @TempDir Path tempDir;

    @Test
    void unrelatedNamespaceAdmitsOnlyProvenVanillaLiquidMaterialFamily()throws Exception{
        Path jar=tempDir.resolve("foreign-liquid.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(jar))){
            put(out,"foreign/liquid/Spring.class",liquid("foreign/liquid/Spring","field_151586_h",true));
            put(out,"foreign/liquid/Unknown.class",liquid("foreign/liquid/Unknown","field_151576_e",true));
            put(out,"foreign/liquid/SolidCollision.class",liquid("foreign/liquid/SolidCollision","field_151586_h",false));
            put(out,"foreign/liquid/Bootstrap.class",bootstrap());
        }
        var analysis=new LegacyLiquidBlockAnalyzer().analyze(jar);
        var spring=analysis.rules().stream().filter(rule->rule.registryName().equals("spring")).findFirst().orElseThrow();
        assertEquals(LegacyLiquidBlockAnalyzer.Kind.WATER,spring.kind());
        assertEquals(4,spring.renderType());assertTrue(spring.collisionEmpty());
        assertEquals(8D/9D,spring.height(0),0.000001D);
        assertEquals(1D/9D,spring.height(7),0.000001D);
        assertEquals(8D/9D,spring.height(8),0.000001D);
        assertTrue(analysis.rules().stream().noneMatch(rule->rule.registryName().equals("unknown")));
        assertTrue(analysis.rules().stream().noneMatch(rule->rule.registryName().equals("solid_collision")));
    }

    private static byte[] liquid(String name,String materialField,boolean emptyCollision){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,name,null,"net/minecraft/block/BlockLiquid",null);
        MethodVisitor init=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD,0);
        init.visitFieldInsn(Opcodes.GETSTATIC,"net/minecraft/block/material/Material",materialField,"Lnet/minecraft/block/material/Material;");
        init.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/block/BlockLiquid","<init>","(Lnet/minecraft/block/material/Material;)V",false);
        init.visitInsn(Opcodes.RETURN);init.visitMaxs(0,0);init.visitEnd();
        constantInt(w,"func_149645_b",4);constantInt(w,"func_149662_c",0);constantInt(w,"func_149686_d",0);
        MethodVisitor collision=w.visitMethod(Opcodes.ACC_PUBLIC,"func_149668_a",
                "(Lnet/minecraft/world/World;III)Lnet/minecraft/util/AxisAlignedBB;",null,null);
        collision.visitCode();
        if(emptyCollision)collision.visitInsn(Opcodes.ACONST_NULL);
        else{
            collision.visitTypeInsn(Opcodes.NEW,"net/minecraft/util/AxisAlignedBB");collision.visitInsn(Opcodes.DUP);
            collision.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/util/AxisAlignedBB","<init>","()V",false);
        }
        collision.visitInsn(Opcodes.ARETURN);collision.visitMaxs(0,0);collision.visitEnd();
        w.visitEnd();return w.toByteArray();
    }

    private static void constantInt(ClassWriter w,String name,int value){
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,name,"()"+(name.equals("func_149645_b")?"I":"Z"),null,null);m.visitCode();
        if(value>=0&&value<=5)m.visitInsn(Opcodes.ICONST_0+value);else m.visitLdcInsn(value);
        m.visitInsn(Opcodes.IRETURN);m.visitMaxs(0,0);m.visitEnd();
    }

    private static byte[] bootstrap(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC,"foreign/liquid/Bootstrap",null,"java/lang/Object",null);
        MethodVisitor m=w.visitMethod(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);m.visitCode();
        register(m,"foreign/liquid/Spring","spring");register(m,"foreign/liquid/Unknown","unknown");
        register(m,"foreign/liquid/SolidCollision","solid_collision");
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();w.visitEnd();return w.toByteArray();
    }
    private static void register(MethodVisitor m,String type,String id){
        m.visitTypeInsn(Opcodes.NEW,type);m.visitInsn(Opcodes.DUP);m.visitMethodInsn(Opcodes.INVOKESPECIAL,type,"<init>","()V",false);
        m.visitLdcInsn(id);m.visitMethodInsn(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/registry/GameRegistry","registerBlock",
                "(Lnet/minecraft/block/Block;Ljava/lang/String;)V",false);
    }
    private static void put(JarOutputStream out,String name,byte[] bytes)throws Exception{
        out.putNextEntry(new JarEntry(name));out.write(bytes);out.closeEntry();
    }
}
