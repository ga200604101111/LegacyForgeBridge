package dev.longyu.legacyforgebridge.convert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyConstantNameTableAnalyzerTest {
    @TempDir Path temp;

    @Test void extractsShortToNameTableWithoutExecutingSourceInitializers() throws Exception {
        Path source=temp.resolve("foreign-constant-table.jar");
        try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(source))){
            write(out,"foreign/presentation/NameBase.class",nameBase());
            write(out,"foreign/presentation/NameA.class",forwarder("foreign/presentation/NameA"));
            write(out,"foreign/presentation/NameB.class",forwarder("foreign/presentation/NameB"));
            write(out,"foreign/presentation/Names.class",names());
        }

        var analysis=new LegacyConstantNameTableAnalyzer().analyze(source);
        assertTrue(analysis.diagnostics().isEmpty(),String.join("\n",analysis.diagnostics()));
        assertEquals(1,analysis.tables().size());
        var table=analysis.tables().getFirst();
        assertEquals(new LegacyConstantNameTableAnalyzer.FieldRef(
                "foreign/presentation/Names","TABLE","Ljava/util/HashMap;"),table.field());
        assertEquals("foreign/presentation/NameBase",table.valueType());
        assertEquals("name",table.nameField());
        assertEquals(java.util.List.of(
                new LegacyConstantNameTableAnalyzer.Entry((short)4,"echo"),
                new LegacyConstantNameTableAnalyzer.Entry((short)7,"nova")),table.entries());
    }

    private static void write(JarOutputStream out,String path,byte[] bytes)throws Exception{
        out.putNextEntry(new JarEntry(path));out.write(bytes);out.closeEntry();
    }

    private static byte[] nameBase(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        String owner="foreign/presentation/NameBase";
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC|Opcodes.ACC_SUPER,owner,null,"java/lang/Object",null);
        w.visitField(Opcodes.ACC_PRIVATE|Opcodes.ACC_FINAL,"name","Ljava/lang/String;",null,null).visitEnd();
        MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","(ILjava/lang/String;I)V",null,null);c.visitCode();
        c.visitVarInsn(Opcodes.ALOAD,0);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false);
        c.visitVarInsn(Opcodes.ALOAD,0);c.visitVarInsn(Opcodes.ALOAD,2);c.visitFieldInsn(Opcodes.PUTFIELD,owner,"name","Ljava/lang/String;");
        c.visitFieldInsn(Opcodes.GETSTATIC,"foreign/presentation/Names","TABLE","Ljava/util/HashMap;");
        c.visitVarInsn(Opcodes.ILOAD,1);c.visitInsn(Opcodes.I2S);
        c.visitMethodInsn(Opcodes.INVOKESTATIC,"java/lang/Short","valueOf","(S)Ljava/lang/Short;",false);
        c.visitVarInsn(Opcodes.ALOAD,0);
        c.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/util/HashMap","put","(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",false);
        c.visitInsn(Opcodes.POP);c.visitInsn(Opcodes.RETURN);c.visitMaxs(3,4);c.visitEnd();
        MethodVisitor getter=w.visitMethod(Opcodes.ACC_PUBLIC,"getName","()Ljava/lang/String;",null,null);getter.visitCode();
        getter.visitVarInsn(Opcodes.ALOAD,0);getter.visitFieldInsn(Opcodes.GETFIELD,owner,"name","Ljava/lang/String;");getter.visitInsn(Opcodes.ARETURN);getter.visitMaxs(1,1);getter.visitEnd();
        w.visitEnd();return w.toByteArray();
    }

    private static byte[] forwarder(String owner){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC|Opcodes.ACC_SUPER,owner,null,"foreign/presentation/NameBase",null);
        MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","(ILjava/lang/String;I)V",null,null);c.visitCode();
        c.visitVarInsn(Opcodes.ALOAD,0);c.visitVarInsn(Opcodes.ILOAD,1);c.visitVarInsn(Opcodes.ALOAD,2);c.visitVarInsn(Opcodes.ILOAD,3);
        c.visitMethodInsn(Opcodes.INVOKESPECIAL,"foreign/presentation/NameBase","<init>","(ILjava/lang/String;I)V",false);
        c.visitInsn(Opcodes.RETURN);c.visitMaxs(4,4);c.visitEnd();w.visitEnd();return w.toByteArray();
    }

    private static byte[] names(){
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        String owner="foreign/presentation/Names";
        w.visit(Opcodes.V1_7,Opcodes.ACC_PUBLIC|Opcodes.ACC_SUPER,owner,null,"java/lang/Object",null);
        w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"TABLE","Ljava/util/HashMap;",null,null).visitEnd();
        w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"A","Lforeign/presentation/NameBase;",null,null).visitEnd();
        w.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"B","Lforeign/presentation/NameBase;",null,null).visitEnd();
        MethodVisitor c=w.visitMethod(Opcodes.ACC_PRIVATE,"<init>","()V",null,null);c.visitCode();c.visitVarInsn(Opcodes.ALOAD,0);
        c.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false);c.visitInsn(Opcodes.RETURN);c.visitMaxs(1,1);c.visitEnd();
        MethodVisitor s=w.visitMethod(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);s.visitCode();
        s.visitTypeInsn(Opcodes.NEW,"java/util/HashMap");s.visitInsn(Opcodes.DUP);
        s.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/util/HashMap","<init>","()V",false);
        s.visitFieldInsn(Opcodes.PUTSTATIC,owner,"TABLE","Ljava/util/HashMap;");
        s.visitTypeInsn(Opcodes.NEW,"foreign/presentation/NameA");s.visitInsn(Opcodes.DUP);s.visitInsn(Opcodes.ICONST_4);s.visitLdcInsn("echo");s.visitInsn(Opcodes.ICONST_2);
        s.visitMethodInsn(Opcodes.INVOKESPECIAL,"foreign/presentation/NameA","<init>","(ILjava/lang/String;I)V",false);
        s.visitFieldInsn(Opcodes.PUTSTATIC,owner,"A","Lforeign/presentation/NameBase;");
        s.visitTypeInsn(Opcodes.NEW,"foreign/presentation/NameB");s.visitInsn(Opcodes.DUP);s.visitIntInsn(Opcodes.BIPUSH,7);s.visitLdcInsn("nova");s.visitInsn(Opcodes.ICONST_3);
        s.visitMethodInsn(Opcodes.INVOKESPECIAL,"foreign/presentation/NameB","<init>","(ILjava/lang/String;I)V",false);
        s.visitFieldInsn(Opcodes.PUTSTATIC,owner,"B","Lforeign/presentation/NameBase;");
        s.visitInsn(Opcodes.RETURN);s.visitMaxs(5,0);s.visitEnd();w.visitEnd();return w.toByteArray();
    }
}
