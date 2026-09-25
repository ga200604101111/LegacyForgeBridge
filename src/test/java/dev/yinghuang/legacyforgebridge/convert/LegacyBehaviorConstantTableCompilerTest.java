package dev.yinghuang.legacyforgebridge.convert;

import dev.yinghuang.legacyforgebridge.behavior.LegacyBehaviorApi;
import dev.yinghuang.legacyforgebridge.behavior.LegacyBehaviorRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyBehaviorConstantTableCompilerTest {
    @TempDir Path temp;

    @Test void compiledCallbackReadsReconstructedShortToNameTable() throws Exception {
        Path source=temp.resolve("foreign-table-callback.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(source))){
            write(out,"foreign/table/NameBase.class",nameBase());
            write(out,"foreign/table/NameA.class",forwarder("foreign/table/NameA"));
            write(out,"foreign/table/NameB.class",forwarder("foreign/table/NameB"));
            write(out,"foreign/table/Names.class",names());
            write(out,"foreign/table/NamedItem.class",namedItem());
        }
        var allocation=new LegacyItemRenderAnalyzer.ItemAllocation("named","foreign/table/NamedItem","()V",List.of(),false,false,false);
        var result=new LegacyBehaviorCompiler().compile(source,"table_fixture","generated/TableBootstrap",
                Map.of("named","table_fixture:named"),List.of(allocation));
        assertEquals(1,result.items().size(),String.join("\n",result.diagnostics()));
        assertTrue(result.items().getFirst().hooks().contains("tooltip"),result.items().toString());

        ClassLoader loader=new ClassLoader(getClass().getClassLoader()){
            @Override protected Class<?> findClass(String name)throws ClassNotFoundException{
                byte[] bytes=result.classes().get(name.replace('.','/')+".class");
                if(bytes==null)throw new ClassNotFoundException(name);
                return defineClass(name,bytes,0,bytes.length);
            }
        };
        Class.forName("generated.TableBootstrap",true,loader).getMethod("initialize").invoke(null);
        try{
            var definition=LegacyBehaviorRegistry.item("table_fixture:named");
            var lines=new ArrayList<String>();
            LegacyBehaviorApi.begin("table_fixture",(key,args)->key);
            try{
                definition.item().func_77624_a(new LegacyBehaviorApi.Stack(definition.item()),new LegacyBehaviorApi.Player(),lines,false);
            }finally{LegacyBehaviorApi.end();}
            assertEquals(List.of("echo"),lines);
        }finally{LegacyBehaviorRegistry.removeMod("table_fixture");}
    }

    private static void write(JarOutputStream out,String path,byte[] bytes)throws Exception{out.putNextEntry(new JarEntry(path));out.write(bytes);out.closeEntry();}

    private static byte[] nameBase(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);String owner="foreign/table/NameBase";
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC|Opcodes.ACC_SUPER,owner,null,"java/lang/Object",null);
        w.visitField(Opcodes.ACC_PRIVATE|Opcodes.ACC_FINAL,"name","Ljava/lang/String;",null,null).visitEnd();
        MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","(ILjava/lang/String;I)V",null,null);c.visitCode();
        c.visitVarInsn(Opcodes.ALOAD,0);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false);
        c.visitVarInsn(Opcodes.ALOAD,0);c.visitVarInsn(Opcodes.ALOAD,2);c.visitFieldInsn(Opcodes.PUTFIELD,owner,"name","Ljava/lang/String;");
        c.visitFieldInsn(Opcodes.GETSTATIC,"foreign/table/Names","TABLE","Ljava/util/HashMap;");c.visitVarInsn(Opcodes.ILOAD,1);c.visitInsn(Opcodes.I2S);
        c.visitMethodInsn(Opcodes.INVOKESTATIC,"java/lang/Short","valueOf","(S)Ljava/lang/Short;",false);c.visitVarInsn(Opcodes.ALOAD,0);
        c.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/util/HashMap","put","(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",false);c.visitInsn(Opcodes.POP);c.visitInsn(Opcodes.RETURN);c.visitMaxs(3,4);c.visitEnd();
        MethodVisitor g=w.visitMethod(Opcodes.ACC_PUBLIC,"getName","()Ljava/lang/String;",null,null);g.visitCode();g.visitVarInsn(Opcodes.ALOAD,0);g.visitFieldInsn(Opcodes.GETFIELD,owner,"name","Ljava/lang/String;");g.visitInsn(Opcodes.ARETURN);g.visitMaxs(1,1);g.visitEnd();
        w.visitEnd();return w.toByteArray();
    }
    private static byte[] forwarder(String owner){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC|Opcodes.ACC_SUPER,owner,null,"foreign/table/NameBase",null);
        MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","(ILjava/lang/String;I)V",null,null);c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitVarInsn(Opcodes.ILOAD,1);c.visitVarInsn(Opcodes.ALOAD,2);c.visitVarInsn(Opcodes.ILOAD,3);
        c.visitMethodInsn(Opcodes.INVOKESPECIAL,"foreign/table/NameBase","<init>","(ILjava/lang/String;I)V",false);c.visitInsn(Opcodes.RETURN);c.visitMaxs(4,4);c.visitEnd();w.visitEnd();return w.toByteArray();
    }
    private static byte[] names(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);String owner="foreign/table/Names";w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC|Opcodes.ACC_SUPER,owner,null,"java/lang/Object",null);
        w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"TABLE","Ljava/util/HashMap;",null,null).visitEnd();
        w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"A","Lforeign/table/NameBase;",null,null).visitEnd();w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"B","Lforeign/table/NameBase;",null,null).visitEnd();
        MethodVisitor s=w.visitMethod(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);s.visitCode();s.visitTypeInsn(Opcodes.NEW,"java/util/HashMap");s.visitInsn(Opcodes.DUP);s.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/util/HashMap","<init>","()V",false);s.visitFieldInsn(Opcodes.PUTSTATIC,owner,"TABLE","Ljava/util/HashMap;");
        s.visitTypeInsn(Opcodes.NEW,"foreign/table/NameA");s.visitInsn(Opcodes.DUP);s.visitInsn(Opcodes.ICONST_4);s.visitLdcInsn("echo");s.visitInsn(Opcodes.ICONST_2);s.visitMethodInsn(Opcodes.INVOKESPECIAL,"foreign/table/NameA","<init>","(ILjava/lang/String;I)V",false);s.visitFieldInsn(Opcodes.PUTSTATIC,owner,"A","Lforeign/table/NameBase;");
        s.visitTypeInsn(Opcodes.NEW,"foreign/table/NameB");s.visitInsn(Opcodes.DUP);s.visitIntInsn(Opcodes.BIPUSH,7);s.visitLdcInsn("nova");s.visitInsn(Opcodes.ICONST_3);s.visitMethodInsn(Opcodes.INVOKESPECIAL,"foreign/table/NameB","<init>","(ILjava/lang/String;I)V",false);s.visitFieldInsn(Opcodes.PUTSTATIC,owner,"B","Lforeign/table/NameBase;");s.visitInsn(Opcodes.RETURN);s.visitMaxs(5,0);s.visitEnd();w.visitEnd();return w.toByteArray();
    }
    private static byte[] namedItem(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);String owner="foreign/table/NamedItem";w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC|Opcodes.ACC_SUPER,owner,null,"net/minecraft/item/Item",null);
        MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/item/Item","<init>","()V",false);c.visitInsn(Opcodes.RETURN);c.visitMaxs(1,1);c.visitEnd();
        MethodVisitor t=w.visitMethod(Opcodes.ACC_PUBLIC,"func_77624_a","(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/EntityPlayer;Ljava/util/List;Z)V",null,null);t.visitCode();t.visitVarInsn(Opcodes.ALOAD,3);
        t.visitFieldInsn(Opcodes.GETSTATIC,"foreign/table/Names","TABLE","Ljava/util/HashMap;");t.visitInsn(Opcodes.ICONST_4);t.visitInsn(Opcodes.I2S);t.visitMethodInsn(Opcodes.INVOKESTATIC,"java/lang/Short","valueOf","(S)Ljava/lang/Short;",false);
        t.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/util/HashMap","get","(Ljava/lang/Object;)Ljava/lang/Object;",false);t.visitTypeInsn(Opcodes.CHECKCAST,"foreign/table/NameBase");t.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"foreign/table/NameBase","getName","()Ljava/lang/String;",false);
        t.visitMethodInsn(Opcodes.INVOKEINTERFACE,"java/util/List","add","(Ljava/lang/Object;)Z",true);t.visitInsn(Opcodes.POP);t.visitInsn(Opcodes.RETURN);t.visitMaxs(3,5);t.visitEnd();w.visitEnd();return w.toByteArray();
    }
}
